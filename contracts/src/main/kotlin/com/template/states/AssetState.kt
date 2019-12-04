package com.template.states

import com.monetago.fp.schema.AssetSchemaV1
import com.template.contracts.AssetContract
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import java.lang.IllegalArgumentException

@BelongsToContract(AssetContract::class)
data class AssetState(
        val parties: List<Party>,
        val issuingNode: Party,
        val uniqueId: String,
        override var linearId: UniqueIdentifier = UniqueIdentifier()) : LinearState, QueryableState {

    override var participants: List<Party> = parties

    override fun generateMappedObject(schema: MappedSchema): PersistentState {
        return when (schema) {
            is AssetSchemaV1 -> AssetSchemaV1.PersistentAsset(this.uniqueId)
            else -> throw IllegalArgumentException("Unrecognized schema: $schema")
        }
    }

    override fun supportedSchemas(): Iterable<MappedSchema> = listOf(AssetSchemaV1)
}
