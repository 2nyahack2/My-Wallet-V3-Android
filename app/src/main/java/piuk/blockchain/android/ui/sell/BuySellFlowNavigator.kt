package piuk.blockchain.android.ui.sell

import com.blockchain.preferences.CurrencyPrefs
import com.blockchain.nabu.datamanagers.CustodialWalletManager
import com.blockchain.nabu.datamanagers.EligibilityProvider
import com.blockchain.nabu.datamanagers.OrderState
import com.blockchain.nabu.models.responses.nabu.KycTierLevel
import com.blockchain.nabu.service.TierService
import io.reactivex.Completable
import io.reactivex.Single
import io.reactivex.rxkotlin.zipWith
import io.reactivex.rxkotlin.Singles
import piuk.blockchain.android.simplebuy.SimpleBuyModel
import piuk.blockchain.android.simplebuy.SimpleBuyState
import piuk.blockchain.androidcore.utils.extensions.thenSingle

class BuySellFlowNavigator(
    private val simpleBuyModel: SimpleBuyModel,
    private val currencyPrefs: CurrencyPrefs,
    private val custodialWalletManager: CustodialWalletManager,
    private val eligibilityProvider: EligibilityProvider,
    private val tierService: TierService
) {
    fun navigateTo(): Single<BuySellIntroAction> = simpleBuyModel.state.firstOrError().flatMap { state ->
        val cancel = if (state.orderState == OrderState.PENDING_CONFIRMATION)
            custodialWalletManager.deleteBuyOrder(state.id
                ?: throw IllegalStateException("Pending order should always have an id")).onErrorComplete()
        else Completable.complete()
        val isGoldButNotEligible = tierService.tiers()
            .zipWith(
                eligibilityProvider.isEligibleForSimpleBuy(forceRefresh = true)
            ) { tier, eligible ->
                tier.isApprovedFor(KycTierLevel.GOLD) && !eligible
            }

        cancel.thenSingle {
            Singles.zip(custodialWalletManager.isCurrencySupportedForSimpleBuy(currencyPrefs.selectedFiatCurrency),
                custodialWalletManager.getSupportedFiatCurrencies(),
                isGoldButNotEligible
            ) { currencySupported, supportedFiats, isGoldButNotEligible ->
                if (currencySupported)
                    BuySellIntroAction.DisplayBuySellIntro(
                        isGoldButNotEligible = isGoldButNotEligible,
                        hasPendingBuy = state.hasPendingBuyThatCannotBeCancelled()
                    )
                else
                    BuySellIntroAction.NavigateToCurrencySelection(supportedFiats)
            }
        }
    }
}

private fun SimpleBuyState.hasPendingBuyThatCannotBeCancelled(): Boolean =
    orderState > OrderState.PENDING_CONFIRMATION && orderState < OrderState.FINISHED

sealed class BuySellIntroAction {

    data class NavigateToCurrencySelection(val supportedCurrencies: List<String>) : BuySellIntroAction()

    data class DisplayBuySellIntro(val isGoldButNotEligible: Boolean, val hasPendingBuy: Boolean) : BuySellIntroAction()
}
