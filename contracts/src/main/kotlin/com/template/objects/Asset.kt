package com.template.objects

import net.corda.core.serialization.CordaSerializable

@CordaSerializable
data class Asset(
        val uniqueId: String
)