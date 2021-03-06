package piuk.blockchain.android.coincore.erc20

import com.blockchain.preferences.WalletStatus
import com.blockchain.nabu.datamanagers.CustodialWalletManager
import info.blockchain.balance.CryptoCurrency
import info.blockchain.balance.Money
import io.reactivex.Completable
import io.reactivex.Single
import io.reactivex.rxkotlin.Singles
import piuk.blockchain.android.coincore.ActivitySummaryList
import piuk.blockchain.android.coincore.CryptoAddress
import piuk.blockchain.android.coincore.ReceiveAddress
import piuk.blockchain.android.coincore.TxEngine
import piuk.blockchain.android.coincore.TxResult
import piuk.blockchain.android.coincore.TxSourceState
import piuk.blockchain.android.coincore.impl.CryptoNonCustodialAccount
import piuk.blockchain.androidcore.data.erc20.FeedErc20Transfer
import piuk.blockchain.androidcore.data.ethereum.EthDataManager
import piuk.blockchain.androidcore.data.exchangerate.ExchangeRateDataManager
import piuk.blockchain.androidcore.data.fees.FeeDataManager
import piuk.blockchain.androidcore.data.payload.PayloadDataManager
import piuk.blockchain.androidcore.utils.extensions.mapList
import java.util.concurrent.atomic.AtomicBoolean

class Erc20NonCustodialAccount(
    payloadManager: PayloadDataManager,
    asset: CryptoCurrency,
    private val ethDataManager: EthDataManager,
    internal val address: String,
    private val fees: FeeDataManager,
    override val label: String,
    override val exchangeRates: ExchangeRateDataManager,
    private val walletPreferences: WalletStatus,
    private val custodialWalletManager: CustodialWalletManager
) : CryptoNonCustodialAccount(payloadManager, asset) {

    private val hasFunds = AtomicBoolean(false)

    override val isFunded: Boolean
        get() = hasFunds.get()

    override val isDefault: Boolean = true // Only one account, so always default

    override val receiveAddress: Single<ReceiveAddress>
        get() = Single.just(
            Erc20Address(asset, address, label)
        )

    override val accountBalance: Single<Money>
        get() = ethDataManager.getErc20Balance(asset)
            .doOnSuccess { hasFunds.set(it.isPositive) }
            .map { it }

    override val actionableBalance: Single<Money>
        get() = accountBalance

    override val activity: Single<ActivitySummaryList>
        get() {
            val feedTransactions = ethDataManager.fetchErc20DataModel(asset)
                    .flatMap { ethDataManager.getErc20Transactions(asset) }
                    .mapList {
                        val feeObservable = ethDataManager
                            .getTransaction(it.transactionHash)
                            .map { transaction ->
                                transaction.gasUsed * transaction.gasPrice
                            }
                        FeedErc20Transfer(it, feeObservable)
                    }

            return Singles.zip(
                feedTransactions,
                ethDataManager.getErc20AccountHash(asset),
                ethDataManager.getLatestBlockNumber()
            ) { transactions, accountHash, latestBlockNumber ->
                transactions.map { transaction ->
                    Erc20ActivitySummaryItem(
                        asset,
                        feedTransfer = transaction,
                        accountHash = accountHash,
                        ethDataManager = ethDataManager,
                        exchangeRates = exchangeRates,
                        lastBlockNumber = latestBlockNumber.number,
                        account = this
                    )
                }
            }.flatMap {
                appendTradeActivity(custodialWalletManager, asset, it)
            }.doOnSuccess { setHasTransactions(it.isNotEmpty()) }
        }

    override val sourceState: Single<TxSourceState>
        get() = super.sourceState.flatMap { state ->
            ethDataManager.isLastTxPending().map { hasUnconfirmed ->
                if (hasUnconfirmed) {
                    TxSourceState.TRANSACTION_IN_FLIGHT
                } else {
                    state
                }
            }
        }

    override fun createTxEngine(): TxEngine =
        Erc20OnChainTxEngine(
            ethDataManager = ethDataManager,
            feeManager = fees,
            requireSecondPassword = ethDataManager.requireSecondPassword,
            walletPreferences = walletPreferences
        )
}

internal open class Erc20Address(
    final override val asset: CryptoCurrency,
    override val address: String,
    override val label: String = address,
    override val onTxCompleted: (TxResult) -> Completable = { Completable.complete() }
) : CryptoAddress {
    init {
        require(asset.hasFeature(CryptoCurrency.IS_ERC20))
    }
}