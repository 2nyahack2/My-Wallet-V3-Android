package piuk.blockchain.android.kyc

import android.content.Intent
import android.net.Uri
import com.blockchain.nabu.models.responses.nabu.CampaignData
import com.blockchain.notifications.links.PendingLink
import io.reactivex.Maybe
import io.reactivex.Single

fun Uri.ignoreFragment(): Uri {
    val cleanedUri = this.toString().replace("/#", "")
    return Uri.parse(cleanedUri)
}

class KycDeepLinkHelper(
    private val linkHandler: PendingLink
) {

    fun getLink(intent: Intent): Single<KycLinkState> =
        linkHandler.getPendingLinks(intent)
            .map(this::mapUri)
            .switchIfEmpty(Maybe.just(KycLinkState.NoUri))
            .toSingle()
            .onErrorResumeNext { Single.just(KycLinkState.NoUri) }

    fun mapUri(uri: Uri): KycLinkState {
        val uriWithoutFragment = uri.ignoreFragment()
        val name = uriWithoutFragment.getQueryParameter("deep_link_path")
        return when (name) {
            "verification" -> KycLinkState.Resubmit
            "email_verified" -> {
                val ctx = uriWithoutFragment.getQueryParameter("context")?.toLowerCase()
                return if (KYC_CONTEXT == ctx) KycLinkState.EmailVerified else KycLinkState.NoUri
            }
            "kyc" -> {
                val campaign = uriWithoutFragment.getQueryParameter("campaign")
                val campaignData = if (!campaign.isNullOrEmpty()) CampaignData(campaign, false) else null
                return KycLinkState.General(campaignData)
            }
            else -> KycLinkState.NoUri
        }
    }

    companion object {
        const val KYC_CONTEXT = "kyc"
    }
}

sealed class KycLinkState {
    /**
     * Deep link into the email confirmation part of KYC
     */
    object EmailVerified : KycLinkState()

    /**
     * General deep link into KYC
     */
    data class General(val campaignData: CampaignData?) : KycLinkState()

    /**
     * Not a valid KYC deep link URI
     */
    object NoUri : KycLinkState()

    /**
     * Deep link into identity verification part of KYC
     */
    object Resubmit : KycLinkState()
}
