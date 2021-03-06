package piuk.blockchain.android.coincore

import com.blockchain.nabu.models.responses.interest.DisabledReason
import info.blockchain.balance.CryptoCurrency
import info.blockchain.balance.CryptoValue
import info.blockchain.balance.ExchangeRates
import info.blockchain.balance.FiatValue
import info.blockchain.balance.Money
import io.reactivex.Single
import piuk.blockchain.android.coincore.impl.CustodialTradingAccount

interface BlockchainAccount {

    val label: String

    val accountBalance: Single<Money> // Total balance, including uncleared and locked

    val pendingBalance: Single<Money>

    val activity: Single<ActivitySummaryList>

    val actions: Single<AvailableActions>

    val isFunded: Boolean

    val hasTransactions: Boolean

    val isEnabled: Single<Boolean>

    val disabledReason: Single<DisabledReason>

    fun fiatBalance(fiatCurrency: String, exchangeRates: ExchangeRates): Single<Money>
}

interface SingleAccount : BlockchainAccount, TransactionTarget {
    val receiveAddress: Single<ReceiveAddress>
    val isDefault: Boolean

    // Available balance, not including uncleared and locked, that may be used for transactions
    val actionableBalance: Single<Money>

    // Is this account currently able to operate as a transaction source
    val sourceState: Single<TxSourceState>
}

enum class TxSourceState {
    CAN_TRANSACT,
    NO_FUNDS,
    FUNDS_LOCKED,
    NOT_ENOUGH_GAS,
    TRANSACTION_IN_FLIGHT,
    NOT_SUPPORTED
}

interface InterestAccount
interface TradingAccount
interface NonCustodialAccount

typealias SingleAccountList = List<SingleAccount>

interface CryptoAccount : SingleAccount {
    val asset: CryptoCurrency

    val isArchived: Boolean
        get() = false

    override val pendingBalance: Single<Money>
        get() = Single.just(CryptoValue.zero(asset))

    fun requireSecondPassword(): Single<Boolean>

    fun matches(other: CryptoAccount): Boolean
}

interface FiatAccount : SingleAccount {
    val fiatCurrency: String
    override val pendingBalance: Single<Money>
        get() = Single.just(FiatValue.zero(fiatCurrency))
}

interface AccountGroup : BlockchainAccount {
    val accounts: SingleAccountList

    override val isEnabled: Single<Boolean>
        get() = Single.just(true)

    override val disabledReason: Single<DisabledReason>
        get() = Single.just(DisabledReason.NONE)

    fun includes(account: BlockchainAccount): Boolean
}

internal fun BlockchainAccount.isCustodial(): Boolean =
    this is CustodialTradingAccount

object NullCryptoAddress : CryptoAddress {
    override val asset: CryptoCurrency = CryptoCurrency.BTC
    override val label: String = ""
    override val address = ""
}

// Stub invalid accounts; use as an initialisers to avoid nulls.
class NullCryptoAccount(
    override val label: String = ""
) : CryptoAccount {
    override val receiveAddress: Single<ReceiveAddress>
        get() = Single.just(NullAddress)

    override val isDefault: Boolean
        get() = false

    override val asset: CryptoCurrency
        get() = CryptoCurrency.BTC

    override val sourceState: Single<TxSourceState>
        get() = Single.just(TxSourceState.NOT_SUPPORTED)

    override val accountBalance: Single<Money>
        get() = Single.just(CryptoValue.zero(asset))

    override val actionableBalance: Single<Money>
        get() = accountBalance

    override val activity: Single<ActivitySummaryList>
        get() = Single.just(emptyList())

    override val actions: Single<AvailableActions> = Single.just(emptySet())

    override val isFunded: Boolean = false
    override val hasTransactions: Boolean = false

    override fun requireSecondPassword(): Single<Boolean> = Single.just(false)

    override fun matches(other: CryptoAccount): Boolean =
        other is NullCryptoAccount

    override fun fiatBalance(
        fiatCurrency: String,
        exchangeRates: ExchangeRates
    ): Single<Money> =
        Single.just(FiatValue.zero(fiatCurrency))

    override val isEnabled: Single<Boolean>
        get() = Single.just(true)

    override val disabledReason: Single<DisabledReason>
        get() = Single.just(DisabledReason.NONE)
}

object NullFiatAccount : FiatAccount {
    override val fiatCurrency: String = "NULL"

    override val receiveAddress: Single<ReceiveAddress>
        get() = Single.just(NullAddress)

    override val isDefault: Boolean
        get() = false

    override val sourceState: Single<TxSourceState>
        get() = Single.just(TxSourceState.NOT_SUPPORTED)

    override val label: String = ""

    override val accountBalance: Single<Money>
        get() = Single.just(FiatValue.zero(fiatCurrency))

    override val actionableBalance: Single<Money>
        get() = accountBalance

    override val activity: Single<ActivitySummaryList>
        get() = Single.just(emptyList())

    override val actions: Single<AvailableActions> = Single.just(emptySet())
    override val isFunded: Boolean = false
    override val hasTransactions: Boolean = false

    override val isEnabled: Single<Boolean>
        get() = Single.just(true)

    override val disabledReason: Single<DisabledReason>
        get() = Single.just(DisabledReason.NONE)

    override fun fiatBalance(
        fiatCurrency: String,
        exchangeRates: ExchangeRates
    ): Single<Money> =
        Single.just(FiatValue.zero(fiatCurrency))
}