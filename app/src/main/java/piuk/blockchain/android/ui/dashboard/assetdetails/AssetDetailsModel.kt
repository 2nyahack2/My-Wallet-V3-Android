package piuk.blockchain.android.ui.dashboard.assetdetails

import info.blockchain.balance.Money
import info.blockchain.wallet.prices.data.PriceDatum
import io.reactivex.Scheduler
import io.reactivex.disposables.Disposable
import io.reactivex.rxkotlin.subscribeBy
import piuk.blockchain.android.coincore.AssetAction
import piuk.blockchain.android.coincore.AvailableActions
import piuk.blockchain.android.coincore.BlockchainAccount
import piuk.blockchain.android.coincore.CryptoAsset
import piuk.blockchain.android.ui.base.mvi.MviModel
import piuk.blockchain.android.ui.base.mvi.MviState
import piuk.blockchain.androidcore.data.exchangerate.TimeSpan
import timber.log.Timber

data class AssetDetailsState(
    val asset: CryptoAsset? = null,
    val selectedAccount: BlockchainAccount? = null,
    val actions: AvailableActions = emptySet(),
    val assetDetailsCurrentStep: AssetDetailsStep = AssetDetailsStep.ZERO,
    val assetDisplayMap: AssetDisplayMap? = null,
    val assetFiatPrice: String = "",
    val timeSpan: TimeSpan = TimeSpan.DAY,
    val chartLoading: Boolean = false,
    val chartData: List<PriceDatum> = emptyList(),
    val errorState: AssetDetailsError = AssetDetailsError.NONE,
    val hostAction: AssetAction? = null,
    val selectedAccountCryptoBalance: Money? = null,
    val selectedAccountFiatBalance: Money? = null,
    val navigateToInterestDashboard: Boolean = false
) : MviState

enum class AssetDetailsError {
    NONE,
    NO_CHART_DATA,
    NO_ASSET_DETAILS,
    NO_EXCHANGE_RATE,
    TX_IN_FLIGHT
}

class AssetDetailsModel(
    initialState: AssetDetailsState,
    mainScheduler: Scheduler,
    private val interactor: AssetDetailsInteractor
) : MviModel<AssetDetailsState, AssetDetailsIntent>(initialState, mainScheduler) {
    override fun performAction(
        previousState: AssetDetailsState,
        intent: AssetDetailsIntent
    ): Disposable? {
        Timber.d("***> performAction: ${intent.javaClass.simpleName}")

        return when (intent) {
            is ShowRelevantAssetDetailsSheet -> interactor.shouldShowCustody(intent.cryptoCurrency)
                .subscribeBy(
                    onSuccess = {
                        if (it) {
                            process(ShowCustodyIntroSheetIntent)
                        } else {
                            process(ShowAssetDetailsIntent)
                        }
                    },
                    onError = {
                        // fail silently, try to show AssetSheet instead
                        process(ShowAssetDetailsIntent)
                    }
                )
            is LoadAssetDisplayDetails -> interactor.loadAssetDetails(previousState.asset!!)
                .subscribeBy(
                    onSuccess = {
                        process(AssetDisplayDetailsLoaded(it))
                    },
                    onError = {
                        process(AssetDisplayDetailsFailed)
                    })
            is LoadAssetFiatValue -> interactor.loadExchangeRate(previousState.asset!!)
                .subscribeBy(
                    onSuccess = {
                        process(AssetExchangeRateLoaded(it))
                    }, onError = {
                        process(AssetExchangeRateFailed)
                    })
            is ShowAssetActionsIntent -> accountActions(intent.account)
            is LoadHistoricPrices -> updateChartData(previousState.asset!!, previousState.timeSpan)
            is UpdateTimeSpan -> updateChartData(previousState.asset!!, intent.updatedTimeSpan)
            is HandleActionIntent,
            is LoadAsset,
            is ChartLoading,
            is ChartDataLoaded,
            is ChartDataLoadFailed,
            is AssetDisplayDetailsLoaded,
            is AssetDisplayDetailsFailed,
            is AssetExchangeRateLoaded,
            is AssetExchangeRateFailed,
            is ShowAssetDetailsIntent,
            is ShowCustodyIntroSheetIntent,
            is SelectAccount,
            is ReturnToPreviousStep,
            is ClearSheetDataIntent,
            is CustodialSheetFinished,
            is TransactionInFlight,
            is ShowInterestDashboard,
            is ClearActionStates -> null
            is AccountActionsLoaded -> null
        }
    }

    private fun updateChartData(asset: CryptoAsset, timeSpan: TimeSpan) =
        interactor.loadHistoricPrices(asset, timeSpan).doOnSubscribe {
            process(ChartLoading)
        }.subscribeBy(
            onSuccess = {
                process(ChartDataLoaded(it))
            },
            onError = {
                process(ChartDataLoadFailed)
            }
        )

    private fun accountActions(account: BlockchainAccount): Disposable =
        account.actions.subscribeBy(
            onSuccess = {
                process(AccountActionsLoaded(account, it))
            },
            onError = { Timber.e("***> Error Loading account actions: $it") }
        )

    override fun onScanLoopError(t: Throwable) {
        Timber.e("***> Scan loop failed: $t")
    }
}
