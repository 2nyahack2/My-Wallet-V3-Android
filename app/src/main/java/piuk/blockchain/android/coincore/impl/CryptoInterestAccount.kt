package piuk.blockchain.android.coincore.impl

import com.blockchain.nabu.datamanagers.CustodialWalletManager
import com.blockchain.nabu.datamanagers.InterestActivityItem
import com.blockchain.nabu.datamanagers.InterestState
import com.blockchain.nabu.datamanagers.Product
import com.blockchain.nabu.models.responses.interest.DisabledReason
import com.blockchain.nabu.datamanagers.TransferDirection
import info.blockchain.balance.CryptoCurrency
import info.blockchain.balance.CryptoValue
import info.blockchain.balance.Money
import io.reactivex.Completable
import io.reactivex.Single
import piuk.blockchain.android.coincore.ActivitySummaryItem
import piuk.blockchain.android.coincore.ActivitySummaryList
import piuk.blockchain.android.coincore.AssetAction
import piuk.blockchain.android.coincore.AvailableActions
import piuk.blockchain.android.coincore.CryptoAccount
import piuk.blockchain.android.coincore.CustodialInterestActivitySummaryItem
import piuk.blockchain.android.coincore.InterestAccount
import piuk.blockchain.android.coincore.ReceiveAddress
import piuk.blockchain.android.coincore.TradeActivitySummaryItem
import piuk.blockchain.android.coincore.TxResult
import piuk.blockchain.android.coincore.TxSourceState
import piuk.blockchain.androidcore.data.api.EnvironmentConfig
import piuk.blockchain.androidcore.data.exchangerate.ExchangeRateDataManager
import piuk.blockchain.androidcore.utils.extensions.mapList
import java.util.concurrent.atomic.AtomicBoolean

internal class CryptoInterestAccount(
    override val asset: CryptoCurrency,
    override val label: String,
    val custodialWalletManager: CustodialWalletManager,
    override val exchangeRates: ExchangeRateDataManager,
    private val environmentConfig: EnvironmentConfig
) : CryptoAccountBase(), InterestAccount {

    private val hasFunds = AtomicBoolean(false)

    override val receiveAddress: Single<ReceiveAddress>
        get() = custodialWalletManager.getInterestAccountAddress(asset).map {
            makeExternalAssetAddress(
                asset = asset,
                address = it,
                label = label,
                environmentConfig = environmentConfig,
                postTransactions = onTxCompleted
            )
        }

    override val onTxCompleted: (TxResult) -> Completable
        get() = { txResult ->
            require(txResult.amount is CryptoValue)
            require(txResult is TxResult.HashedTxResult)
            receiveAddress.flatMapCompletable { receiveAddress ->
                custodialWalletManager.createPendingDeposit(
                    crypto = txResult.amount.currency,
                    address = receiveAddress.address,
                    hash = txResult.txHash,
                    amount = txResult.amount,
                    product = Product.SAVINGS
                )
            }
        }

    override val directions: Set<TransferDirection>
        get() = emptySet()

    override fun requireSecondPassword(): Single<Boolean> =
        Single.just(false)

    override fun matches(other: CryptoAccount): Boolean =
        other is CryptoInterestAccount && other.asset == asset

    override val accountBalance: Single<Money>
        get() = custodialWalletManager.getInterestAccountBalance(asset)
            .switchIfEmpty(
                Single.just(CryptoValue.zero(asset))
            ).doOnSuccess { hasFunds.set(it.isPositive) }
            .map { it }

    override val pendingBalance: Single<Money>
        get() = custodialWalletManager.getPendingInterestAccountBalance(asset)
            .switchIfEmpty(
                Single.just(CryptoValue.zero(asset))
            ).map { it }

    override val actionableBalance: Single<Money>
        get() = accountBalance // TODO This will need updating when we support transfer out of an interest account

    override val activity: Single<ActivitySummaryList>
        get() = custodialWalletManager.getInterestActivity(asset)
            .onErrorReturn { emptyList() }
            .mapList { interestActivityToSummary(it) }
            .filterActivityStates()
            .doOnSuccess {
                setHasTransactions(it.isNotEmpty())
            }

    private fun interestActivityToSummary(item: InterestActivityItem): ActivitySummaryItem =
        CustodialInterestActivitySummaryItem(
            exchangeRates = exchangeRates,
            cryptoCurrency = item.cryptoCurrency,
            txId = item.id,
            timeStampMs = item.insertedAt.time,
            value = item.value,
            account = this,
            status = item.state,
            type = item.type,
            confirmations = item.extraAttributes?.confirmations ?: 0,
            accountRef = item.extraAttributes?.beneficiary?.accountRef ?: "",
            recipientAddress = item.extraAttributes?.address ?: ""
        )

    private fun Single<ActivitySummaryList>.filterActivityStates(): Single<ActivitySummaryList> {
        return flattenAsObservable { list ->
            list.filter {
                it is CustodialInterestActivitySummaryItem && displayedStates.contains(it.status)
            }
        }.toList()
    }

    // No swaps on interest accounts, so just return the activity list unmodified
    override fun reconcileSwaps(
        tradeItems: List<TradeActivitySummaryItem>,
        activity: List<ActivitySummaryItem>
    ): List<ActivitySummaryItem> = activity

    override val isFunded: Boolean
        get() = hasFunds.get()

    override val isDefault: Boolean = false // Default is, presently, only ever a non-custodial account.

    override val sourceState: Single<TxSourceState>
        get() = Single.just(TxSourceState.CAN_TRANSACT)

    override val isEnabled: Single<Boolean>
        get() = custodialWalletManager.getInterestEligibilityForAsset(asset)
            .map { (enabled, _) ->
                enabled
            }

    override val disabledReason: Single<DisabledReason>
        get() = custodialWalletManager.getInterestEligibilityForAsset(asset)
            .map { (_, reason) ->
                reason
            }

    override val actions: Single<AvailableActions> =
        Single.just(setOf(AssetAction.Deposit, AssetAction.Summary, AssetAction.ViewActivity))

    companion object {
        private val displayedStates = setOf(
            InterestState.COMPLETE,
            InterestState.PROCESSING,
            InterestState.PENDING,
            InterestState.MANUAL_REVIEW
        )
    }
}