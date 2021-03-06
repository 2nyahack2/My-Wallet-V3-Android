package piuk.blockchain.android.ui.dashboard.announcements.rule

import com.blockchain.nabu.datamanagers.featureflags.Feature
import com.blockchain.nabu.datamanagers.featureflags.KycFeatureEligibility
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import io.reactivex.Single
import org.junit.Before
import org.junit.Test
import piuk.blockchain.android.ui.dashboard.announcements.DismissRecorder

class FiatFundsNoKycAnnouncementTest {

    private val dismissRecorder: DismissRecorder = mock()
    private val dismissEntry: DismissRecorder.DismissEntry = mock()
    private val kycFeatureEligibility: KycFeatureEligibility = mock()

    private lateinit var subject: FiatFundsNoKycAnnouncement

    @Before
    fun setUp() {
        whenever(dismissRecorder[FiatFundsNoKycAnnouncement.DISMISS_KEY]).thenReturn(dismissEntry)
        whenever(dismissEntry.prefsKey).thenReturn(FiatFundsNoKycAnnouncement.DISMISS_KEY)

        subject =
            FiatFundsNoKycAnnouncement(
                dismissRecorder = dismissRecorder,
                featureEligibility = kycFeatureEligibility
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
    fun `should show, when not already shown and user is not kyc gold`() {
        whenever(dismissEntry.isDismissed).thenReturn(false)
        whenever(kycFeatureEligibility.isEligibleFor(Feature.SIMPLEBUY_BALANCE))
            .thenReturn(Single.just(false))

        subject.shouldShow()
            .test()
            .assertValue { it }
            .assertValueCount(1)
            .assertComplete()
    }

    @Test
    fun `should not show, when not already shown and user is kyc gold`() {
        whenever(dismissEntry.isDismissed).thenReturn(false)
        whenever(kycFeatureEligibility.isEligibleFor(Feature.SIMPLEBUY_BALANCE))
            .thenReturn(Single.just(true))

        subject.shouldShow()
            .test()
            .assertValue { !it }
            .assertValueCount(1)
            .assertComplete()
    }
}
