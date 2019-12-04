package com.monetago.fp.schema

import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import javax.persistence.*


object AssetSchema

object AssetSchemaV1 : MappedSchema(
        schemaFamily = AssetSchema.javaClass,
        version = 1,
        mappedTypes = listOf(PersistentAsset::class.java)) {
    @Entity
    @Table(
            name = "assets",
            indexes = arrayOf(Index(name = "unique_id_idx", columnList = "uniqueId"))
    )
    class PersistentAsset(
            @Column(name = "uniqueId")
            var uniqueId: String

    ) : PersistentState() {
        constructor() : this("")
    }
}
