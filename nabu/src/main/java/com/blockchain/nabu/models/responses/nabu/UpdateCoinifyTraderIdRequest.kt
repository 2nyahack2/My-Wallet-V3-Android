package com.blockchain.nabu.models.responses.nabu

import com.blockchain.serialization.JsonSerializable

internal data class UpdateCoinifyTraderIdRequest(
    val coinifyTraderId: Int
) : JsonSerializable