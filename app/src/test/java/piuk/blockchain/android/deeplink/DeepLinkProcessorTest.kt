package piuk.blockchain.android.deeplink

import android.content.Intent
import android.net.Uri
import com.blockchain.nabu.models.responses.nabu.CampaignData
import com.blockchain.notifications.links.PendingLink
import com.nhaarman.mockito_kotlin.mock
import io.reactivex.Maybe
import org.amshove.kluent.`it returns`
import org.amshove.kluent.any
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import piuk.blockchain.android.BlockchainTestApplication
import piuk.blockchain.android.kyc.KycDeepLinkHelper
import piuk.blockchain.android.kyc.KycLinkState
import piuk.blockchain.android.sunriver.CampaignLinkState
import piuk.blockchain.android.sunriver.SunriverDeepLinkHelper
import piuk.blockchain.android.thepit.ThePitDeepLinkParser

@Config(sdk = [23], application = BlockchainTestApplication::class)
@RunWith(RobolectricTestRunner::class)
class DeepLinkProcessorTest {

    @Test
    fun `unknown uri`() {
        givenUriExpect(
            "https://login.blockchain.com/",
            LinkState.NoUri
        )
    }

    @Test
    fun `sunriver uri`() {
        givenUriExpect(
            "https://login.blockchain.com/#/open/referral?campaign=sunriver",
            LinkState.SunriverDeepLink(
                CampaignLinkState.Data(CampaignData("sunriver", newUser = false))
            )
        )
    }

    @Test
    fun `kyc resubmit uri`() {
        givenUriExpect(
            "https://login.blockchain.com/login?deep_link_path=verification", LinkState.KycDeepLink(
                KycLinkState.Resubmit
            )
        )
    }

    @Test
    fun `kyc email verified uri`() {
        givenUriExpect(
            "https://login.blockchain.com/login?deep_link_path=email_verified&context=kyc", LinkState.KycDeepLink(
                KycLinkState.EmailVerified
            )
        )
    }

    @Test
    fun `pit email verified uri`() {
        givenUriExpect(
            "https://login.blockchain.com/login?deep_link_path=email_verified&context=pit_signup",
            LinkState.EmailVerifiedDeepLink(EmailVerifiedLinkState.FromPitLinking)
        )
    }

    @Test
    fun `general kyc uri with campaign`() {
        val url = "https://login.blockchain.com/#/open/kyc?tier=2&deep_link_path=kyc&campaign=sunriver"
        givenUriExpect(url, LinkState.KycDeepLink(
                KycLinkState.General(CampaignData("sunriver", false))
            )
        )
    }

    @Test
    fun `general kyc uri without campaign`() {
        givenUriExpect(
            "https://login.blockchain.com/#/open/kyc?tier=2&deep_link_path=kyc",
            LinkState.KycDeepLink(KycLinkState.General(null)
            )
        )
    }

    @Test
    fun `pit to wallet linking`() {
        givenUriExpect(
            "https://wallet-frontend-v4.dev.blockchain.info/#/open/link-account?link_id=$LINK_ID",
            LinkState.ThePitDeepLink(LINK_ID)
        )
    }

    companion object {
        private const val LINK_ID = "11111111-2222-3333-4444-555555556666"
    }
}

private fun givenUriExpect(uri: String, expected: LinkState) {
    val i: Intent = mock()

    DeepLinkProcessor(
        linkHandler = givenPendingUri(uri),
        emailVerifiedLinkHelper = EmailVerificationDeepLinkHelper(),
        kycDeepLinkHelper = KycDeepLinkHelper(mock()),
        sunriverDeepLinkHelper = SunriverDeepLinkHelper(mock()),
        thePitDeepLinkParser = ThePitDeepLinkParser()
    ).getLink(i)
        .test()
        .assertNoErrors()
        .assertValue(expected)
}

private fun givenPendingUri(uri: String): PendingLink =
    mock {
        on { getPendingLinks(any()) } `it returns` Maybe.just(Uri.parse(uri))
    }
