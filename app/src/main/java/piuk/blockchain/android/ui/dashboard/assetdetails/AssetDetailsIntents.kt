package piuk.blockchain.android.ui.dashboard.assetdetails

import info.blockchain.balance.CryptoCurrency
import info.blockchain.wallet.prices.data.PriceDatum
import piuk.blockchain.android.coincore.AssetAction
import piuk.blockchain.android.coincore.AvailableActions
import piuk.blockchain.android.coincore.BlockchainAccount
import piuk.blockchain.android.coincore.CryptoAsset
import piuk.blockchain.android.ui.base.mvi.MviIntent
import piuk.blockchain.androidcore.data.exchangerate.TimeSpan

sealed class AssetDetailsIntent : MviIntent<AssetDetailsState>

class ShowAssetActionsIntent(
    val account: BlockchainAccount
) : AssetDetailsIntent() {
    override fun reduce(oldState: AssetDetailsState): AssetDetailsState =
        oldState
}

class AccountActionsLoaded(
    private val account: BlockchainAccount,
    private val actions: AvailableActions
) : AssetDetailsIntent() {
    override fun reduce(oldState: AssetDetailsState): AssetDetailsState =
        oldState.copy(
            selectedAccount = account,
            errorState = AssetDetailsError.NONE,
            actions = actions,
            assetDetailsCurrentStep = AssetDetailsStep.ASSET_ACTIONS
        )
}

class LoadAsset(
    val asset: CryptoAsset
) : AssetDetailsIntent() {
    override fun reduce(oldState: AssetDetailsState): AssetDetailsState =
        oldState.copy(
            asset = asset,
            assetDisplayMap = mapOf()
        )
}

object LoadAssetDisplayDetails : AssetDetailsIntent() {
    override fun reduce(oldState: AssetDetailsState): AssetDetailsState = oldState
}

object LoadAssetFiatValue : AssetDetailsIntent() {
    override fun reduce(oldState: AssetDetailsState): AssetDetailsState = oldState
}

object LoadHistoricPrices : AssetDetailsIntent() {
    override fun reduce(oldState: AssetDetailsState): AssetDetailsState = oldState
}

class UpdateTimeSpan(
    val updatedTimeSpan: TimeSpan
) : AssetDetailsIntent() {
    override fun reduce(oldState: AssetDetailsState): AssetDetailsState =
        oldState.copy(timeSpan = updatedTimeSpan)
}

class HandleActionIntent(
    private val action: AssetAction
) : AssetDetailsIntent() {
    override fun reduce(oldState: AssetDetailsState): AssetDetailsState =
        oldState.copy(hostAction = action)
}

object SelectAccount : AssetDetailsIntent() {
    override fun reduce(oldState: AssetDetailsState): AssetDetailsState =
        oldState.copy(
            assetDetailsCurrentStep = AssetDetailsStep.SELECT_ACCOUNT
        )
}

object ChartLoading : AssetDetailsIntent() {
    override fun reduce(oldState: AssetDetailsState): AssetDetailsState =
        oldState.copy(chartLoading = true)
}

class AssetExchangeRateLoaded(
    val exchangeRate: String
) : AssetDetailsIntent() {
    override fun reduce(oldState: AssetDetailsState): AssetDetailsState =
        oldState.copy(assetFiatPrice = exchangeRate)
}

class AssetDisplayDetailsLoaded(
    private val assetDisplayMap: AssetDisplayMap
) : AssetDetailsIntent() {
    override fun reduce(oldState: AssetDetailsState): AssetDetailsState =
        oldState.copy(assetDisplayMap = assetDisplayMap)
}

class ChartDataLoaded(
    private val chartData: List<PriceDatum>
) : AssetDetailsIntent() {
    override fun reduce(oldState: AssetDetailsState): AssetDetailsState =
        oldState.copy(
            chartData = chartData,
            chartLoading = false
        )
}

object ChartDataLoadFailed : AssetDetailsIntent() {
    override fun reduce(oldState: AssetDetailsState): AssetDetailsState =
        oldState.copy(
            chartData = emptyList(),
            chartLoading = false,
            errorState = AssetDetailsError.NO_CHART_DATA
        )
}

object AssetDisplayDetailsFailed : AssetDetailsIntent() {
    override fun reduce(oldState: AssetDetailsState): AssetDetailsState =
        oldState.copy(
            errorState = AssetDetailsError.NO_ASSET_DETAILS
        )
}

object AssetExchangeRateFailed : AssetDetailsIntent() {
    override fun reduce(oldState: AssetDetailsState): AssetDetailsState =
        oldState.copy(
            errorState = AssetDetailsError.NO_EXCHANGE_RATE
        )
}

object ShowCustodyIntroSheetIntent : AssetDetailsIntent() {
    override fun reduce(oldState: AssetDetailsState): AssetDetailsState =
        oldState.copy(assetDetailsCurrentStep = AssetDetailsStep.CUSTODY_INTRO_SHEET)
}

object ShowAssetDetailsIntent : AssetDetailsIntent() {
    override fun reduce(oldState: AssetDetailsState): AssetDetailsState =
        oldState.copy(assetDetailsCurrentStep = AssetDetailsStep.ASSET_DETAILS)
}

object CustodialSheetFinished : AssetDetailsIntent() {
    override fun reduce(oldState: AssetDetailsState): AssetDetailsState =
        oldState.copy(assetDetailsCurrentStep = AssetDetailsStep.ASSET_DETAILS)
}

object ClearSheetDataIntent : AssetDetailsIntent() {
    override fun reduce(oldState: AssetDetailsState): AssetDetailsState = AssetDetailsState()
}

object TransactionInFlight : AssetDetailsIntent() {
    override fun reduce(oldState: AssetDetailsState): AssetDetailsState =
        oldState.copy(errorState = AssetDetailsError.TX_IN_FLIGHT)
}

object ClearActionStates : AssetDetailsIntent() {
    override fun reduce(oldState: AssetDetailsState): AssetDetailsState =
        oldState.copy(
            errorState = AssetDetailsError.NONE,
            hostAction = null
        )
}

object ShowInterestDashboard : AssetDetailsIntent() {
    override fun reduce(oldState: AssetDetailsState): AssetDetailsState =
        oldState.copy(navigateToInterestDashboard = true)
}

class ShowRelevantAssetDetailsSheet(
    val cryptoCurrency: CryptoCurrency
) : AssetDetailsIntent() {
    override fun reduce(oldState: AssetDetailsState): AssetDetailsState = oldState
}

object ReturnToPreviousStep : AssetDetailsIntent() {
    override fun reduce(oldState: AssetDetailsState): AssetDetailsState {
        val steps = AssetDetailsStep.values()
        val currentStep = oldState.assetDetailsCurrentStep.ordinal
        if (currentStep == 0) {
            throw IllegalStateException("Cannot go back")
        }
        val previousStep = steps[currentStep - 1]

        return oldState.copy(
            hostAction = null,
            assetDetailsCurrentStep = previousStep
        )
    }
}
