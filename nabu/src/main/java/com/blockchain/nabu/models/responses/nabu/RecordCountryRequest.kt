package com.blockchain.nabu.models.responses.nabu

internal data class RecordCountryRequest(
    val jwt: String,
    val countryCode: String,
    val notifyWhenAvailable: Boolean,
    val state: String?
)