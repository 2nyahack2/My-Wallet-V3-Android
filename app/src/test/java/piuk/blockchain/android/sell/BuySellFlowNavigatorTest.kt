package piuk.blockchain.android.sell

import com.blockchain.preferences.CurrencyPrefs
import com.blockchain.nabu.datamanagers.CustodialWalletManager
import com.blockchain.nabu.datamanagers.EligibilityProvider
import com.blockchain.nabu.datamanagers.OrderState
import com.blockchain.nabu.models.responses.nabu.KycTiers
import com.blockchain.nabu.service.TierService
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import org.junit.Before
import org.junit.Test
import piuk.blockchain.android.simplebuy.SimpleBuyModel
import piuk.blockchain.android.simplebuy.SimpleBuyState
import piuk.blockchain.android.ui.sell.BuySellFlowNavigator
import piuk.blockchain.android.ui.sell.BuySellIntroAction

class BuySellFlowNavigatorTest {
    private val simpleBuyModel: SimpleBuyModel = mock()
    private val currencyPrefs: CurrencyPrefs = mock()
    private val custodialWalletManager: CustodialWalletManager = mock()
    private val tierService: TierService = mock()
    private val eligibilityProvider: EligibilityProvider = mock()
    private lateinit var subject: BuySellFlowNavigator

    @Before
    fun setUp() {
        subject = BuySellFlowNavigator(
            simpleBuyModel, currencyPrefs, custodialWalletManager, eligibilityProvider, tierService
        )
        whenever(tierService.tiers()).thenReturn(Single.just(KycTiers.default()))
        whenever(eligibilityProvider.isEligibleForSimpleBuy(any(), any())).thenReturn(Single.just(true))
    }

    @Test
    fun `when buy state is pending and currency is right, hasPendingBuy should be true`() {
        whenever(simpleBuyModel.state).thenReturn(
            Observable.just(SimpleBuyState(orderState = OrderState.PENDING_EXECUTION, fiatCurrency = "GBP"))
        )
        whenever(currencyPrefs.selectedFiatCurrency).thenReturn("GBP")
        whenever(eligibilityProvider.defCurrency).thenReturn("GBP")

        whenever(custodialWalletManager.getSupportedFiatCurrencies()).thenReturn(Single.just(listOf("EUR", "GBP")))
        whenever(custodialWalletManager.isCurrencySupportedForSimpleBuy("GBP"))
            .thenReturn(Single.just(true))

        val test = subject.navigateTo().test()

        test.assertValue(BuySellIntroAction.DisplayBuySellIntro(isGoldButNotEligible = false, hasPendingBuy = true))
    }

    @Test
    fun `whenΒuyStateIsNotPendingAndCurrencyIsNotSupportedThenSelectCurrencyShouldBeLaunchedWithAllSupportedCrncies`() {
        whenever(simpleBuyModel.state).thenReturn(Observable.just(SimpleBuyState()))
        whenever(currencyPrefs.selectedFiatCurrency).thenReturn("USD")
        whenever(eligibilityProvider.defCurrency).thenReturn("USD")
        whenever(custodialWalletManager.getSupportedFiatCurrencies()).thenReturn(Single.just(listOf("EUR", "GBP")))
        whenever(custodialWalletManager.isCurrencySupportedForSimpleBuy("USD"))
            .thenReturn(Single.just(false))

        val test = subject.navigateTo().test()

        test.assertValue(BuySellIntroAction.NavigateToCurrencySelection(listOf("EUR", "GBP")))
    }

    @Test
    fun `whenBuyStateIsNotPendingCurrencyIsSupportedAndSellIsEnableNormalBuySellUiIsDisplayed`() {
        whenever(simpleBuyModel.state).thenReturn(Observable.just(SimpleBuyState()))
        whenever(currencyPrefs.selectedFiatCurrency).thenReturn("USD")
        whenever(custodialWalletManager.getSupportedFiatCurrencies()).thenReturn(Single.just(listOf("EUR", "USD")))
        whenever(custodialWalletManager.isCurrencySupportedForSimpleBuy("USD"))
            .thenReturn(Single.just(true))
        whenever(eligibilityProvider.defCurrency).thenReturn("USD")
        val test = subject.navigateTo().test()

        test.assertValue(BuySellIntroAction.DisplayBuySellIntro(false, false))
    }

    @Test
    fun `whenBuyStateIsPendingConfirmationOrderIsCancelledAndBuySellUiIsDisplayed`() {
        whenever(simpleBuyModel.state).thenReturn(
            Observable.just(SimpleBuyState(id = "ORDERID", orderState = OrderState.PENDING_CONFIRMATION))
        )
        whenever(eligibilityProvider.defCurrency).thenReturn("USD")
        whenever(currencyPrefs.selectedFiatCurrency).thenReturn("USD")
        whenever(custodialWalletManager.getSupportedFiatCurrencies()).thenReturn(Single.just(listOf("EUR", "USD")))
        whenever(custodialWalletManager.isCurrencySupportedForSimpleBuy("USD"))
            .thenReturn(Single.just(true))
        whenever(custodialWalletManager.deleteBuyOrder("ORDERID"))
            .thenReturn(Completable.complete())

        val test = subject.navigateTo().test()

        test.assertValue(BuySellIntroAction.DisplayBuySellIntro(isGoldButNotEligible = false, hasPendingBuy = false))
        verify(custodialWalletManager).deleteBuyOrder("ORDERID")
    }
}