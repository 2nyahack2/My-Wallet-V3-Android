package piuk.blockchain.android.kyc

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

@Config(sdk = [23], application = BlockchainTestApplication::class)
@RunWith(RobolectricTestRunner::class)
class KycDeepLinkHelperTest {

    @Test
    fun `no uri`() {
        KycDeepLinkHelper(
            mock {
                on { getPendingLinks(any()) } `it returns` Maybe.empty()
            }
        ).getLink(mock())
            .test()
            .assertNoErrors()
            .assertValue(KycLinkState.NoUri)
    }

    @Test
    fun `not a resubmission uri`() {
        KycDeepLinkHelper(givenPendingUri("https://login.blockchain.com/#/open/referral?campaign=sunriver"))
            .getLink(mock())
            .test()
            .assertNoErrors()
            .assertValue(KycLinkState.NoUri)
    }

    @Test
    fun `extract that it is a resubmission deeplink`() {
        KycDeepLinkHelper(givenPendingUri("https://login.blockchain.com/login?deep_link_path=verification"))
            .getLink(mock())
            .test()
            .assertNoErrors()
            .assertValue(KycLinkState.Resubmit)
    }

    @Test
    fun `extract that it is an email verified deeplink`() {
        val url = "https://login.blockchain.com/login?deep_link_path=email_verified&context=kyc"
        KycDeepLinkHelper(givenPendingUri(url))
            .getLink(mock())
            .test()
            .assertNoErrors()
            .assertValue(KycLinkState.EmailVerified)
    }

    @Test
    fun `extract that it is a general kyc deeplink with campaign info`() {
        val url = "https://login.blockchain.com/#/open/kyc?tier=2&deep_link_path=kyc&campaign=sunriver"
        KycDeepLinkHelper(givenPendingUri(url))
            .getLink(mock())
            .test()
            .assertNoErrors()
            .assertValue(KycLinkState.General(CampaignData("sunriver", false)))
    }

    @Test
    fun `extract that it is a general kyc deeplink without campaign info`() {
        KycDeepLinkHelper(givenPendingUri("https://login.blockchain.com/#/open/kyc?tier=2&deep_link_path=kyc"))
            .getLink(mock())
            .test()
            .assertNoErrors()
            .assertValue(KycLinkState.General(null))
    }
}

private fun givenPendingUri(uri: String): PendingLink =
    mock {
        on { getPendingLinks(any()) } `it returns` Maybe.just(Uri.parse(uri))
    }