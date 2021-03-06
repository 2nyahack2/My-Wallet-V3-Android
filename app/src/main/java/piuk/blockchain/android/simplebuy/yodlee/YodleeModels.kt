package piuk.blockchain.android.simplebuy.yodlee

import com.google.gson.annotations.SerializedName

data class FastLinkMessage(val type: String?, val data: MessageData?)

data class MessageData(
    val fnToCall: String?,
    val title: String?,
    val code: String?,
    val message: String?,
    val providerName: String?,
    val requestId: String?,
    val isMFAError: Boolean?,
    val reason: String?,
    val status: String?,
    val action: String?,
    val providerAccountId: String?,
    val providerId: String?,
    val sites: List<SiteData>?,
    @SerializedName("url")
    val externalUrl: String?
)

data class SiteData(
    val status: String?,
    val providerId: String?,
    val requestId: String?,
    val providerName: String?,
    val providerAccountId: String?,
    val accountId: String?
)