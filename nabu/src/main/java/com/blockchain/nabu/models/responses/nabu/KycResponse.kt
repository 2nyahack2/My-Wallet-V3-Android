package com.blockchain.nabu.models.responses.nabu

import com.blockchain.serialization.JsonSerializable
import java.math.BigDecimal

data class TierResponse(
    val index: Int,
    val name: String,
    val state: KycTierState,
    val limits: LimitsJson?
) : JsonSerializable

data class LimitsJson(
    val currency: String,
    val daily: BigDecimal?,
    val annual: BigDecimal?
) : JsonSerializable