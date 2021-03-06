package piuk.blockchain.android.coincore.impl.txEngine

import com.blockchain.notifications.analytics.Analytics
import com.blockchain.preferences.WalletStatus
import info.blockchain.balance.CryptoCurrency
import info.blockchain.balance.CryptoValue
import info.blockchain.balance.Money
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.disposables.Disposable
import piuk.blockchain.android.coincore.CryptoAccount
import piuk.blockchain.android.coincore.FeeLevel
import piuk.blockchain.android.coincore.FeeState
import piuk.blockchain.android.coincore.PendingTx
import piuk.blockchain.android.coincore.TransactionTarget
import piuk.blockchain.android.coincore.TxConfirmation
import piuk.blockchain.android.coincore.TxConfirmationValue
import piuk.blockchain.android.coincore.TxEngine
import piuk.blockchain.android.coincore.TxResult
import piuk.blockchain.android.coincore.TxValidationFailure
import piuk.blockchain.android.coincore.ValidationState
import piuk.blockchain.android.coincore.copyAndPut
import piuk.blockchain.android.coincore.impl.BitPayInvoiceTarget
import piuk.blockchain.android.coincore.impl.CryptoNonCustodialAccount
import piuk.blockchain.android.coincore.updateTxValidity
import piuk.blockchain.android.data.api.bitpay.BitPayDataManager
import piuk.blockchain.android.data.api.bitpay.analytics.BitPayEvent
import piuk.blockchain.android.data.api.bitpay.models.BitPayTransaction
import piuk.blockchain.android.data.api.bitpay.models.BitPaymentRequest
import piuk.blockchain.androidcore.data.exchangerate.ExchangeRateDataManager
import piuk.blockchain.androidcore.utils.helperfunctions.unsafeLazy
import rx.Subscription
import timber.log.Timber
import java.util.concurrent.TimeUnit

const val BITPAY_TIMER_SUB = "bitpay_timer"
private val PendingTx.bitpayTimer: Subscription?
    get() = (this.engineState[BITPAY_TIMER_SUB] as? Subscription)

interface EngineTransaction {
    val encodedMsg: String
    val msgSize: Int
    val txHash: String
}

interface BitPayClientEngine {
    fun doPrepareTransaction(pendingTx: PendingTx, secondPassword: String): Single<EngineTransaction>
    fun doOnTransactionSuccess(pendingTx: PendingTx)
    fun doOnTransactionFailed(pendingTx: PendingTx, e: Throwable)
}

class BitpayTxEngine(
    private val bitPayDataManager: BitPayDataManager,
    private val assetEngine: OnChainTxEngineBase,
    private val walletPrefs: WalletStatus,
    private val analytics: Analytics
) : TxEngine() {

    override fun assertInputsValid() {
        // Only support non-custodial BTC bitpay at this time
        check(sourceAccount is CryptoNonCustodialAccount)
        check(asset == CryptoCurrency.BTC)
        check(txTarget is BitPayInvoiceTarget)
        require(assetEngine is BitPayClientEngine)
        assetEngine.assertInputsValid()
    }

    private val executionClient: BitPayClientEngine by unsafeLazy {
        assetEngine as BitPayClientEngine
    }

    private val bitpayInvoice: BitPayInvoiceTarget by unsafeLazy {
        txTarget as BitPayInvoiceTarget
    }

    override fun start(
        sourceAccount: CryptoAccount,
        txTarget: TransactionTarget,
        exchangeRates: ExchangeRateDataManager,
        refreshTrigger: RefreshTrigger
    ) {
        super.start(sourceAccount, txTarget, exchangeRates, refreshTrigger)
        assetEngine.start(sourceAccount, txTarget, exchangeRates, refreshTrigger)
    }

    override fun doInitialiseTx(): Single<PendingTx> =
        assetEngine.doInitialiseTx()
            .map { tx ->
                tx.copy(
                    amount = bitpayInvoice.amount,
                    feeLevel = FeeLevel.Priority,
                    availableFeeLevels = AVAILABLE_FEE_LEVELS
                )
            }

    override fun doBuildConfirmations(pendingTx: PendingTx): Single<PendingTx> =
        assetEngine.doUpdateAmount(bitpayInvoice.amount, pendingTx)
            .flatMap { assetEngine.doBuildConfirmations(it) }
            .map { pTx ->
                startTimerIfNotStarted(pTx)
            }.map { pTx ->
                pTx.addOrReplaceOption(
                    TxConfirmationValue.BitPayCountdown(timeRemainingSecs()),
                    true
                ).run {
                    if (hasOption(TxConfirmation.FEE_SELECTION)) {
                        addOrReplaceOption(makeFeeSelectionOption(pTx))
                    } else {
                        this
                    }
                }
            }

    override fun doRefreshConfirmations(pendingTx: PendingTx): Single<PendingTx> =
        Single.just(pendingTx.addOrReplaceOption(TxConfirmationValue.BitPayCountdown(timeRemainingSecs()), true))

    private fun startTimerIfNotStarted(pendingTx: PendingTx): PendingTx =
        if (pendingTx.bitpayTimer == null) {
            pendingTx.copy(
                engineState = pendingTx.engineState.copyAndPut(
                    BITPAY_TIMER_SUB, startCountdownTimer(timeRemainingSecs())
                )
            )
        } else {
            pendingTx
        }

    private fun timeRemainingSecs() =
        (bitpayInvoice.expireTimeMs - System.currentTimeMillis()) / 1000

    private fun startCountdownTimer(remainingTime: Long): Disposable {
        var remaining = remainingTime
        return Observable.interval(1, TimeUnit.SECONDS)
            .doOnEach { remaining-- }
            .map { remaining }
            .doOnNext { updateCountdownConfirmation() }
            .takeUntil { it <= TIMEOUT_STOP }
            .doOnComplete { handleCountdownComplete() }
            .subscribe()
    }

    private fun updateCountdownConfirmation() {
        refreshConfirmations(false)
    }

    private fun handleCountdownComplete() {
        Timber.d("BitPay Invoice Countdown expired")
        refreshConfirmations(true)
    }

    // BitPay invoices _always_ require priority fees, so replace the option as defined by the
    // underlying asset engine.
    private fun makeFeeSelectionOption(pendingTx: PendingTx): TxConfirmationValue.FeeSelection =
        TxConfirmationValue.FeeSelection(
            feeDetails = FeeState.FeeDetails(pendingTx.fees),
            exchange = pendingTx.fees.toFiat(exchangeRates, userFiat),
            selectedLevel = pendingTx.feeLevel,
            availableLevels = setOf(FeeLevel.Priority),
            asset = asset
        )

    // Don't set the amount here, it is fixed so we can do it in the confirmation building step
    override fun doUpdateAmount(amount: Money, pendingTx: PendingTx): Single<PendingTx> =
        Single.just(pendingTx)

    override fun doUpdateFeeLevel(
        pendingTx: PendingTx,
        level: FeeLevel,
        customFeeAmount: Long
    ): Single<PendingTx> {
        require(pendingTx.availableFeeLevels.contains(level))
        return Single.just(pendingTx)
    }

    override fun doValidateAmount(pendingTx: PendingTx): Single<PendingTx> =
        assetEngine.doValidateAmount(pendingTx)

    override fun doValidateAll(pendingTx: PendingTx): Single<PendingTx> =
        doValidateTimeout(pendingTx)
            .flatMap { assetEngine.doValidateAll(pendingTx) }
            .updateTxValidity(pendingTx)

    private fun doValidateTimeout(pendingTx: PendingTx): Single<PendingTx> =
        Single.just(pendingTx)
            .map { pTx ->
                if (timeRemainingSecs() <= TIMEOUT_STOP) {
                    analytics.logEvent(BitPayEvent.InvoiceExpired)
                    throw TxValidationFailure(ValidationState.INVOICE_EXPIRED)
                }
                pTx
            }

    override fun doExecute(pendingTx: PendingTx, secondPassword: String): Single<TxResult> =
        executionClient.doPrepareTransaction(pendingTx, secondPassword)
            .flatMap { preparedTx ->
                doExecuteTransaction(bitpayInvoice.invoiceId, preparedTx)
            }.doOnSuccess {
                walletPrefs.setBitPaySuccess()
                analytics.logEvent(BitPayEvent.TxSuccess(pendingTx.amount as CryptoValue))
                executionClient.doOnTransactionSuccess(pendingTx)
            }.doOnError { e ->
                analytics.logEvent(BitPayEvent.TxFailed(e.message ?: e.toString()))
                executionClient.doOnTransactionFailed(pendingTx, e)
            }.map {
                TxResult.HashedTxResult(it, pendingTx.amount)
            }

    private fun doExecuteTransaction(
        invoiceId: String,
        tx: EngineTransaction
    ): Single<String> =
        bitPayDataManager.paymentVerificationRequest(
            invoiceId,
            BitPaymentRequest(
                CryptoCurrency.BTC.networkTicker,
                listOf(
                    BitPayTransaction(
                        tx.encodedMsg,
                        tx.msgSize
                    )
                )
            )
        ).flatMap {
            Single.timer(3, TimeUnit.SECONDS)
        }.flatMap {
            bitPayDataManager.paymentSubmitRequest(
                invoiceId,
                BitPaymentRequest(
                    CryptoCurrency.BTC.networkTicker,
                    listOf(
                        BitPayTransaction(
                            tx.encodedMsg,
                            tx.msgSize
                        )
                    )
                )
            )
        }.map {
            tx.txHash
        }

    companion object {
        private const val TIMEOUT_STOP = 2
        private val AVAILABLE_FEE_LEVELS = setOf(FeeLevel.Priority)
    }
}
