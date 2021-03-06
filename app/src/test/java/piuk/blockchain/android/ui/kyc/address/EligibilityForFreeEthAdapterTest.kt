package piuk.blockchain.android.ui.kyc.address

import com.blockchain.android.testutils.rxInit
import piuk.blockchain.android.ui.getBlankNabuUser
import com.blockchain.nabu.datamanagers.NabuDataManager
import com.blockchain.nabu.models.responses.nabu.TierLevels
import com.blockchain.nabu.NabuToken
import com.nhaarman.mockito_kotlin.whenever
import io.reactivex.Single
import org.amshove.kluent.`should equal to`
import org.amshove.kluent.mock
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import piuk.blockchain.android.ui.validOfflineToken

class EligibilityForFreeEthAdapterTest {

    private lateinit var eligibilityForFreeEthAdapter: EligibilityForFreeEthAdapter
    private val nabuToken: NabuToken = mock()
    private val nabuDataManager: NabuDataManager = mock()

    @get:Rule
    val rx = rxInit {
        mainTrampoline()
    }

    @Before
    fun setUp() {
        eligibilityForFreeEthAdapter = EligibilityForFreeEthAdapter(nabuToken, nabuDataManager)
    }

    @Test
    fun `should not be eligible, if tier is lower than 2 and no tag contained`() {
        whenever(
            nabuToken.fetchNabuToken()
        ).thenReturn(Single.just(validOfflineToken))

        whenever(nabuDataManager.getUser(validOfflineToken))
            .thenReturn(Single.just(
                getBlankNabuUser()
                .copy(
                    tiers = TierLevels(1, 1, 2))
                .copy(tags = mapOf())
            ))

        val testEligibleObserver = eligibilityForFreeEthAdapter.isEligible().test()

        testEligibleObserver.values().single().apply {
            this `should equal to` false
        }
    }

    @Test
    fun `should not be eligible, if tier is lower than 2 and tag contained`() {
        whenever(
            nabuToken.fetchNabuToken()
        ).thenReturn(Single.just(validOfflineToken))

        whenever(nabuDataManager.getUser(validOfflineToken))
            .thenReturn(Single.just(
                getBlankNabuUser()
                .copy(tiers = TierLevels(1, 1, 2))
                .copy(tags = mapOf("POWER_PAX" to mapOf("some key" to "some key")))
            ))

        val testEligibleObserver = eligibilityForFreeEthAdapter.isEligible().test()

        testEligibleObserver.values().single().apply {
            this `should equal to` false
        }
    }

    @Test
    fun `should not be eligible, if tier is 2 and tag contained`() {
        whenever(
            nabuToken.fetchNabuToken()
        ).thenReturn(Single.just(validOfflineToken))

        whenever(nabuDataManager.getUser(validOfflineToken))
            .thenReturn(Single.just(
                getBlankNabuUser()
                .copy(tiers = TierLevels(2, 2, 2))
                .copy(tags = mapOf("POWER_PAX" to mapOf("some key" to "some key")))
            ))

        val testEligibleObserver = eligibilityForFreeEthAdapter.isEligible().test()

        testEligibleObserver.values().single().apply {
            this `should equal to` false
        }
    }

    @Test
    fun `should be eligible, if tier is 2 and no tag contained`() {
        whenever(
            nabuToken.fetchNabuToken()
        ).thenReturn(Single.just(validOfflineToken))

        whenever(nabuDataManager.getUser(validOfflineToken))
            .thenReturn(Single.just(
                getBlankNabuUser()
                .copy(tiers = TierLevels(2, 2, 2))
                .copy(tags = mapOf())
            ))

        val testEligibleObserver = eligibilityForFreeEthAdapter.isEligible().test()

        testEligibleObserver.values().single().apply {
            this `should equal to` true
        }
    }
}