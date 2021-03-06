package piuk.blockchain.android.ui.dashboard.announcements.rule

import com.blockchain.notifications.analytics.Analytics
import com.blockchain.remoteconfig.FeatureFlag
import com.blockchain.nabu.datamanagers.EligibilityProvider
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.whenever
import info.blockchain.balance.CryptoCurrency
import io.reactivex.Single
import org.amshove.kluent.mock
import org.junit.Before
import org.junit.Test
import piuk.blockchain.android.coincore.Coincore
import piuk.blockchain.android.coincore.btc.BtcCryptoWalletAccount
import piuk.blockchain.android.coincore.impl.CryptoAccountNonCustodialGroup
import piuk.blockchain.android.ui.dashboard.announcements.DismissRecorder

class SellIntroAnnouncementTest {

    private val dismissRecorder: DismissRecorder = mock()
    private val sellFeatureFlag: FeatureFlag = mock()
    private val coincore: Coincore = mock()
    private val analytics: Analytics = mock()
    private val eligibilityProvider: EligibilityProvider = mock()
    private val dismissEntry: DismissRecorder.DismissEntry = mock()

    private lateinit var subject: SellIntroAnnouncement

    @Before
    fun setUp() {
        whenever(dismissRecorder[SellIntroAnnouncement.DISMISS_KEY]).thenReturn(dismissEntry)
        whenever(dismissEntry.prefsKey).thenReturn(SellIntroAnnouncement.DISMISS_KEY)

        subject =
            SellIntroAnnouncement(
                dismissRecorder = dismissRecorder,
                eligibilityProvider = eligibilityProvider,
                coincore = coincore,
                analytics = analytics
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
    fun `should show, when not already shown`() {
        whenever(dismissEntry.isDismissed).thenReturn(false)
        whenever(eligibilityProvider.isEligibleForSimpleBuy(any(), any())).thenReturn(Single.just(true))
        whenever(sellFeatureFlag.enabled).thenReturn(Single.just(true))
        whenever(eligibilityProvider.defCurrency).thenReturn("GBP")

        val account: BtcCryptoWalletAccount = mock()
        whenever(account.isFunded).thenReturn(true)
        val acg = CryptoAccountNonCustodialGroup(
            CryptoCurrency.BTC, "label", listOf(account)
        )
        whenever(coincore.allWallets()).thenReturn(Single.just(acg))

        subject.shouldShow()
            .test()
            .assertValue { it }
            .assertValueCount(1)
            .assertComplete()
    }
}