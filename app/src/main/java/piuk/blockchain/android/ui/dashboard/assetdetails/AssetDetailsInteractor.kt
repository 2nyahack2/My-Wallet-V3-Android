package piuk.blockchain.android.ui.dashboard.assetdetails

import com.blockchain.preferences.DashboardPrefs
import com.blockchain.remoteconfig.FeatureFlag
import info.blockchain.balance.CryptoCurrency
import info.blockchain.balance.ExchangeRate
import info.blockchain.balance.Money
import info.blockchain.wallet.prices.TimeInterval
import io.reactivex.Maybe
import io.reactivex.Single
import io.reactivex.rxkotlin.Singles
import piuk.blockchain.android.coincore.AccountGroup
import piuk.blockchain.android.coincore.AssetAction
import piuk.blockchain.android.coincore.AssetFilter
import piuk.blockchain.android.coincore.AvailableActions
import piuk.blockchain.android.coincore.BlockchainAccount
import piuk.blockchain.android.coincore.Coincore
import piuk.blockchain.android.coincore.CryptoAsset
import piuk.blockchain.androidcore.data.exchangerate.TimeSpan

typealias AssetDisplayMap = Map<AssetFilter, AssetDisplayInfo>

data class AssetDisplayInfo(
    val account: BlockchainAccount,
    val amount: Money,
    val pendingAmount: Money,
    val fiatValue: Money,
    val actions: Set<AssetAction>,
    val interestRate: Double = Double.NaN
)

class AssetDetailsInteractor(
    private val interestFeatureFlag: FeatureFlag,
    private val dashboardPrefs: DashboardPrefs,
    private val coincore: Coincore
) {

    fun loadAssetDetails(asset: CryptoAsset) =
        getAssetDisplayDetails(asset)

    fun loadExchangeRate(asset: CryptoAsset) =
        asset.exchangeRate().map {
            it.price().toStringWithSymbol()
        }

    fun loadHistoricPrices(asset: CryptoAsset, timeSpan: TimeSpan) =
        asset.historicRateSeries(timeSpan, TimeInterval.FIFTEEN_MINUTES)
            .onErrorResumeNext(Single.just(emptyList()))

    fun shouldShowCustody(cryptoCurrency: CryptoCurrency): Single<Boolean> {
        return coincore[cryptoCurrency].accountGroup(AssetFilter.Custodial)
            .flatMapSingle { it.accountBalance }
            .map {
                !dashboardPrefs.isCustodialIntroSeen && !it.isZero
            }
    }

    private sealed class Details {
        object NoDetails : Details()
        class DetailsItem(
            val isEnabled: Boolean,
            val account: BlockchainAccount,
            val balance: Money,
            val pendingBalance: Money,
            val actions: AvailableActions
        ) : Details()
    }

    private fun Maybe<AccountGroup>.mapDetails(): Single<Details> =
        this.flatMap { grp ->
            Singles.zip(
                grp.accountBalance,
                grp.pendingBalance,
                grp.isEnabled,
                grp.actions
            ) { accBalance, pendingBalance, enable, actions ->
                Details.DetailsItem(
                    isEnabled = enable,
                    account = grp,
                    balance = accBalance,
                    pendingBalance = pendingBalance,
                    actions = actions
                ) as Details
            }.toMaybe()
        }.toSingle(Details.NoDetails)

    private fun getAssetDisplayDetails(asset: CryptoAsset): Single<AssetDisplayMap> {
        return Singles.zip(
            asset.exchangeRate(),
            asset.accountGroup(AssetFilter.NonCustodial).mapDetails(),
            asset.accountGroup(AssetFilter.Custodial).mapDetails(),
            asset.accountGroup(AssetFilter.Interest).mapDetails(),
            asset.interestRate(),
            interestFeatureFlag.enabled
        ) { fiatRate, nonCustodial, custodial, interest, interestRate, interestEnabled ->
            makeAssetDisplayMap(
                fiatRate, nonCustodial, custodial, interest, interestRate, interestEnabled
            )
        }
    }

    private fun makeAssetDisplayMap(
        fiatRate: ExchangeRate,
        nonCustodial: Details,
        custodial: Details,
        interest: Details,
        interestRate: Double,
        interestEnabled: Boolean
    ): AssetDisplayMap = mutableMapOf<AssetFilter, AssetDisplayInfo>().apply {
        if (nonCustodial !is Details.NoDetails) {
            addToDisplayMap(this, AssetFilter.NonCustodial, nonCustodial, fiatRate)
        }

        if (custodial !is Details.NoDetails) {
            addToDisplayMap(this, AssetFilter.Custodial, custodial, fiatRate)
        }

        if (interestEnabled && (interest as? Details.DetailsItem)?.isEnabled == true) {
            addToDisplayMap(this, AssetFilter.Interest, interest, fiatRate, interestRate)
        }
    }

    private fun addToDisplayMap(
        map: MutableMap<AssetFilter, AssetDisplayInfo>,
        filter: AssetFilter,
        item: Details,
        fiatRate: ExchangeRate,
        interestRate: Double = Double.NaN
    ) {
        (item as? Details.DetailsItem)?.let {
            val fiat = fiatRate.convert(it.balance)
            map.put(
                filter,
                AssetDisplayInfo(
                    account = it.account,
                    amount = it.balance,
                    fiatValue = fiat,
                    pendingAmount = it.pendingBalance,
                    actions = it.actions,
                    interestRate = interestRate
                )
            )
        }
    }
}