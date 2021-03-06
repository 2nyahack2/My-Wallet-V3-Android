package com.blockchain.nabu.metadata

import com.blockchain.serialization.JsonSerializable
import com.squareup.moshi.Json

data class NabuCredentialsMetadata(
    @field:Json(name = "user_id") val userId: String,
    @field:Json(name = "lifetime_token") val lifetimeToken: String
) : JsonSerializable {

    fun isValid() = userId.isNotEmpty() && lifetimeToken.isNotEmpty()

    companion object {
        const val USER_CREDENTIALS_METADATA_NODE = 10
    }
}
