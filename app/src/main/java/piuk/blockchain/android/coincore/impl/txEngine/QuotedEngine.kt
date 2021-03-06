package piuk.blockchain.android.coincore.impl.txEngine

import com.blockchain.nabu.datamanagers.CurrencyPair
import com.blockchain.nabu.datamanagers.CustodialOrder
import com.blockchain.nabu.datamanagers.CustodialWalletManager
import com.blockchain.nabu.datamanagers.TransferLimits
import com.blockchain.nabu.datamanagers.TransferDirection
import com.blockchain.nabu.models.responses.nabu.KycTiers
import com.blockchain.nabu.models.responses.nabu.NabuApiException
import com.blockchain.nabu.models.responses.nabu.NabuErrorCodes
import com.blockchain.nabu.service.TierService
import info.blockchain.balance.CryptoValue
import info.blockchain.balance.Money
import io.reactivex.Completable
import io.reactivex.Single
import io.reactivex.disposables.Disposable
import io.reactivex.rxkotlin.Singles
import piuk.blockchain.android.coincore.CryptoAccount
import piuk.blockchain.android.coincore.FiatAccount
import piuk.blockchain.android.coincore.PendingTx
import piuk.blockchain.android.coincore.TransactionTarget
import piuk.blockchain.android.coincore.TxEngine
import piuk.blockchain.android.coincore.TxResult
import piuk.blockchain.android.coincore.ValidationState
import piuk.blockchain.android.coincore.copyAndPut
import piuk.blockchain.android.coincore.impl.makeExternalAssetAddress
import piuk.blockchain.androidcore.data.api.EnvironmentConfig
import piuk.blockchain.androidcore.data.exchangerate.ExchangeRateDataManager
import piuk.blockchain.androidcore.utils.extensions.emptySubscribe
import piuk.blockchain.androidcore.utils.extensions.thenSingle
import java.lang.IllegalStateException
import java.math.RoundingMode

const val QUOTE_SUB = "quote_sub"
private val PendingTx.quoteSub: Disposable?
    get() = (this.engineState[QUOTE_SUB] as? Disposable)

abstract class QuotedEngine(
    protected val quotesEngine: TransferQuotesEngine,
    private val kycTierService: TierService,
    private val walletManager: CustodialWalletManager,
    private val environmentConfig: EnvironmentConfig
) : TxEngine() {
    protected abstract val direction: TransferDirection

    protected abstract val availableBalance: Single<Money>

    protected fun updateLimits(pendingTx: PendingTx, pricedQuote: PricedQuote): Single<PendingTx> =
        Singles.zip(
            kycTierService.tiers(),
            walletManager.getSwapLimits(userFiat)
        ) { tier, limits ->
            onLimitsForTierFetched(tier, limits, pendingTx, pricedQuote)
        }

    protected val pair: CurrencyPair
        get() {
            return txTarget.let {
                when (it) {
                    is CryptoAccount -> CurrencyPair.CryptoCurrencyPair(sourceAccount.asset, it.asset)
                    is FiatAccount -> CurrencyPair.CryptoToFiatCurrencyPair(sourceAccount.asset, it.fiatCurrency)
                    else -> throw IllegalStateException("Unsupported target")
                }
            }
        }

    protected abstract fun onLimitsForTierFetched(
        tier: KycTiers,
        limits: TransferLimits,
        pendingTx: PendingTx,
        pricedQuote: PricedQuote
    ): PendingTx

    protected fun Single<PendingTx>.clearConfirmations(): Single<PendingTx> =
        map {
            it.quoteSub?.dispose()
            it.copy(
                confirmations = emptyList(),
                engineState = it.engineState.toMutableMap().apply { remove(QUOTE_SUB) }.toMap()
            )
        }

    override fun start(
        sourceAccount: CryptoAccount,
        txTarget: TransactionTarget,
        exchangeRates: ExchangeRateDataManager,
        refreshTrigger: RefreshTrigger
    ) {
        super.start(sourceAccount, txTarget, exchangeRates, refreshTrigger)
        quotesEngine.start(direction, pair)
    }

    protected fun Single<PendingTx>.updateQuotePrice(): Single<PendingTx> =
        doOnSuccess {
            quotesEngine.updateAmount(it.amount)
        }

    override fun startConfirmationsUpdate(pendingTx: PendingTx): Single<PendingTx> =
        startQuotesFetchingIfNotStarted(pendingTx)

    private fun startQuotesFetching(): Disposable =
        quotesEngine.pricedQuote.doOnNext {
            refreshConfirmations(true)
        }.emptySubscribe()

    private fun startQuotesFetchingIfNotStarted(pendingTx: PendingTx): Single<PendingTx> =
        Single.just(
            if (pendingTx.quoteSub == null) {
                pendingTx.copy(
                    engineState = pendingTx.engineState.copyAndPut(
                        QUOTE_SUB, startQuotesFetching()
                    )
                )
            } else {
                pendingTx
            }
        )

    protected fun disposeQuotesFetching(pendingTx: PendingTx) {
        pendingTx.quoteSub?.dispose()
        quotesEngine.stop()
    }

    protected fun OnChainTxEngineBase.startFromQuote(quote: PricedQuote) {
        start(
            sourceAccount = sourceAccount,
            txTarget = makeExternalAssetAddress(
                asset = sourceAccount.asset,
                address = quote.transferQuote.sampleDepositAddress,
                environmentConfig = environmentConfig
            ),
            exchangeRates = exchangeRates
        )
    }

    protected fun OnChainTxEngineBase.restartFromOrder(order: CustodialOrder, pendingTx: PendingTx): Single<PendingTx> =
        restart(
            txTarget = makeExternalAssetAddress(
                asset = sourceAccount.asset,
                address = order.depositAddress ?: throw IllegalStateException("Missing deposit address"),
                environmentConfig = environmentConfig,
                postTransactions = { Completable.complete() }
            ),
            pendingTx = pendingTx
        )

    protected fun Single<TxResult>.updateOrderStatus(orderId: String): Single<TxResult> = onErrorResumeNext { error ->
        walletManager.updateOrder(orderId, false).onErrorComplete().toSingle {
            throw error
        }
    }
        .flatMap { result ->
            walletManager.updateOrder(orderId, true).onErrorComplete().thenSingle {
                Single.just(result)
            }
        }

    override fun stop(pendingTx: PendingTx) {
        disposeQuotesFetching(pendingTx)
    }

    // Quotes api returns the error code for pending orders that's why this method belongs here
    protected fun Single<PendingTx>.handlePendingOrdersError(pendingTx: PendingTx): Single<PendingTx> =
        this.onErrorResumeNext {
            if (it is NabuApiException && it.getErrorCode() == NabuErrorCodes.PendingOrdersLimitReached) {
                Single.just(
                    pendingTx.copy(
                        validationState = ValidationState.PENDING_ORDERS_LIMIT_REACHED
                    )
                )
            } else Single.error(it)
        }

    protected fun Money.withUserDpRounding(roundingMode: RoundingMode): CryptoValue =
        (this as? CryptoValue)?.let {
            CryptoValue.fromMajor(it.currency, it.toBigDecimal().setScale(sourceAccount.asset.userDp, roundingMode))
        } ?: throw IllegalStateException("Method only support cryptovalues")
}