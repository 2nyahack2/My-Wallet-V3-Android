package piuk.blockchain.android.ui.dashboard.announcements.rule

import com.blockchain.nabu.status.KycTiersQueries
import com.nhaarman.mockito_kotlin.whenever
import io.reactivex.Single
import org.amshove.kluent.mock
import org.junit.Before
import org.junit.Test
import piuk.blockchain.android.ui.dashboard.announcements.DismissRecorder

class KycResubmissionAnnouncementTest {
    private val dismissRecorder: DismissRecorder = mock()
    private val dismissEntry: DismissRecorder.DismissEntry = mock()
    private val kycQueries: KycTiersQueries = mock()

    private lateinit var subject: KycResubmissionAnnouncement

    @Before
    fun setUp() {
        whenever(dismissRecorder[KycResubmissionAnnouncement.DISMISS_KEY])
            .thenReturn(dismissEntry)
        whenever(dismissEntry.prefsKey)
            .thenReturn(KycResubmissionAnnouncement.DISMISS_KEY)

        subject = KycResubmissionAnnouncement(
            kycTiersQueries = kycQueries,
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
    fun `should not show, if kyc resubmission is not required`() {
        whenever(dismissEntry.isDismissed).thenReturn(false)
        whenever(kycQueries.isKycResubmissionRequired()).thenReturn(Single.just(false))

        subject.shouldShow()
            .test()
            .assertValue { !it }
            .assertValueCount(1)
            .assertComplete()
    }

    @Test
    fun `should show, if kyc resubmission is required`() {
        whenever(dismissEntry.isDismissed).thenReturn(false)
        whenever(kycQueries.isKycResubmissionRequired()).thenReturn(Single.just(true))

        subject.shouldShow()
            .test()
            .assertValue { it }
            .assertValueCount(1)
            .assertComplete()
    }
}