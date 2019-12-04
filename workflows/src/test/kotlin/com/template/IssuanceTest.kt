package com.template

import com.google.common.collect.ImmutableList
import com.template.flows.AssetRequestFlow
import com.template.flows.IssueAssetFlow
import com.template.objects.Asset
import com.template.states.AssetState
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.FlowException
import net.corda.core.identity.CordaX500Name
import net.corda.core.node.services.Vault
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.utilities.getOrThrow
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.StartedMockNode
import net.corda.testing.node.TestCordapp
import org.junit.After
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import java.util.*
import kotlin.test.assertTrue

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class IssuanceTest {
    private lateinit var network: MockNetwork
    private lateinit var issuingNode: StartedMockNode
    private lateinit var clientNodeA: StartedMockNode
    private lateinit var clientNodeB: StartedMockNode
    private lateinit var clientNodeC: StartedMockNode
    private lateinit var clientNodeD: StartedMockNode

    @Before
    fun setup() {
        val testClientA = CordaX500Name("TestClientA", "New York", "US")
        val testClientB = CordaX500Name("TestClientB", "New York", "US")
        val testClientC = CordaX500Name("TestClientC", "New York", "US")
        val testClientD = CordaX500Name("TestClientD", "New York", "US")
        val issuing = CordaX500Name("IssuingNode", "New York", "US")

        network = MockNetwork(
                MockNetworkParameters(
                        threadPerNode = true,
                        cordappsForAllNodes = listOf(
                                TestCordapp.findCordapp("com.template.contracts")
                        )
                )
        )

        issuingNode = network.createPartyNode(issuing)
        clientNodeA = network.createPartyNode(testClientA)
        clientNodeB = network.createPartyNode(testClientB)
        clientNodeC = network.createPartyNode(testClientC)
        clientNodeD = network.createPartyNode(testClientD)

        listOf(clientNodeA, clientNodeB, clientNodeC, clientNodeD, issuingNode).forEach {
            it.registerInitiatedFlow(AssetRequestFlow.SendResponse::class.java)
            it.registerInitiatedFlow(IssueAssetFlow.ReceiveIssuance::class.java)
        }
    }

    @After
    fun tearDown() {
        network.stopNodes()
    }

    @Test
    fun `{01} Receives new issuance for a single unique asset`() {
        val a1 = Asset("uniqueId")

        val linearId = requestAssetIssuance(clientNodeA, a1)

        assertTrue { linearId.isNotEmpty() }

        val invoiceStateAndRef = queryNodeVaultByLinearId(clientNodeA, linearId)
        val issuingNodeStateAndRef = queryNodeVaultByLinearId(issuingNode, linearId)

        assertTrue { invoiceStateAndRef.ref == issuingNodeStateAndRef.ref }
    }

    @Test
    fun `{02} Adds a single extra participant to a unique asset`() {
        val a1 = Asset("uniqueId")

        val l1 = requestAssetIssuance(clientNodeA, a1)

        assertTrue { l1.isNotEmpty() }

        val l2 = requestAssetIssuance(clientNodeB, a1)

        assertTrue { l2.isNotEmpty() }

        assertTrue { l1 == l2 }

        val q1 = queryNodeVaultByLinearId(clientNodeA, l1)
        val q2 = queryNodeVaultByLinearId(clientNodeB, l2)

        assertTrue { q1.ref == q2.ref }
    }

    @Test
    fun `{03} Adds multiple extra participants to a unique asset`() {
        val a1 = Asset("uniqueId")

        val l1 = requestAssetIssuance(clientNodeA, a1)

        assertTrue { l1.isNotEmpty() }

        val l2 = requestAssetIssuance(clientNodeB, a1)

        assertTrue { l2.isNotEmpty() }

        val l3 = requestAssetIssuance(clientNodeC, a1)

        assertTrue { l3.isNotEmpty() }

        assertTrue { l1 == l2 }
        assertTrue { l2 == l3 }

        val q1 = queryNodeVaultByLinearId(clientNodeA, l1)
        val q2 = queryNodeVaultByLinearId(clientNodeB, l2)
        val q3 = queryNodeVaultByLinearId(clientNodeC, l3)

        assertTrue { q1.ref == q2.ref }
        assertTrue { q2.ref == q3.ref }
    }

    private fun requestAssetIssuance(node: StartedMockNode, asset: Asset) : String {
        val registerFlow = AssetRequestFlow.SubmitRequest(asset)
        val registerFuture = node.startFlow(registerFlow)
        return registerFuture.getOrThrow()
    }

    private fun queryNodeVaultByLinearId(node: StartedMockNode, linearId: String) : StateAndRef<AssetState> {
        val id = UniqueIdentifier(id= UUID.fromString(linearId))
        val queryCriteria = QueryCriteria.LinearStateQueryCriteria(null, ImmutableList.of(id), Vault.StateStatus.UNCONSUMED, null)
        val queryResponse = node.services.vaultService.queryBy<AssetState>(queryCriteria)

        if (queryResponse.states.size != 1) {
            throw FlowException("Could not locate unique state with linearId '$linearId'")
        }

        return queryResponse.states[0]
    }
}