package piuk.blockchain.androidcore.data.auth.metadata

import com.blockchain.serialization.JsonSerializable
import com.squareup.moshi.Json
import piuk.blockchain.androidcore.utils.extensions.isValidGuid

data class WalletCredentialsMetadata(
    @field:Json(name = "guid") val guid: String,
    @field:Json(name = "password") val password: String,
    @field:Json(name = "sharedKey") val sharedKey: String
) : JsonSerializable {

    fun isValid() = guid.isValidGuid() && password.isNotEmpty() && sharedKey.isNotEmpty()

    companion object {
        const val WALLET_CREDENTIALS_METADATA_NODE = 12
    }
}
