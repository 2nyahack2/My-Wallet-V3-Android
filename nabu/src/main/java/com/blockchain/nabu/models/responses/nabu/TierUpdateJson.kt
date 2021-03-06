package com.blockchain.nabu.models.responses.nabu

import com.blockchain.serialization.JsonSerializable

internal data class TierUpdateJson(
    val selectedTier: Int
) : JsonSerializable
