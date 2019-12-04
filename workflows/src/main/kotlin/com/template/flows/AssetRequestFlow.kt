package com.template.flows

import co.paralleluniverse.fibers.Suspendable
import com.template.objects.Asset
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.utilities.loggerFor
import net.corda.core.utilities.unwrap
import java.time.Instant


object AssetRequestFlow {
    @InitiatingFlow
    @StartableByRPC
    class SubmitRequest(private val asset: Asset) : FlowLogic<String>() {
        companion object {
            val log = loggerFor<SubmitRequest>()
        }

        @Suspendable
        override fun call(): String {

            val issuingNode = serviceHub.identityService.partiesFromName(
                    "IssuingNode",
                    true
            ).first()

            val session = initiateFlow(issuingNode)

            log.info("Forwarding asset issuance request to issuing node")

            val res = session.sendAndReceive<String>(asset).unwrap { it }

            log.debug("Received response from issuing node")

            return res
        }
    }

    @InitiatedBy(SubmitRequest::class)
    class SendResponse(private val counterpartySession: FlowSession) : FlowLogic<Unit>() {
        companion object {
            val log = loggerFor<SendResponse>()
        }


        @Suspendable
        override fun call() {
            log.info("Received message with fingerprinted asset issuance request")

            val req: Asset = counterpartySession.receive<Asset>().unwrap { it }
            val res = issueAssets(req, counterpartySession.counterparty)

            log.info("Responding to client with fingerprinting response")
            counterpartySession.send(res)
        }

        @Suspendable
        private fun issueAssets(asset: Asset, client: Party): String {
            return subFlow(IssueAssetFlow.Issue(asset, client))
        }
    }
}