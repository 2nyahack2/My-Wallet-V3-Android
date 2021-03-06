package piuk.blockchain.android.simplebuy

import com.blockchain.preferences.CurrencyPrefs
import com.blockchain.nabu.datamanagers.CustodialWalletManager
import com.blockchain.nabu.models.responses.nabu.KycTierState
import com.blockchain.nabu.service.TierService
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import info.blockchain.balance.CryptoCurrency
import io.reactivex.Observable
import io.reactivex.Single
import org.junit.Before
import org.junit.Test
import piuk.blockchain.android.ui.tiers

class SimpleBuyFlowNavigatorTest {

    private val simpleBuyModel: SimpleBuyModel = mock()
    private val tierService: TierService = mock()
    private val currencyPrefs: CurrencyPrefs = mock()
    private val custodialWalletManager: CustodialWalletManager = mock()
    private lateinit var subject: SimpleBuyFlowNavigator

    @Before
    fun setUp() {
        subject = SimpleBuyFlowNavigator(
            simpleBuyModel, tierService, currencyPrefs, custodialWalletManager
        )
    }

    @Test
    fun `if currency is not  supported  and startedFromDashboard then screen should be currency selector`() {
        mockCurrencyIsSupported(false)
        whenever(simpleBuyModel.state).thenReturn(Observable.just(SimpleBuyState()))
        whenever(custodialWalletManager.getSupportedFiatCurrencies()).thenReturn(Single.just(listOf("GBP,EUR")))

        val test =
            subject.navigateTo(startedFromKycResume = false, startedFromDashboard = true, preselectedCrypto = null)
                .test()
        test.assertValueAt(0, BuyNavigation.CurrencySelection(listOf("GBP,EUR")))
    }

    @Test
    fun `if currency is  supported and state is clear and startedFromDashboard then screen should be enter amount`() {
        mockCurrencyIsSupported(true)
        whenever(simpleBuyModel.state).thenReturn(Observable.just(SimpleBuyState()))

        val test =
            subject.navigateTo(
                startedFromKycResume = false,
                startedFromDashboard = true,
                preselectedCrypto = CryptoCurrency.BTC
            ).test()
        test.assertValueAt(0, BuyNavigation.FlowScreenWithCurrency(FlowScreen.ENTER_AMOUNT, CryptoCurrency.BTC))
    }

    // KYC tests
    @Test
    fun `if  current is screen is KYC and tier 2 approved then screen should be enter amount`() {
        mockCurrencyIsSupported(true)
        whenever(simpleBuyModel.state)
            .thenReturn(Observable.just(SimpleBuyState().copy(currentScreen = FlowScreen.KYC)))
        whenever(tierService.tiers()).thenReturn(Single.just(tiers(KycTierState.Verified, KycTierState.Verified)))

        val test =
            subject.navigateTo(
                startedFromKycResume = false,
                startedFromDashboard = true,
                preselectedCrypto = CryptoCurrency.BTC
            ).test()
        test.assertValueAt(0, BuyNavigation.FlowScreenWithCurrency(FlowScreen.ENTER_AMOUNT, CryptoCurrency.BTC))
    }

    @Test
    fun `if  current is screen is KYC and tier 2 is pending then screen should be kyc verification`() {
        mockCurrencyIsSupported(true)
        whenever(simpleBuyModel.state)
            .thenReturn(Observable.just(SimpleBuyState().copy(currentScreen = FlowScreen.KYC)))
        whenever(tierService.tiers()).thenReturn(Single.just(tiers(KycTierState.Verified, KycTierState.Pending)))

        val test =
            subject.navigateTo(
                startedFromKycResume = false,
                startedFromDashboard = true,
                preselectedCrypto = CryptoCurrency.BTC
            ).test()
        test.assertValueAt(
            0,
            BuyNavigation.FlowScreenWithCurrency(FlowScreen.KYC_VERIFICATION, CryptoCurrency.BTC)
        )
    }

    @Test
    fun `if  current is screen is KYC and tier 2 is none then screen should be kyc`() {
        mockCurrencyIsSupported(true)
        whenever(simpleBuyModel.state)
            .thenReturn(Observable.just(SimpleBuyState().copy(currentScreen = FlowScreen.KYC)))
        whenever(tierService.tiers()).thenReturn(Single.just(tiers(KycTierState.Verified, KycTierState.None)))

        val test =
            subject.navigateTo(
                startedFromKycResume = false,
                startedFromDashboard = true,
                preselectedCrypto = CryptoCurrency.BTC
            ).test()
        test.assertValueAt(
            0,
            BuyNavigation.FlowScreenWithCurrency(FlowScreen.KYC, CryptoCurrency.BTC)
        )
    }

    private fun mockCurrencyIsSupported(supported: Boolean) {
        whenever(custodialWalletManager
            .isCurrencySupportedForSimpleBuy("USD")).thenReturn(Single.just(supported))
        whenever(currencyPrefs.selectedFiatCurrency).thenReturn(("USD"))
    }
}