package piuk.blockchain.android.coincore.impl

import com.blockchain.swap.nabu.datamanagers.BuySellOrder
import com.blockchain.swap.nabu.datamanagers.CurrencyPair
import com.blockchain.swap.nabu.datamanagers.CustodialOrderState
import com.blockchain.swap.nabu.datamanagers.CustodialWalletManager
import com.blockchain.swap.nabu.datamanagers.EligibilityProvider
import com.blockchain.swap.nabu.datamanagers.OrderState
import com.blockchain.swap.nabu.datamanagers.Product
import com.blockchain.swap.nabu.datamanagers.TransferDirection
import com.blockchain.swap.nabu.datamanagers.custodialwalletimpl.OrderType
import info.blockchain.balance.CryptoCurrency
import info.blockchain.balance.CryptoValue
import info.blockchain.balance.FiatValue
import info.blockchain.balance.Money
import io.reactivex.Completable
import io.reactivex.Single
import io.reactivex.rxkotlin.Singles
import io.reactivex.rxkotlin.zipWith
import piuk.blockchain.android.coincore.ActivitySummaryItem
import piuk.blockchain.android.coincore.ActivitySummaryList
import piuk.blockchain.android.coincore.AssetAction
import piuk.blockchain.android.coincore.AvailableActions
import piuk.blockchain.android.coincore.CustodialTradingActivitySummaryItem
import piuk.blockchain.android.coincore.ReceiveAddress
import piuk.blockchain.android.coincore.TradeActivitySummaryItem
import piuk.blockchain.android.coincore.TradingAccount
import piuk.blockchain.android.coincore.TxResult
import piuk.blockchain.android.coincore.TxSourceState
import piuk.blockchain.androidcore.data.api.EnvironmentConfig
import piuk.blockchain.androidcore.data.exchangerate.ExchangeRateDataManager
import piuk.blockchain.androidcore.utils.extensions.mapList
import timber.log.Timber
import java.lang.IllegalStateException
import java.util.concurrent.atomic.AtomicBoolean

open class CustodialTradingAccount(
    override val asset: CryptoCurrency,
    override val label: String,
    override val exchangeRates: ExchangeRateDataManager,
    val custodialWalletManager: CustodialWalletManager,
    val isNoteSupported: Boolean = false,
    private val environmentConfig: EnvironmentConfig,
    private val eligibilityProvider: EligibilityProvider
) : CryptoAccountBase(), TradingAccount {

    private val nabuAccountExists = AtomicBoolean(false)
    private val isEligibleForSimpleBuy = AtomicBoolean(false)
    private val hasFunds = AtomicBoolean(false)

    override val receiveAddress: Single<ReceiveAddress>
        get() = custodialWalletManager.getCustodialAccountAddress(asset).map {
            makeExternalAssetAddress(
                asset = asset,
                address = it,
                label = label,
                environmentConfig = environmentConfig,
                postTransactions = onTxCompleted
            )
        }

    override val directions: Set<TransferDirection> = setOf(TransferDirection.INTERNAL)

    override val onTxCompleted: (TxResult) -> Completable
        get() = { txResult ->
            receiveAddress.flatMapCompletable {
                require(txResult.amount is CryptoValue)
                require(txResult is TxResult.HashedTxResult)
                custodialWalletManager.createPendingDeposit(
                    crypto = txResult.amount.currency,
                    address = it.address,
                    hash = txResult.txHash,
                    amount = txResult.amount,
                    product = Product.SIMPLEBUY
                )
            }
        }

    override fun requireSecondPassword(): Single<Boolean> =
        Single.just(false)

    override val accountBalance: Single<Money>
        get() = custodialWalletManager.getTotalBalanceForAsset(asset)
            .doOnComplete { nabuAccountExists.set(false) }
            .doOnSuccess { nabuAccountExists.set(true) }
            .toSingle(CryptoValue.zero(asset))
            .zipWith(eligibilityProvider.isEligibleForSimpleBuy().doOnSuccess {
                isEligibleForSimpleBuy.set(it)
            }).map { (balance, _) ->
                balance
            }
            .onErrorReturn {
                Timber.d("Unable to get custodial trading total balance: $it")
                CryptoValue.zero(asset)
            }
            .doOnSuccess { hasFunds.set(it.isPositive) }
            .map { it as Money }

    override val actionableBalance: Single<Money>
        get() = custodialWalletManager.getActionableBalanceForAsset(asset)
            .doOnComplete { nabuAccountExists.set(false) }
            .doOnSuccess { nabuAccountExists.set(true) }
            .toSingle(CryptoValue.zero(asset))
            .onErrorReturn {
                Timber.d("Unable to get custodial trading actionable balance: $it")
                CryptoValue.zero(asset)
            }
            .doOnSuccess { hasFunds.set(it.isPositive) }
            .map { it as Money }

    override val pendingBalance: Single<Money>
        get() = custodialWalletManager.getPendingBalanceForAsset(asset)
            .toSingle(CryptoValue.zero(asset))
            .map { it as Money }

    override val activity: Single<ActivitySummaryList>
        get() = custodialWalletManager.getAllOrdersFor(asset)
            .mapList { orderToSummary(it) }
            .filterActivityStates()
            .flatMap {
                appendCustodialActivity(custodialWalletManager, asset, it)
            }
            .doOnSuccess { setHasTransactions(it.isNotEmpty()) }
            .onErrorReturn { emptyList() }

    val isConfigured: Boolean
        get() = nabuAccountExists.get()

    override val isFunded: Boolean
        get() = hasFunds.get()

    override val isDefault: Boolean =
        false // Default is, presently, only ever a non-custodial account.

    override val sourceState: Single<TxSourceState>
        get() = Singles.zip(
            accountBalance,
            actionableBalance
        ) { total, available ->
            when {
                total <= CryptoValue.zero(asset) -> TxSourceState.NO_FUNDS
                available <= CryptoValue.zero(asset) -> TxSourceState.FUNDS_LOCKED
                else -> TxSourceState.CAN_TRANSACT
            }
        }

    override val actions: AvailableActions
        get() =
            mutableSetOf(
                AssetAction.ViewActivity
            ).apply {
                if (isFunded && !isArchived) {
                    add(AssetAction.Send)
                    if (isEligibleForSimpleBuy.get()) {
                        add(AssetAction.Sell)
                        add(AssetAction.Swap)
                    }
                }
            }.toSet()

    private fun orderToSummary(order: BuySellOrder): ActivitySummaryItem =
        if (order.type == OrderType.BUY) {
            CustodialTradingActivitySummaryItem(
                exchangeRates = exchangeRates,
                cryptoCurrency = order.crypto.currency,
                value = order.crypto,
                fundedFiat = order.fiat,
                txId = order.id,
                timeStampMs = order.created.time,
                status = order.state,
                fee = order.fee ?: FiatValue.zero(order.fiat.currencyCode),
                account = this,
                type = order.type,
                paymentMethodId = order.paymentMethodId,
                paymentMethodType = order.paymentMethodType
            )
        } else {
            TradeActivitySummaryItem(
                exchangeRates = exchangeRates,
                txId = order.id,
                timeStampMs = order.created.time,
                sendingValue = order.crypto,
                sendingAccount = this,
                sendingAddress = null,
                receivingAddress = null,
                state = order.state.toCustodialOrderState(),
                direction = TransferDirection.INTERNAL,
                receivingValue = order.orderValue ?: throw IllegalStateException("Order missing receivingValue"),
                depositNetworkFee = Single.just(CryptoValue.zero(order.crypto.currency)),
                withdrawalNetworkFee = order.fee ?: FiatValue.zero(order.fiat.currencyCode),
                currencyPair = CurrencyPair.CryptoToFiatCurrencyPair(order.crypto.currency, order.fiat.currencyCode),
                fiatValue = order.fiat,
                fiatCurrency = order.fiat.currencyCode
            )
        }

    // Stop gap filter, until we finalise which item we wish to display to the user.
    // TODO: This can be done via the API when it's settled
    private fun Single<ActivitySummaryList>.filterActivityStates(): Single<ActivitySummaryList> {
        return flattenAsObservable { list ->
            list.filter {
                (it is CustodialTradingActivitySummaryItem && displayedStates.contains(it.status)) or
                        (it is TradeActivitySummaryItem && displayedStates.contains(it.state))
            }
        }.toList()
    }

    // No need to reconcile sends and swaps in custodial accounts, the BE deals with this
    // Return a list containing both supplied list
    override fun reconcileSwaps(
        tradeItems: List<TradeActivitySummaryItem>,
        activity: List<ActivitySummaryItem>
    ): List<ActivitySummaryItem> = activity + tradeItems

    companion object {
        private val displayedStates = setOf(
            OrderState.FINISHED,
            OrderState.AWAITING_FUNDS,
            OrderState.PENDING_EXECUTION,
            CustodialOrderState.FINISHED,
            CustodialOrderState.PENDING_DEPOSIT,
            CustodialOrderState.PENDING_EXECUTION
        )
    }
}

private fun OrderState.toCustodialOrderState(): CustodialOrderState =
    when (this) {
        OrderState.FINISHED -> CustodialOrderState.FINISHED
        OrderState.CANCELED -> CustodialOrderState.CANCELED
        OrderState.FAILED -> CustodialOrderState.FAILED
        OrderState.PENDING_CONFIRMATION -> CustodialOrderState.PENDING_CONFIRMATION
        OrderState.AWAITING_FUNDS -> CustodialOrderState.PENDING_DEPOSIT
        OrderState.PENDING_EXECUTION -> CustodialOrderState.PENDING_EXECUTION
        else -> CustodialOrderState.UNKNOWN
    }