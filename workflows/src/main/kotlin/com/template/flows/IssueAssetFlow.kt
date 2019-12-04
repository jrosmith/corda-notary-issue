package com.template.flows

import co.paralleluniverse.fibers.Suspendable
import com.monetago.fp.schema.AssetSchemaV1
import com.template.contracts.AssetContract
import com.template.objects.Asset
import com.template.states.AssetState
import net.corda.core.contracts.Command
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.node.StatesToRecord
import net.corda.core.node.services.Vault
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.node.services.vault.builder
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.loggerFor

object IssueAssetFlow {
    @InitiatingFlow
    class Issue(private val asset: Asset, private val requestingClient: Party) : FlowLogic<String>() {
        companion object {
            val log = loggerFor<Issue>()
        }

        @Suspendable
        override fun call(): String {
            log.info("Processing asset")

            val (exactMatchExists, inputStateAndRef) = checkForExactMatch(asset)

            return if (exactMatchExists) handleAddParticipant(inputStateAndRef) else handleNewAsset(asset)
        }

        @Suspendable
        private fun handleAddParticipant(inputStateAndRef: StateAndRef<AssetState>?): String {
            log.info("Asset is an exact match of an existing asset, adding participant to existing asset")
            val outputState = newStateFromInputState(inputStateAndRef!!.state.data)

            val txn = TransactionBuilder(notary())
                    .addCommand(
                            Command(
                                    AssetContract.Commands.AddParticipant(),
                                    myIdentity().owningKey // issuingNode key
                            )
                    )
                    .addInputState(inputStateAndRef)
                    .addOutputState(outputState)

            val stx = serviceHub.signInitialTransaction(txn)
            log.info("Add participant transaction built and signed by '${myIdentity()}'")

            // saving locally because the issuing node is not a participant but needs to have a record
            // of all assets in the network
            log.info("Saving add participant transaction locally")
            serviceHub.recordTransactions(StatesToRecord.ALL_VISIBLE, listOf(stx))

            log.info("Calling add participant finality flow for '${outputState.participants}")
            val flowSets = outputState.participants.map { initiateFlow(it) }.toSet()
            subFlow(FinalityFlow(stx, flowSets))

            return outputState.linearId.toString()
        }

        @Suspendable
        private fun handleNewAsset(asset: Asset) : String{
            log.info("Asset has no exact matches, issuing new asset")
            val outputState = newAsset(asset)

            val txn = TransactionBuilder(notary())
                    .addCommand(
                            Command(
                                    AssetContract.Commands.Issue(),
                                    myIdentity().owningKey // issuingNode key
                            )
                    )
                    .addOutputState(outputState)


            val stx = serviceHub.signInitialTransaction(txn)
            log.info("New issuance transaction built and signed by ${myIdentity()}")

            // saving locally because the issuing node is not a participant but needs to have a record
            // of all assets in the network
            log.info("Saving new issuance locally")
            serviceHub.recordTransactions(StatesToRecord.ALL_VISIBLE, listOf(stx))

            log.info("Calling new issuance finality flow for '${outputState.participants}'")
            val flowSets = outputState.participants.map { initiateFlow(it) }.toSet()
            subFlow(FinalityFlow(stx, flowSets))

            return outputState.linearId.toString()
        }

        private fun checkForExactMatch(asset: Asset) : Pair<Boolean, StateAndRef<AssetState>?> {
            val generalCriteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED) // Only check unconsumed states
            val uniqueIdx = builder { AssetSchemaV1.PersistentAsset::uniqueId.equal(asset.uniqueId) } // Looking for invoices with matching uniqueIds
            val uniqueQueryCriteria = QueryCriteria.VaultCustomQueryCriteria(uniqueIdx) // Use uniqueId index in database
            val queryCriteria = generalCriteria.and(uniqueQueryCriteria)

            val results = serviceHub.vaultService.queryBy<AssetState>(queryCriteria)

            log.info("Checking for preexisting asset states with fingerprint '${asset.uniqueId}")

            if (results.states.isNotEmpty()) {
                if (results.states.size != 1) {
                    throw Exception("More than one asset returned for unique identifier")
                }

                log.info("Returning preexisting asset matching '${asset.uniqueId}'")
                return Pair(true, results.states[0])
            }

            log.info("No preexisting asset matching '${asset.uniqueId}'")
            return Pair(false, null)
        }

        private fun newAsset(asset: Asset) = AssetState(
                listOf(requestingClient),
                myIdentity(), // issuing node identity
                asset.uniqueId
        )

        private fun newStateFromInputState(inputState : AssetState) = inputState.copy(
                parties = inputState.participants.plus(requestingClient)
        )

        private fun myIdentity() = serviceHub.myInfo.legalIdentities.first()

        private fun notary() = serviceHub.networkMapCache.notaryIdentities.first()
    }


    @InitiatedBy(Issue::class)
    class ReceiveIssuance(private val counterpartySession: FlowSession) : FlowLogic<Unit>() {
        companion object {
            val log = loggerFor<ReceiveIssuance>()
        }

        @Suspendable
        override fun call() {
            log.info("Issuance received by '${serviceHub.myInfo.legalIdentities.first()}'")
            subFlow(ReceiveFinalityFlow(counterpartySession, statesToRecord = StatesToRecord.ALL_VISIBLE))
        }
    }
}