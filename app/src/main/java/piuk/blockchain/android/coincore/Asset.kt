package piuk.blockchain.android.coincore

import info.blockchain.balance.CryptoCurrency
import info.blockchain.balance.ExchangeRate
import info.blockchain.wallet.prices.TimeInterval
import io.reactivex.Completable
import io.reactivex.Maybe
import io.reactivex.Single
import piuk.blockchain.androidcore.data.exchangerate.PriceSeries
import piuk.blockchain.androidcore.data.exchangerate.TimeSpan

enum class AssetFilter {
    All,
    NonCustodial,
    Custodial,
    Interest
}

enum class AssetAction {
    ViewActivity,
    Send,
    Withdraw,
    Receive,
    Swap,
    Sell,
    Summary,
    Deposit
}

typealias AvailableActions = Set<AssetAction>

interface Asset {
    fun init(): Completable
    val isEnabled: Boolean

    fun accountGroup(filter: AssetFilter = AssetFilter.All): Maybe<AccountGroup>

    fun transactionTargets(account: SingleAccount): Single<SingleAccountList>

    fun parseAddress(address: String): Maybe<ReceiveAddress>
    fun isValidAddress(address: String): Boolean = false
}

interface CryptoAsset : Asset {
    val asset: CryptoCurrency

    fun defaultAccount(): Single<SingleAccount>
    fun interestRate(): Single<Double>

    // Fetch exchange rate to user's selected/display fiat
    fun exchangeRate(): Single<ExchangeRate>
    fun historicRate(epochWhen: Long): Single<ExchangeRate>
    fun historicRateSeries(period: TimeSpan, interval: TimeInterval): Single<PriceSeries>
}
