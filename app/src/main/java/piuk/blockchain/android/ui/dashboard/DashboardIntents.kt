package piuk.blockchain.android.ui.dashboard

import info.blockchain.balance.CryptoCurrency
import info.blockchain.balance.CryptoValue
import info.blockchain.balance.ExchangeRate
import info.blockchain.balance.Money
import piuk.blockchain.android.coincore.AssetAction
import piuk.blockchain.android.coincore.FiatAccount
import piuk.blockchain.android.coincore.SingleAccount
import piuk.blockchain.android.ui.base.mvi.MviIntent
import piuk.blockchain.android.ui.dashboard.announcements.AnnouncementCard
import piuk.blockchain.android.ui.dashboard.sheets.BackupDetails
import piuk.blockchain.android.ui.transactionflow.DialogFlow
import piuk.blockchain.androidcore.data.exchangerate.PriceSeries
import java.math.BigInteger

sealed class DashboardIntent : MviIntent<DashboardState>

class FiatBalanceUpdate(
    private val fiatAssetList: List<FiatBalanceInfo>
) : DashboardIntent() {
    override fun reduce(oldState: DashboardState): DashboardState {
        return oldState.copy(
            fiatAssets = FiatAssetState(fiatAssetList)
        )
    }
}

class UpdateDashboardCurrencies(
    private val assetList: List<CryptoCurrency>
) : DashboardIntent() {
    override fun reduce(oldState: DashboardState): DashboardState {
        return oldState.copy(
            assets = AssetMap(
                assetList.associateBy(
                    keySelector = { it },
                    valueTransform = { CryptoAssetState(it) }
                )
            )
        )
    }
}

object GetAvailableAssets : DashboardIntent() {
    override fun reduce(oldState: DashboardState): DashboardState {
        return oldState.copy(
            assets = AssetMap(mapOf())
        )
    }
}

object RefreshAllIntent : DashboardIntent() {
    override fun reduce(oldState: DashboardState): DashboardState {
        return oldState.copy(assets = oldState.assets.reset(), fiatAssets = FiatAssetState())
    }
}

class BalanceUpdate(
    val cryptoCurrency: CryptoCurrency,
    private val newBalance: Money
) : DashboardIntent() {
    override fun reduce(oldState: DashboardState): DashboardState {
        val balance = newBalance as CryptoValue
        require(cryptoCurrency == balance.currency) {
            throw IllegalStateException("CryptoCurrency mismatch")
        }

        val oldAsset = oldState[cryptoCurrency]
        val newAsset = oldAsset.copy(balance = newBalance, hasBalanceError = false)
        val newAssets = oldState.assets.copy(patchAsset = newAsset)

        return oldState.copy(assets = newAssets)
    }
}

class BalanceUpdateError(
    val cryptoCurrency: CryptoCurrency
) : DashboardIntent() {
    override fun reduce(oldState: DashboardState): DashboardState {
        val oldAsset = oldState[cryptoCurrency]
        val newAsset = oldAsset.copy(
            balance = CryptoValue(cryptoCurrency, BigInteger.ZERO),
            hasBalanceError = true
        )
        val newAssets = oldState.assets.copy(patchAsset = newAsset)

        return oldState.copy(assets = newAssets)
    }
}

class CheckForCustodialBalanceIntent(
    val cryptoCurrency: CryptoCurrency
) : DashboardIntent() {
    override fun reduce(oldState: DashboardState): DashboardState {
        val oldAsset = oldState[cryptoCurrency]
        val newAsset = oldAsset.copy(
            hasCustodialBalance = false
        )
        val newAssets = oldState.assets.copy(patchAsset = newAsset)
        return oldState.copy(assets = newAssets)
    }
}

class UpdateHasCustodialBalanceIntent(
    val cryptoCurrency: CryptoCurrency,
    private val hasCustodial: Boolean
) : DashboardIntent() {
    override fun reduce(oldState: DashboardState): DashboardState {
        val oldAsset = oldState[cryptoCurrency]
        val newAsset = oldAsset.copy(
            hasCustodialBalance = hasCustodial
        )
        val newAssets = oldState.assets.copy(patchAsset = newAsset)
        return oldState.copy(assets = newAssets)
    }
}

class RefreshPrices(
    val cryptoCurrency: CryptoCurrency
) : DashboardIntent() {
    override fun reduce(oldState: DashboardState): DashboardState = oldState
}

class PriceUpdate(
    val cryptoCurrency: CryptoCurrency,
    private val latestPrice: ExchangeRate,
    private val oldPrice: ExchangeRate
) : DashboardIntent() {
    override fun reduce(oldState: DashboardState): DashboardState {
        val oldAsset = oldState.assets[cryptoCurrency]
        val newAsset = updateAsset(oldAsset, latestPrice, oldPrice)

        return oldState.copy(assets = oldState.assets.copy(patchAsset = newAsset))
    }

    private fun updateAsset(
        old: CryptoAssetState,
        latestPrice: ExchangeRate,
        oldPrice: ExchangeRate
    ): CryptoAssetState {
        return old.copy(
            price = latestPrice,
            price24h = oldPrice
        )
    }
}

class PriceHistoryUpdate(
    val cryptoCurrency: CryptoCurrency,
    private val historicPrices: PriceSeries
) : DashboardIntent() {
    override fun reduce(oldState: DashboardState): DashboardState {
        val oldAsset = oldState.assets[cryptoCurrency]
        val newAsset = updateAsset(oldAsset, historicPrices)

        return oldState.copy(assets = oldState.assets.copy(patchAsset = newAsset))
    }

    private fun updateAsset(
        old: CryptoAssetState,
        historicPrices: PriceSeries
    ): CryptoAssetState {
        val trend = historicPrices.filter { it.price != null }.map { it.price!!.toFloat() }

        return old.copy(priceTrend = trend)
    }
}

class ShowAnnouncement(private val card: AnnouncementCard) : DashboardIntent() {
    override fun reduce(oldState: DashboardState): DashboardState {
        return oldState.copy(announcement = card)
    }
}

object ClearAnnouncement : DashboardIntent() {
    override fun reduce(oldState: DashboardState): DashboardState {
        return oldState.copy(announcement = null)
    }
}

class ShowFiatAssetDetails(
    private val fiatAccount: FiatAccount
) : DashboardIntent() {
    override fun reduce(oldState: DashboardState): DashboardState =
        oldState.copy(
            showDashboardSheet = DashboardSheet.FIAT_FUNDS_DETAILS,
            selectedFiatAccount = fiatAccount
        )
}

class ShowBankLinkingSheet(
    private val fiatAccount: FiatAccount? = null
) : DashboardIntent() {
    override fun reduce(oldState: DashboardState): DashboardState =
        oldState.copy(
            showDashboardSheet = DashboardSheet.LINK_OR_DEPOSIT,
            selectedFiatAccount = fiatAccount
        )
}

class ShowDashboardSheet(
    private val dashboardSheet: DashboardSheet
) : DashboardIntent() {
    override fun reduce(oldState: DashboardState): DashboardState =
        // Custody sheet isn't displayed via this intent, so filter it out
        oldState.copy(
            showDashboardSheet = dashboardSheet,
            activeFlow = null,
            selectedFiatAccount = null
        )
}

class CancelSimpleBuyOrder(
    val orderId: String
) : DashboardIntent() {
    override fun reduce(oldState: DashboardState): DashboardState = oldState
}

object ClearBottomSheet : DashboardIntent() {
    override fun reduce(oldState: DashboardState): DashboardState =
        oldState.copy(
            showDashboardSheet = null,
            activeFlow = null,
            selectedAsset = null
        )
}

class CheckBackupStatus(
    val account: SingleAccount,
    val action: AssetAction
) : DashboardIntent() {
    override fun reduce(oldState: DashboardState): DashboardState = oldState
}

class ShowBackupSheet(
    private val account: SingleAccount,
    private val action: AssetAction
) : DashboardIntent() {
    override fun reduce(oldState: DashboardState): DashboardState =
        oldState.copy(
            showDashboardSheet = DashboardSheet.BACKUP_BEFORE_SEND,
            backupSheetDetails = BackupDetails(account, action)
        )
}

class UpdateSelectedCryptoAccount(
    private val singleAccount: SingleAccount,
    private val asset: CryptoCurrency
) : DashboardIntent() {
    override fun reduce(oldState: DashboardState): DashboardState =
        oldState.copy(
            selectedCryptoAccount = singleAccount,
            selectedAsset = asset
        )
}

class LaunchSendFlow(
    val fromAccount: SingleAccount,
    val action: AssetAction
) : DashboardIntent() {
    override fun reduce(oldState: DashboardState): DashboardState =
        oldState.copy(
            showDashboardSheet = null,
            activeFlow = null,
            backupSheetDetails = null
        )
}

class LaunchDepositFlow(
    val toAccount: SingleAccount,
    val fromAccount: SingleAccount,
    val action: AssetAction
) : DashboardIntent() {
    override fun reduce(oldState: DashboardState): DashboardState =
        oldState.copy(
            showDashboardSheet = null,
            activeFlow = null,
            backupSheetDetails = null
        )
}

class LaunchAssetDetailsFlow(
    val cryptoCurrency: CryptoCurrency
) : DashboardIntent() {
    override fun reduce(oldState: DashboardState): DashboardState =
        oldState.copy(
            showDashboardSheet = null,
            activeFlow = null,
            backupSheetDetails = null
        )
}

class UpdateLaunchDialogFlow(
    private val flow: DialogFlow
) : DashboardIntent() {
    override fun reduce(oldState: DashboardState): DashboardState =
        oldState.copy(
            showDashboardSheet = null,
            activeFlow = flow,
            backupSheetDetails = null
        )
}
