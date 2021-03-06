package piuk.blockchain.android.ui.dashboard.announcements

import com.blockchain.nabu.NabuToken
import com.blockchain.nabu.datamanagers.NabuDataManager
import com.blockchain.nabu.models.responses.nabu.KycTierState
import com.blockchain.nabu.models.responses.nabu.KycTiers
import com.blockchain.nabu.models.responses.nabu.LimitsJson
import com.blockchain.nabu.models.responses.nabu.TierResponse
import com.blockchain.nabu.service.TierService
import com.nhaarman.mockito_kotlin.whenever
import io.reactivex.Single
import org.amshove.kluent.mock
import org.junit.Before
import org.junit.Test
import piuk.blockchain.android.simplebuy.SimpleBuyState
import piuk.blockchain.android.simplebuy.SimpleBuySyncFactory
import piuk.blockchain.android.ui.tiers
import piuk.blockchain.androidcore.data.settings.SettingsDataManager

class AnnouncementQueriesTest {

    private val nabuToken: NabuToken = mock()
    private val settings: SettingsDataManager = mock()
    private val nabu: NabuDataManager = mock()
    private val tierService: TierService = mock()

    private val sbSync: SimpleBuySyncFactory = mock()

    private val sampleLimits = LimitsJson("", 0.toBigDecimal(), 0.toBigDecimal())

    private lateinit var subject: AnnouncementQueries

    @Before
    fun setUp() {
        subject = AnnouncementQueries(
            nabuToken = nabuToken,
            settings = settings,
            nabu = nabu,
            tierService = tierService,
            sbStateFactory = sbSync
        )
    }

    @Test
    fun `isTier1Or2Verified returns true for tier1 verified`() {

        whenever(tierService.tiers()).thenReturn(
            Single.just(
                KycTiers(
                    listOf(
                        TierResponse(0,
                            "",
                            KycTierState.None,
                            sampleLimits
                        ),
                        TierResponse(0,
                            "",
                            KycTierState.Verified,
                            sampleLimits
                        ),
                        TierResponse(0,
                            "",
                            KycTierState.None,
                            sampleLimits
                        )
                    )
                )
            )
        )

        subject.isTier1Or2Verified()
            .test()
            .assertValue { it }
            .assertValueCount(1)
            .assertComplete()
    }

    @Test
    fun `isTier1Or2Verified returns true for tier2 verified`() {
        whenever(tierService.tiers()).thenReturn(
            Single.just(
                KycTiers(
                    listOf(
                        TierResponse(
                            0,
                            "",
                            KycTierState.None,
                            sampleLimits
                        ),
                        TierResponse(
                            0,
                            "",
                            KycTierState.Verified,
                            sampleLimits
                        ),
                        TierResponse(
                            0,
                            "",
                            KycTierState.Verified,
                            sampleLimits
                        )
                    )
                )
            )
        )

        subject.isTier1Or2Verified()
            .test()
            .assertValue { it }
            .assertValueCount(1)
            .assertComplete()
    }

    @Test
    fun `isTier1Or2Verified returns false if not verified`() {
        whenever(tierService.tiers()).thenReturn(
            Single.just(
                KycTiers(
                    listOf(
                        TierResponse(
                            0,
                            "",
                            KycTierState.None,
                            sampleLimits
                        ),
                        TierResponse(
                            0,
                            "",
                            KycTierState.None,
                            sampleLimits
                        ),
                        TierResponse(
                            0,
                            "",
                            KycTierState.None,
                            sampleLimits
                        )
                    )
                )
            )
        )

        subject.isTier1Or2Verified()
            .test()
            .assertValue { !it }
            .assertValueCount(1)
            .assertComplete()
    }

    @Test
    fun `isSimpleBuyKycInProgress - no local simple buy state exists, return false`() {
        whenever(sbSync.currentState()).thenReturn(null)

        subject.isSimpleBuyKycInProgress()
            .test()
            .assertValue { !it }
            .assertValueCount(1)
            .assertComplete()
    }

    @Test
    fun `isSimpleBuyKycInProgress - local simple buy state exists but has finished kyc, return false`() {
        val state: SimpleBuyState = mock()
        whenever(state.kycStartedButNotCompleted).thenReturn(false)
        whenever(tierService.tiers()).thenReturn(Single.just(tiers(KycTierState.Verified, KycTierState.Verified)))
        whenever(sbSync.currentState()).thenReturn(state)

        subject.isSimpleBuyKycInProgress()
            .test()
            .assertValue { !it }
            .assertValueCount(1)
            .assertComplete()
    }

    @Test
    fun `isSimpleBuyKycInProgress - local simple buy state exists and has finished kyc, return true`() {
        val state: SimpleBuyState = mock()
        whenever(state.kycStartedButNotCompleted).thenReturn(true)
        whenever(state.kycVerificationState).thenReturn(null)

        whenever(tierService.tiers()).thenReturn(Single.just(tiers(KycTierState.Verified, KycTierState.None)))
        whenever(sbSync.currentState()).thenReturn(state)

        subject.isSimpleBuyKycInProgress()
            .test()
            .assertValue { it }
            .assertValueCount(1)
            .assertComplete()
    }

    @Test
    fun `isSimpleBuyKycInProgress - simple buy state is not finished, and kyc state is pending - as expected`() {
        val state: SimpleBuyState = mock()
        whenever(state.kycStartedButNotCompleted).thenReturn(true)
        whenever(tierService.tiers()).thenReturn(Single.just(tiers(KycTierState.Pending, KycTierState.None)))
        whenever(sbSync.currentState()).thenReturn(state)
        whenever(tierService.tiers()).thenReturn(Single.just(tiers(KycTierState.Pending, KycTierState.None)))

        subject.isSimpleBuyKycInProgress()
            .test()
            .assertValue { it }
            .assertValueCount(1)
            .assertComplete()
    }

    // Belt and braces checks: add double check that the SB state doesn't think kyc data has been submitted
    // to patch AND-2790, 2801. This _may_ be insufficient, though. If it doesn't solve the problem, we may have to
    // check backend kyc state ourselves...

    @Test
    fun `isSimpleBuyKycInProgress - SB state reports unfinished, but kyc docs are submitted - belt & braces case`() {
        val state: SimpleBuyState = mock()
        whenever(state.kycStartedButNotCompleted).thenReturn(true)

        whenever(tierService.tiers()).thenReturn(Single.just(tiers(KycTierState.Pending, KycTierState.UnderReview)))
        whenever(sbSync.currentState()).thenReturn(state)

        subject.isSimpleBuyKycInProgress()
            .test()
            .assertValue { !it }
            .assertValueCount(1)
            .assertComplete()
    }

    @Test
    fun `isSimpleBuyKycInProgress - SB state reports unfinished, but kyc docs are submitted - belt & braces case 2`() {
        val state: SimpleBuyState = mock()
        whenever(state.kycStartedButNotCompleted).thenReturn(true)
        whenever(tierService.tiers()).thenReturn(Single.just(tiers(KycTierState.Pending, KycTierState.Verified)))
        whenever(sbSync.currentState()).thenReturn(state)

        subject.isSimpleBuyKycInProgress()
            .test()
            .assertValue { !it }
            .assertValueCount(1)
            .assertComplete()
    }

    companion object {
        private const val BUY_ORDER_ID = "1234567890"
    }
}
