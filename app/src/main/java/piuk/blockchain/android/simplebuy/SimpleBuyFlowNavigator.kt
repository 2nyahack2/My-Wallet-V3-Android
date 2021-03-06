package piuk.blockchain.android.simplebuy

import com.blockchain.preferences.CurrencyPrefs
import com.blockchain.nabu.datamanagers.CustodialWalletManager
import com.blockchain.nabu.datamanagers.OrderState
import com.blockchain.nabu.models.responses.nabu.KycTierLevel
import com.blockchain.nabu.service.TierService
import info.blockchain.balance.CryptoCurrency
import io.reactivex.Observable
import io.reactivex.Single

class SimpleBuyFlowNavigator(
    private val simpleBuyModel: SimpleBuyModel,
    private val tierService: TierService,
    private val currencyPrefs: CurrencyPrefs,
    private val custodialWalletManager: CustodialWalletManager
) {

    private fun stateCheck(
        startedFromKycResume: Boolean,
        startedFromNavigationBar: Boolean,
        preselectedCrypto: CryptoCurrency?
    ): Single<BuyNavigation> =
        simpleBuyModel.state.flatMap {
            val cryptoCurrency = preselectedCrypto ?: it.selectedCryptoCurrency
            ?: throw IllegalStateException("CryptoCurrency is not available")
            if (
                startedFromKycResume ||
                it.currentScreen == FlowScreen.KYC ||
                it.currentScreen == FlowScreen.KYC_VERIFICATION
            ) {
                tierService.tiers().toObservable().map { tier ->
                    when {
                        tier.isApprovedFor(KycTierLevel.GOLD) ->
                            BuyNavigation.FlowScreenWithCurrency(FlowScreen.ENTER_AMOUNT, cryptoCurrency)
                        tier.isPendingOrUnderReviewFor(KycTierLevel.GOLD) ||
                                tier.isRejectedFor(KycTierLevel.GOLD) ->
                            BuyNavigation.FlowScreenWithCurrency(FlowScreen.KYC_VERIFICATION, cryptoCurrency)
                        else -> BuyNavigation.FlowScreenWithCurrency(FlowScreen.KYC, cryptoCurrency)
                    }
                }
            } else {
                when {
                    it.orderState == OrderState.AWAITING_FUNDS -> {
                        Observable.just(BuyNavigation.PendingOrderScreen)
                    }
                    startedFromNavigationBar -> {
                        Observable.just(BuyNavigation.FlowScreenWithCurrency(FlowScreen.ENTER_AMOUNT, cryptoCurrency))
                    }
                    else -> {
                        Observable.just(BuyNavigation.FlowScreenWithCurrency(it.currentScreen, cryptoCurrency))
                    }
                }
            }
        }.firstOrError()

    fun navigateTo(
        startedFromKycResume: Boolean,
        startedFromDashboard: Boolean,
        preselectedCrypto: CryptoCurrency?
    ): Single<BuyNavigation> {

        val currencyCheck = (currencyPrefs.selectedFiatCurrency.takeIf { it.isNotEmpty() }?.let {
            custodialWalletManager.isCurrencySupportedForSimpleBuy(it)
        } ?: Single.just(false))

        return currencyCheck.flatMap { currencySupported ->
            if (!currencySupported) {
                custodialWalletManager.getSupportedFiatCurrencies().map {
                    BuyNavigation.CurrencySelection(it)
                }
            } else {
                stateCheck(startedFromKycResume, startedFromDashboard, preselectedCrypto)
            }
        }
    }
}

sealed class BuyNavigation {
    data class CurrencySelection(val currencies: List<String>) : BuyNavigation()
    data class FlowScreenWithCurrency(val flowScreen: FlowScreen, val cryptoCurrency: CryptoCurrency) : BuyNavigation()
    object PendingOrderScreen : BuyNavigation()
}