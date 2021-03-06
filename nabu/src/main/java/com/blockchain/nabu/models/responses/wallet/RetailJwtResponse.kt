package com.blockchain.nabu.models.responses.wallet

import com.squareup.moshi.Json

internal data class RetailJwtResponse(
    @field:Json(name = "success") val isSuccessful: Boolean,
    val token: String?,
    val error: String?
)