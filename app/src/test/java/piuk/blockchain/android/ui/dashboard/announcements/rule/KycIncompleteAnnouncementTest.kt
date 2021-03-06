package piuk.blockchain.android.ui.dashboard.announcements.rule

import com.blockchain.nabu.status.KycTiersQueries
import piuk.blockchain.android.campaign.SunriverCampaignRegistration
import com.nhaarman.mockito_kotlin.whenever
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import org.amshove.kluent.mock
import org.junit.Before
import org.junit.Test
import piuk.blockchain.android.ui.dashboard.announcements.DismissRecorder

class KycIncompleteAnnouncementTest {

    private val dismissRecorder: DismissRecorder = mock()
    private val dismissEntry: DismissRecorder.DismissEntry = mock()
    private val kycQueries: KycTiersQueries = mock()
    private val campaignRegistration: SunriverCampaignRegistration = mock()
    private lateinit var subject: KycIncompleteAnnouncement

    @Before
    fun setUp() {
        whenever(dismissRecorder[KycIncompleteAnnouncement.DISMISS_KEY])
            .thenReturn(dismissEntry)
        whenever(dismissEntry.prefsKey)
            .thenReturn(KycIncompleteAnnouncement.DISMISS_KEY)

        subject = KycIncompleteAnnouncement(
            kycTiersQueries = kycQueries,
            sunriverCampaignRegistration = campaignRegistration,
            mainScheduler = Schedulers.trampoline(),
            dismissRecorder = dismissRecorder
        )
    }

    @Test
    fun `should not show, when already shown`() {
        whenever(dismissEntry.isDismissed).thenReturn(true)

        subject.shouldShow()
            .test()
            .assertValue { !it }
            .assertValueCount(1)
            .assertComplete()
    }

    @Test
    fun `should not show, if kyc is not ongoing`() {
        whenever(dismissEntry.isDismissed).thenReturn(false)
        whenever(kycQueries.isKycInProgress()).thenReturn(Single.just(false))

        subject.shouldShow()
            .test()
            .assertValue { !it }
            .assertValueCount(1)
            .assertComplete()
    }

    @Test
    fun `should show, if kyc is ongoing`() {
        whenever(dismissEntry.isDismissed).thenReturn(false)
        whenever(kycQueries.isKycInProgress()).thenReturn(Single.just(true))

        subject.shouldShow()
            .test()
            .assertValue { it }
            .assertValueCount(1)
            .assertComplete()
    }
}