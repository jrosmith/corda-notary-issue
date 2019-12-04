package com.template.contracts

import com.template.states.AssetState
import net.corda.core.contracts.*
import net.corda.core.transactions.LedgerTransaction
import java.lang.IllegalArgumentException
import java.security.PublicKey

class AssetContract : Contract {
    companion object {
        @JvmStatic
        val ASSET_CONTRACT_ID = AssetContract::class.qualifiedName!!
    }

    override fun verify(tx: LedgerTransaction) {
        val cmd = tx.commands.requireSingleCommand<Commands>()

        when (cmd.value) {
            is Commands.Issue -> verifyIssue(tx, cmd)
            is Commands.AddParticipant -> verifyAddParticipant(tx, cmd)
            else -> throw IllegalArgumentException("Unrecognized command: ${cmd.value}")
        }
    }

    interface Commands : CommandData {
        class Issue : TypeOnlyCommandData(), Commands
        class AddParticipant: TypeOnlyCommandData(), Commands
    }

    private fun verifyIssue(tx: LedgerTransaction, cmd: CommandWithParties<AssetContract.Commands>) {
        val signers = cmd.signers
        val outputs = tx.outputsOfType<AssetState>()

        return requireThat {
            "No input states are consumed for new asset issuance" using (tx.inputs.isEmpty())
            "Only one signature required for asset issuance" using (signers.size == 1)
            "Non-issuing nodes cannot issue a fingerprinted asset" using (outputs.map { verifyNoClientSigs(it, signers) }.all { it == true } )
            "The issuing node must sign the new fingerprinted asset issuance" using (outputs.map { signers[0] == (it.issuingNode.owningKey) }.all { it == true })
            "The issuing node cannot be a participant" using (outputs.map { !(it.participants.toSet().contains(it.issuingNode)) }.all { it == true} )
        }
    }

    private fun verifyAddParticipant(tx: LedgerTransaction, cmd: CommandWithParties<Commands>) {
        val signers = cmd.signers
        val inputs = tx.inputsOfType<AssetState>()
        val outputs = tx.outputsOfType<AssetState>()

        return requireThat {
            "Non unique assets should consume a single existing asset state" using (inputs.size == outputs.size)
            "Only one signature required for asset issuance" using (signers.size == 1)
            "Non-issuing nodes cannot add a participant to an asset" using (outputs.map { verifyNoClientSigs(it, signers) }.all { it == true })
            "The issuing node must sign the addition of a participant to an existing asset" using (outputs.map { signers[0] == (it.issuingNode.owningKey) }.all { it == true })
            "The issuing node cannot be a participant" using (outputs.map { !(it.participants.toSet().contains(it.issuingNode)) }.all { it == true} )
        }
    }

    private fun verifyNoClientSigs(out: AssetState, signers: List<PublicKey>): Boolean {
        out.participants.forEach { lender ->
            if(signers.toSet().contains(lender.owningKey)) {
                return false
            }
        }

        return true
    }
}