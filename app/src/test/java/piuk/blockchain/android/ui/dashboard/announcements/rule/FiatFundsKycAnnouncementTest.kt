package piuk.blockchain.android.ui.dashboard.announcements.rule

import com.blockchain.nabu.datamanagers.CustodialWalletManager
import com.blockchain.nabu.datamanagers.Beneficiary
import com.blockchain.nabu.datamanagers.featureflags.Feature
import com.blockchain.nabu.datamanagers.featureflags.KycFeatureEligibility
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import io.reactivex.Single
import org.junit.Before
import org.junit.Test
import piuk.blockchain.android.ui.dashboard.announcements.DismissRecorder

class FiatFundsKycAnnouncementTest {

    private val dismissRecorder: DismissRecorder = mock()
    private val dismissEntry: DismissRecorder.DismissEntry = mock()
    private val kycFeatureEligibility: KycFeatureEligibility = mock()
    private val custodialWalletManager: CustodialWalletManager = mock()

    private lateinit var subject: FiatFundsKycAnnouncement

    @Before
    fun setUp() {
        whenever(dismissRecorder[FiatFundsKycAnnouncement.DISMISS_KEY]).thenReturn(dismissEntry)
        whenever(dismissEntry.prefsKey).thenReturn(FiatFundsKycAnnouncement.DISMISS_KEY)

        subject =
            FiatFundsKycAnnouncement(
                dismissRecorder = dismissRecorder,
                featureEligibility = kycFeatureEligibility,
                custodialWalletManager = custodialWalletManager
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
    fun `should show, when not already shown and user is kyc gold without linked banks`() {
        whenever(dismissEntry.isDismissed).thenReturn(false)
        whenever(kycFeatureEligibility.isEligibleFor(Feature.SIMPLEBUY_BALANCE))
            .thenReturn(Single.just(true))

        whenever(custodialWalletManager.getLinkedBeneficiaries()).thenReturn(Single.just(emptyList()))

        subject.shouldShow()
            .test()
            .assertValue { it }
            .assertValueCount(1)
            .assertComplete()
    }

    @Test
    fun `should not show, when not already shown and user is kyc gold but has linked banks`() {
        whenever(dismissEntry.isDismissed).thenReturn(false)
        whenever(kycFeatureEligibility.isEligibleFor(Feature.SIMPLEBUY_BALANCE))
            .thenReturn(Single.just(true))

        whenever(custodialWalletManager.getLinkedBeneficiaries()).thenReturn(
            Single.just(listOf(Beneficiary("", "", "", "")))
        )

        subject.shouldShow()
            .test()
            .assertValue { !it }
            .assertValueCount(1)
            .assertComplete()
    }

    @Test
    fun `should not show, when not already shown and user is not kyc gold and has no linked banks`() {
        whenever(dismissEntry.isDismissed).thenReturn(false)
        whenever(kycFeatureEligibility.isEligibleFor(Feature.SIMPLEBUY_BALANCE))
            .thenReturn(Single.just(false))

        whenever(custodialWalletManager.getLinkedBeneficiaries()).thenReturn(Single.just(emptyList()))

        subject.shouldShow()
            .test()
            .assertValue { !it }
            .assertValueCount(1)
            .assertComplete()
    }
}
