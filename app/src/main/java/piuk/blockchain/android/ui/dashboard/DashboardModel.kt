package piuk.blockchain.android.ui.dashboard

import androidx.annotation.VisibleForTesting
import info.blockchain.balance.CryptoCurrency
import info.blockchain.balance.CryptoValue
import info.blockchain.balance.ExchangeRate
import info.blockchain.balance.FiatValue
import info.blockchain.balance.Money
import info.blockchain.balance.percentageDelta
import info.blockchain.balance.total
import io.reactivex.Scheduler
import io.reactivex.disposables.Disposable
import io.reactivex.rxkotlin.subscribeBy
import org.koin.core.KoinComponent
import piuk.blockchain.android.coincore.AssetAction
import piuk.blockchain.android.coincore.AssetFilter
import piuk.blockchain.android.coincore.FiatAccount
import piuk.blockchain.android.coincore.SingleAccount
import piuk.blockchain.android.ui.base.mvi.MviModel
import piuk.blockchain.android.ui.base.mvi.MviState
import piuk.blockchain.android.ui.dashboard.announcements.AnnouncementCard
import piuk.blockchain.android.ui.dashboard.sheets.BackupDetails
import piuk.blockchain.android.ui.transactionflow.DialogFlow
import piuk.blockchain.androidcore.utils.helperfunctions.unsafeLazy
import timber.log.Timber

class AssetMap(private val map: Map<CryptoCurrency, CryptoAssetState>) :
    Map<CryptoCurrency, CryptoAssetState> by map {
    override operator fun get(key: CryptoCurrency): CryptoAssetState {
        return map.getOrElse(key) {
            throw IllegalArgumentException("$key is not a known CryptoCurrency")
        }
    }

    // TODO: This is horrendously inefficient. Fix it!
    fun copy(): AssetMap {
        val assets = toMutableMap()
        return AssetMap(assets)
    }

    fun copy(patchBalance: Money): AssetMap {
        val assets = toMutableMap()
        // CURRENCY HERE
        val balance = patchBalance as CryptoValue
        val value = get(balance.currency).copy(balance = patchBalance)
        assets[balance.currency] = value
        return AssetMap(assets)
    }

    fun copy(patchAsset: CryptoAssetState): AssetMap {
        val assets = toMutableMap()
        assets[patchAsset.currency] = patchAsset
        return AssetMap(assets)
    }

    fun reset(): AssetMap {
        val assets = toMutableMap()
        map.values.forEach { assets[it.currency] = it.reset() }
        return AssetMap(assets)
    }
}

@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
fun mapOfAssets(vararg pairs: Pair<CryptoCurrency, CryptoAssetState>) = AssetMap(mapOf(*pairs))

interface DashboardItem

interface BalanceState : DashboardItem {
    val isLoading: Boolean
    val fiatBalance: Money?
    val delta: Pair<Money, Double>?
    operator fun get(currency: CryptoCurrency): CryptoAssetState
    fun getFundsFiat(fiat: String): Money
}

data class FiatBalanceInfo(
    val balance: Money,
    val userFiat: Money,
    val account: FiatAccount
)

data class FiatAssetState(
    val fiatAccounts: List<FiatBalanceInfo> = emptyList()
) : DashboardItem {

    val totalBalance: Money? =
        if (fiatAccounts.isEmpty()) {
            null
        } else {
            fiatAccounts.map {
                it.userFiat
            }.total()
        }
}

enum class DashboardSheet {
    STX_AIRDROP_COMPLETE,
    BACKUP_BEFORE_SEND,
    SIMPLE_BUY_CANCEL_ORDER,
    FIAT_FUNDS_DETAILS,
    LINK_OR_DEPOSIT,
    FIAT_FUNDS_NO_KYC,
    INTEREST_SUMMARY
}

data class DashboardState(
    val assets: AssetMap = AssetMap(emptyMap()),
    val showDashboardSheet: DashboardSheet? = null,
    val activeFlow: DialogFlow? = null,
    val announcement: AnnouncementCard? = null,
    val fiatAssets: FiatAssetState? = null,
    val selectedFiatAccount: FiatAccount? = null,
    val selectedCryptoAccount: SingleAccount? = null,
    val selectedAsset: CryptoCurrency? = null,
    val backupSheetDetails: BackupDetails? = null
) : MviState, BalanceState, KoinComponent {

    // If ALL the assets are refreshing, then report true. Else false
    override val isLoading: Boolean by unsafeLazy {
        assets.values.all { it.isLoading }
    }

    override val fiatBalance: Money? by unsafeLazy {
        val cryptoAssetBalance = cryptoAssetFiatBalances()
        val fiatAssetBalance = fiatAssets?.totalBalance

        if (cryptoAssetBalance != null) {
            if (fiatAssetBalance != null) {
                cryptoAssetBalance + fiatAssetBalance
            } else {
                cryptoAssetBalance
            }
        } else {
            fiatAssetBalance
        }
    }

    private fun cryptoAssetFiatBalances() = assets.values
        .filter { !it.isLoading && it.fiatBalance != null }
        .map { it.fiatBalance!! }
        .ifEmpty { null }?.total()

    private val fiatBalance24h: Money? by unsafeLazy {
        assets.values
            .filter { !it.isLoading && it.fiatBalance24h != null }
            .map { it.fiatBalance24h!! }
            .ifEmpty { null }?.total()
    }

    override val delta: Pair<Money, Double>? by unsafeLazy {
        val current = fiatBalance
        val old = fiatBalance24h

        if (current != null && old != null) {
            Pair(current - old, current.percentageDelta(old))
        } else {
            null
        }
    }

    override operator fun get(currency: CryptoCurrency): CryptoAssetState =
        assets[currency]

    override fun getFundsFiat(fiat: String): Money =
        fiatAssets?.totalBalance ?: FiatValue.zero(fiat)

    val assetMapKeys = assets.keys

    val erc20Assets = assetMapKeys.filter { it.hasFeature(CryptoCurrency.IS_ERC20) }
}

data class CryptoAssetState(
    val currency: CryptoCurrency,
    val balance: Money? = null,
    val price: ExchangeRate? = null,
    val price24h: ExchangeRate? = null,
    val priceTrend: List<Float> = emptyList(),
    val hasBalanceError: Boolean = false,
    val hasCustodialBalance: Boolean = false
) : DashboardItem {
    val fiatBalance: Money? by unsafeLazy {
        price?.let { p -> balance?.let { p.convert(it) } }
    }

    val fiatBalance24h: Money? by unsafeLazy {
        price24h?.let { p -> balance?.let { p.convert(it) } }
    }

    val priceDelta: Double by unsafeLazy { price.percentageDelta(price24h) }

    val isLoading: Boolean by unsafeLazy {
        balance == null || price == null || price24h == null
    }

    fun reset(): CryptoAssetState = CryptoAssetState(currency)
}

class DashboardModel(
    initialState: DashboardState,
    mainScheduler: Scheduler,
    private val interactor: DashboardInteractor
) : MviModel<DashboardState, DashboardIntent>(
    initialState,
    mainScheduler
) {
    override fun performAction(
        previousState: DashboardState,
        intent: DashboardIntent
    ): Disposable? {
        Timber.d("***> performAction: ${intent.javaClass.simpleName}")

        return when (intent) {
            is GetAvailableAssets -> {
                interactor.getAvailableAssets(this)
            }
            is RefreshAllIntent -> {
                interactor.refreshBalances(this, AssetFilter.All, previousState)
            }
            is BalanceUpdate -> {
                process(CheckForCustodialBalanceIntent(intent.cryptoCurrency))
                null
            }
            is CheckForCustodialBalanceIntent -> interactor.checkForCustodialBalance(
                this,
                intent.cryptoCurrency
            )
            is UpdateHasCustodialBalanceIntent -> {
                process(RefreshPrices(intent.cryptoCurrency))
                null
            }
            is RefreshPrices -> interactor.refreshPrices(this, intent.cryptoCurrency)
            is PriceUpdate -> interactor.refreshPriceHistory(this, intent.cryptoCurrency)
            is CheckBackupStatus -> checkBackupStatus(intent.account, intent.action)
            is CancelSimpleBuyOrder -> interactor.cancelSimpleBuyOrder(intent.orderId)
            is LaunchAssetDetailsFlow -> interactor.getAssetDetailsFlow(this, intent.cryptoCurrency)
            is LaunchDepositFlow -> interactor.getDepositFlow(this, intent.fromAccount, intent.toAccount, intent.action)
            is LaunchSendFlow -> interactor.getSendFlow(this, intent.fromAccount, intent.action)
            is FiatBalanceUpdate,
            is BalanceUpdateError,
            is PriceHistoryUpdate,
            is ClearAnnouncement,
            is ShowAnnouncement,
            is ShowFiatAssetDetails,
            is ShowBankLinkingSheet,
            is ShowDashboardSheet,
            is UpdateLaunchDialogFlow,
            is ClearBottomSheet,
            is UpdateSelectedCryptoAccount,
            is ShowBackupSheet,
            is UpdateDashboardCurrencies -> null
        }
    }

    private fun checkBackupStatus(account: SingleAccount, action: AssetAction): Disposable =
        interactor.hasUserBackedUp()
            .subscribeBy(
                onSuccess = { isBackedUp ->
                    if (isBackedUp) {
                        process(LaunchSendFlow(account, action))
                    } else {
                        process(ShowBackupSheet(account, action))
                    }
                }, onError = { Timber.e(it) }
            )

    override fun onScanLoopError(t: Throwable) {
        Timber.e("***> Scan loop failed: $t")
    }

    override fun distinctIntentFilter(
        previousIntent: DashboardIntent,
        nextIntent: DashboardIntent
    ): Boolean {
        return when (previousIntent) {
            is UpdateLaunchDialogFlow -> {
                if (nextIntent is ClearBottomSheet) {
                    true
                } else {
                    super.distinctIntentFilter(previousIntent, nextIntent)
                }
            }
            else -> super.distinctIntentFilter(previousIntent, nextIntent)
        }
    }
}
