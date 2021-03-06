package piuk.blockchain.android.coincore

import androidx.annotation.CallSuper
import androidx.annotation.VisibleForTesting
import com.blockchain.extensions.replace
import com.blockchain.koin.payloadScope
import com.blockchain.nabu.datamanagers.TransactionError
import com.blockchain.preferences.CurrencyPrefs
import info.blockchain.balance.CryptoCurrency
import info.blockchain.balance.CryptoValue
import info.blockchain.balance.ExchangeRate
import info.blockchain.balance.FiatValue
import info.blockchain.balance.Money
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.subjects.BehaviorSubject
import org.koin.core.KoinComponent
import piuk.blockchain.androidcore.data.exchangerate.ExchangeRateDataManager
import piuk.blockchain.androidcore.utils.extensions.emptySubscribe
import piuk.blockchain.androidcore.utils.helperfunctions.unsafeLazy
import timber.log.Timber

open class TransferError(msg: String) : Exception(msg)

enum class ValidationState {
    CAN_EXECUTE,
    UNINITIALISED,
    HAS_TX_IN_FLIGHT,
    INVALID_AMOUNT,
    INSUFFICIENT_FUNDS,
    INSUFFICIENT_GAS,
    INVALID_ADDRESS,
    ADDRESS_IS_CONTRACT,
    OPTION_INVALID,
    UNDER_MIN_LIMIT,
    PENDING_ORDERS_LIMIT_REACHED,
    OVER_MAX_LIMIT,
    OVER_SILVER_TIER_LIMIT,
    OVER_GOLD_TIER_LIMIT,
    INVOICE_EXPIRED,
    UNKNOWN_ERROR
}

class TxValidationFailure(val state: ValidationState) : TransferError("Invalid Send Tx: $state")

enum class FeeLevel {
    None,
    Regular,
    Priority,
    Custom
}

data class TxFee(
    val fee: Money,
    val type: FeeType,
    val asset: CryptoCurrency
) {
    enum class FeeType {
        DEPOSIT_FEE,
        WITHDRAWAL_FEE
    }
}

data class PendingTx(
    val amount: Money,
    val totalBalance: Money,
    val availableBalance: Money,
    val fees: Money,
    val selectedFiat: String,
    val feeLevel: FeeLevel = FeeLevel.None,
    val customFeeAmount: Long = -1L,
    val availableFeeLevels: Set<FeeLevel> = setOf(FeeLevel.None),
    val confirmations: List<TxConfirmationValue> = emptyList(),
    val minLimit: Money? = null,
    val maxLimit: Money? = null,
    val validationState: ValidationState = ValidationState.UNINITIALISED,
    val engineState: Map<String, Any> = emptyMap()
) {
    fun hasOption(confirmation: TxConfirmation): Boolean =
        confirmations.find { it.confirmation == confirmation } != null

    inline fun <reified T : TxConfirmationValue> getOption(confirmation: TxConfirmation): T? =
        confirmations.find { it.confirmation == confirmation } as? T

    // Internal, coincore only helper methods for managing option lists. If you're using these in
    // UI are client code, you're doing something wrong!
    internal fun removeOption(confirmation: TxConfirmation): PendingTx =
        this.copy(
            confirmations = confirmations.filter { it.confirmation != confirmation }
        )

    internal fun addOrReplaceOption(newConfirmation: TxConfirmationValue, prepend: Boolean = false): PendingTx =
        this.copy(
            confirmations = if (hasOption(newConfirmation.confirmation)) {
                val old = confirmations.find {
                    it.confirmation == newConfirmation.confirmation && it::class == newConfirmation::class
                }
                confirmations.replace(old, newConfirmation).filterNotNull()
            } else {
                val opts = confirmations.toMutableList()
                if (prepend) {
                    opts.add(0, newConfirmation)
                } else {
                    opts.add(newConfirmation)
                }
                opts.toList()
            }
        )
}

enum class TxConfirmation {
    DESCRIPTION,
    AGREEMENT_INTEREST_T_AND_C,
    AGREEMENT_INTEREST_TRANSFER,
    READ_ONLY,
    MEMO,
    LARGE_TRANSACTION_WARNING,
    FEE_SELECTION,
    ERROR_NOTICE,
    INVOICE_COUNTDOWN,
    NETWORK_FEE
}

sealed class TxConfirmationValue(open val confirmation: TxConfirmation) {

    data class ExchangePriceConfirmation(val money: Money, val asset: CryptoCurrency) :
        TxConfirmationValue(TxConfirmation.READ_ONLY)

    data class FeedTotal(
        val amount: Money,
        val fee: Money,
        val exchangeAmount: Money? = null,
        val exchangeFee: Money? = null
    ) : TxConfirmationValue(TxConfirmation.READ_ONLY)

    data class SwapExchangeRate(
        val unitCryptoCurrency: Money,
        val price: Money
    ) : TxConfirmationValue(TxConfirmation.READ_ONLY)

    data class SwapReceiveValue(
        val receiveAmount: Money
    ) : TxConfirmationValue(TxConfirmation.READ_ONLY)

    data class From(val from: String) : TxConfirmationValue(TxConfirmation.READ_ONLY)

    data class To(val to: String) : TxConfirmationValue(TxConfirmation.READ_ONLY)

    data class Total(val total: Money, val exchange: Money? = null) : TxConfirmationValue(TxConfirmation.READ_ONLY)

    data class FeeSelection(
        val feeDetails: FeeState? = null,
        val exchange: Money? = null,
        val selectedLevel: FeeLevel,
        val customFeeAmount: Long = -1L,
        val availableLevels: Set<FeeLevel> = emptySet(),
        val feeInfo: FeeInfo? = null,
        val asset: CryptoCurrency
    ) : TxConfirmationValue(TxConfirmation.FEE_SELECTION) {
        data class FeeInfo(
            val regularFee: Long,
            val priorityFee: Long
        )
    }

    data class BitPayCountdown(
        val timeRemainingSecs: Long
    ) : TxConfirmationValue(TxConfirmation.INVOICE_COUNTDOWN)

    data class ErrorNotice(val status: ValidationState, val money: Money? = null) :
        TxConfirmationValue(TxConfirmation.ERROR_NOTICE)

    data class Description(val text: String = "") : TxConfirmationValue(TxConfirmation.DESCRIPTION)

    data class Memo(val text: String?, val isRequired: Boolean, val id: Long?, val editable: Boolean = true) :
        TxConfirmationValue(TxConfirmation.MEMO)

    data class NetworkFee(
        val txFee: TxFee
    ) : TxConfirmationValue(TxConfirmation.NETWORK_FEE)

    data class TxBooleanConfirmation<T>(
        override val confirmation: TxConfirmation,
        val data: T? = null,
        val value: Boolean = false
    ) : TxConfirmationValue(confirmation)

    data class SwapSourceValue(val swappingAssetValue: CryptoValue) : TxConfirmationValue(TxConfirmation.READ_ONLY)

    data class SwapDestinationValue(val receivingAssetValue: CryptoValue) :
        TxConfirmationValue(TxConfirmation.READ_ONLY)
}

sealed class FeeState {
    object FeeTooHigh : FeeState()
    object FeeUnderMinLimit : FeeState()
    object FeeUnderRecommended : FeeState()
    object FeeOverRecommended : FeeState()
    object ValidCustomFee : FeeState()
    data class FeeDetails(
        val absoluteFee: Money
    ) : FeeState()
}

abstract class TxEngine : KoinComponent {

    interface RefreshTrigger {
        fun refreshConfirmations(revalidate: Boolean = false): Completable
    }

    private lateinit var _sourceAccount: CryptoAccount
    private lateinit var _txTarget: TransactionTarget
    private lateinit var _exchangeRates: ExchangeRateDataManager
    private lateinit var _refresh: RefreshTrigger

    protected val sourceAccount: CryptoAccount
        get() = _sourceAccount

    protected val txTarget: TransactionTarget
        get() = _txTarget

    protected val exchangeRates: ExchangeRateDataManager
        get() = _exchangeRates

    @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    fun refreshConfirmations(revalidate: Boolean = false) =
        _refresh.refreshConfirmations(revalidate).emptySubscribe()

    @CallSuper
    open fun start(
        sourceAccount: CryptoAccount,
        txTarget: TransactionTarget,
        exchangeRates: ExchangeRateDataManager,
        refreshTrigger: RefreshTrigger = object : RefreshTrigger {
            override fun refreshConfirmations(revalidate: Boolean): Completable = Completable.complete()
        }
    ) {
        this._sourceAccount = sourceAccount
        this._txTarget = txTarget
        this._exchangeRates = exchangeRates
        this._refresh = refreshTrigger
    }

    @CallSuper
    open fun restart(txTarget: TransactionTarget, pendingTx: PendingTx): Single<PendingTx> {
        this._txTarget = txTarget
        return Single.just(pendingTx)
    }

    open fun stop(pendingTx: PendingTx) {}

    // Optionally assert, via require() etc, that sourceAccounts and txTarget
    // are valid for this engine.
    open fun assertInputsValid() {}

    open val userFiat: String by unsafeLazy {
        payloadScope.get<CurrencyPrefs>().selectedFiatCurrency
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    val asset: CryptoCurrency
        get() = sourceAccount.asset

    open val requireSecondPassword: Boolean = false

    // Does this engine accept fiat input amounts
    open val canTransactFiat: Boolean = false

    // Return a stream of the exchange rate between the source asset and the user's selected
    // fiat currency. This should always return at least once, but can safely either complete
    // or keep sending updated rates, depending on what is useful for Transaction context
    open fun userExchangeRate(): Observable<ExchangeRate> =
        Observable.just(
            exchangeRates.getLastPrice(sourceAccount.asset, userFiat)
        ).map { rate ->
            ExchangeRate.CryptoToFiat(
                sourceAccount.asset,
                userFiat,
                rate
            )
        }

    abstract fun doBuildConfirmations(pendingTx: PendingTx): Single<PendingTx>

    open fun doRefreshConfirmations(pendingTx: PendingTx): Single<PendingTx> =
        Single.just(pendingTx)

    // If the source and target assets are not the same this MAY return a stream of the exchange rates
    // between them. Or it may simply complete. This is not used yet in the UI, but it may be when
    // sell and or swap are fully integrated into this flow
    open fun targetExchangeRate(): Observable<ExchangeRate> =
        Observable.empty()

    // Implementation interface:
    // Call this first to initialise the processor. Construct and initialise a pendingTx object.
    abstract fun doInitialiseTx(): Single<PendingTx>

    // Update the transaction with a new amount. This method should check balances, calculate fees and
    // Return a new PendingTx with the state updated for the UI to update. The pending Tx will
    // be passed to validate after this call.
    abstract fun doUpdateAmount(amount: Money, pendingTx: PendingTx): Single<PendingTx>

    // Update the selected fee level of this Tx. This should check & update balances etc
    abstract fun doUpdateFeeLevel(pendingTx: PendingTx, level: FeeLevel, customFeeAmount: Long): Single<PendingTx>

    // Process any TxOption updates, if required. The default just replaces the option and returns
    // the updated pendingTx. Subclasses may want to, eg, update amounts on fee changes etc
    open fun doOptionUpdateRequest(pendingTx: PendingTx, newConfirmation: TxConfirmationValue): Single<PendingTx> =
        Single.just(pendingTx.addOrReplaceOption(newConfirmation))

    // Check the tx is complete, well formed and possible. If it is, set pendingTx to CAN_EXECUTE
    // Else set it to the appropriate error, and then return the updated PendingTx
    abstract fun doValidateAmount(pendingTx: PendingTx): Single<PendingTx>

    // Check the tx is complete, well formed and possible. If it is, set pendingTx to CAN_EXECUTE
    // Else set it to the appropriate error, and then return the updated PendingTx
    abstract fun doValidateAll(pendingTx: PendingTx): Single<PendingTx>

    // Execute the transaction, it will have been validated before this is called, so the expectation
    // is that it will succeed.
    abstract fun doExecute(pendingTx: PendingTx, secondPassword: String): Single<TxResult>

    // Action to be executed once the transaction has been executed, it will have been validated before this is called, so the expectation
    // is that it will succeed.
    open fun doPostExecute(txResult: TxResult): Completable = Completable.complete()

    // Action to be executed when confirmations have been built and we want to start checking for updates on them
    open fun startConfirmationsUpdate(pendingTx: PendingTx): Single<PendingTx> =
        Single.just(pendingTx)
}

class TransactionProcessor(
    sourceAccount: CryptoAccount,
    txTarget: TransactionTarget,
    exchangeRates: ExchangeRateDataManager,
    private val engine: TxEngine
) : TxEngine.RefreshTrigger {

    init {
        engine.start(
            sourceAccount,
            txTarget,
            exchangeRates,
            this
        )
        engine.assertInputsValid()
    }

    val requireSecondPassword: Boolean
        get() = engine.requireSecondPassword

    val canTransactFiat: Boolean
        get() = engine.canTransactFiat

    private val txObservable: BehaviorSubject<PendingTx> = BehaviorSubject.create()

    private fun updatePendingTx(pendingTx: PendingTx) =
        txObservable.onNext(pendingTx)

    private fun getPendingTx(): PendingTx =
        txObservable.value ?: throw IllegalStateException("TransactionProcessor not initialised")

    // Initialise the tx as required.
    // This will start propagating the pendingTx to the client code.
    fun initialiseTx(): Observable<PendingTx> =
        engine.doInitialiseTx()
            .doOnSuccess {
                updatePendingTx(it)
            }.flatMapObservable {
                txObservable
            }

    // Set the option to the passed option value. If the option is not supported, it will not be
    // in the original list when the pendingTx is created. And if it is not supported, then trying to
    // update it will cause an error.
    fun setOption(newConfirmation: TxConfirmationValue): Completable {

        val pendingTx = getPendingTx()
        if (!pendingTx.hasOption(newConfirmation.confirmation)) {
            throw IllegalArgumentException("Unsupported TxOption: ${newConfirmation.confirmation}")
        }

        return engine.doOptionUpdateRequest(pendingTx, newConfirmation)
            .flatMap { pTx ->
                engine.doValidateAll(pTx)
            }.doOnSuccess { pTx ->
                updatePendingTx(pTx)
            }.ignoreElement()
    }

    fun updateAmount(amount: Money): Completable {
        Timber.d("!TRANSACTION!> in UpdateAmount")
        val pendingTx = getPendingTx()
        if (!canTransactFiat && amount is FiatValue)
            throw IllegalArgumentException("The processor does not support fiat values")

        return engine.doUpdateAmount(amount, pendingTx)
            .flatMap {
                val isFreshTx = it.validationState == ValidationState.UNINITIALISED
                engine.doValidateAmount(it)
                    .map { pendingTx ->
                        // Remove initial "insufficient funds' warning
                        if (amount.isZero && isFreshTx) {
                            pendingTx.copy(validationState = ValidationState.UNINITIALISED)
                        } else {
                            pendingTx
                        }
                    }
            }
            .doOnSuccess {
                updatePendingTx(it)
            }
            .ignoreElement()
    }

    // Check that the fee level is supported, then call into the engine to set the fee and validate ballances etc
    // the selected fee level is supported
    fun updateFeeLevel(level: FeeLevel, customFeeAmount: Long = -1): Completable {
        Timber.d("!TRANSACTION!> in UpdateFeeLevel")
        val pendingTx = getPendingTx()
        check(pendingTx.availableFeeLevels.contains(level)) {
            "Fee Level $level not supported by engine ${engine::class.java.name}"
        }

        return engine.doUpdateFeeLevel(pendingTx, level, customFeeAmount)
            .flatMap { engine.doValidateAmount(it) }
            .doOnSuccess { updatePendingTx(it) }
            .ignoreElement()
    }

    // Return a stream of the exchange rate between the source asset and the user's selected
    // fiat currency. This should always return at least once, but can safely either complete
    // or keep sending updated rates, depending on what is useful for Transaction context
    fun userExchangeRate(): Observable<ExchangeRate> =
        engine.userExchangeRate()

    // Check the validity of a pending transactions.
    fun validateAll(): Completable {
        val pendingTx = getPendingTx()
        return engine.doBuildConfirmations(pendingTx).flatMap {
            engine.doValidateAll(it)
        }.doOnSuccess { updatePendingTx(it) }
            .flatMapCompletable { px ->
                engine.startConfirmationsUpdate(px).doOnSuccess { updatePendingTx(it) }.ignoreElement()
            }
    }

    // Execute the transaction.
    // Ideally, I'd like to return the Tx id/hash. But we get nothing back from the
    // custodial APIs (and are not likely to, since the tx is batched and not executed immediately)
    fun execute(secondPassword: String): Completable {
        if (requireSecondPassword && secondPassword.isEmpty()) {
            throw IllegalArgumentException("Second password not supplied")
        }

        return engine.doValidateAll(getPendingTx())
            .flatMapCompletable {
                it.validationState.toErrorStateOrExecute(it, secondPassword)
            }
    }

    private fun ValidationState.toErrorStateOrExecute(pendingTx: PendingTx, secondPassword: String): Completable =
        when (this) {
            ValidationState.CAN_EXECUTE -> {
                engine.doExecute(pendingTx, secondPassword).flatMapCompletable { result ->
                    engine.doPostExecute(result)
                }
            }
            ValidationState.UNINITIALISED -> Completable.error(TransactionError.UnexpectedError)
            ValidationState.HAS_TX_IN_FLIGHT -> Completable.error(TransactionError.OrderLimitReached)
            ValidationState.INVALID_AMOUNT -> Completable.error(TransactionError.InvalidDestinationAmount)
            ValidationState.INSUFFICIENT_FUNDS -> Completable.error(TransactionError.InsufficientBalance)
            ValidationState.INSUFFICIENT_GAS -> Completable.error(TransactionError.InsufficientBalance)
            ValidationState.INVALID_ADDRESS -> Completable.error(TransactionError.InvalidCryptoAddress)
            ValidationState.ADDRESS_IS_CONTRACT -> Completable.error(TransactionError.InvalidCryptoAddress)
            ValidationState.OPTION_INVALID -> Completable.error(TransactionError.UnexpectedError)
            ValidationState.UNDER_MIN_LIMIT -> Completable.error(TransactionError.OrderBelowMin)
            ValidationState.PENDING_ORDERS_LIMIT_REACHED ->
                Completable.error(TransactionError.OrderLimitReached)
            ValidationState.OVER_MAX_LIMIT,
            ValidationState.OVER_SILVER_TIER_LIMIT,
            ValidationState.OVER_GOLD_TIER_LIMIT -> Completable.error(TransactionError.OrderAboveMax)
            ValidationState.INVOICE_EXPIRED -> Completable.error(TransactionError.UnexpectedError)
            ValidationState.UNKNOWN_ERROR -> throw IllegalStateException("PendingTx is not executable")
        }

    // If the source and target assets are not the same this MAY return a stream of the exchange rates
    // between them. Or it may simply complete. This is not used yet in the UI, but it may be when
    // sell and or swap are fully integrated into this flow
    fun targetExchangeRate(): Observable<ExchangeRate> =
        engine.targetExchangeRate()

    // Called back by the engine if it has received an external signal and the existing confirmation set
    // requires a refresh
    override fun refreshConfirmations(revalidate: Boolean): Completable {
        val pendingTx = getPendingTx()
        // Don't refresh if confirmations are not created yet:
        return if (pendingTx.confirmations.isNotEmpty()) {
            engine.doRefreshConfirmations(pendingTx)
                .flatMap {
                    if (revalidate) {
                        engine.doValidateAll(it)
                    } else {
                        Single.just(it)
                    }
                }.doOnSuccess {
                    updatePendingTx(it)
                }.ignoreElement()
        } else {
            Completable.complete()
        }
    }

    fun reset() {
        // if initialise tx fails then getPendingTx will crash
        try {
            engine.stop(getPendingTx())
        } catch (e: IllegalStateException) {
        }
    }
}

fun Completable.updateTxValidity(pendingTx: PendingTx): Single<PendingTx> =
    this.toSingle {
        pendingTx.copy(validationState = ValidationState.CAN_EXECUTE)
    }.updateTxValidity(pendingTx)

fun Single<PendingTx>.updateTxValidity(pendingTx: PendingTx): Single<PendingTx> =
    this.onErrorReturn {
        if (it is TxValidationFailure) {
            pendingTx.copy(validationState = it.state)
        } else {
            throw it
        }
    }.map { pTx ->
        if (pTx.confirmations.isNotEmpty())
            updateOptionsWithValidityWarning(pTx)
        else
            pTx
    }

private fun updateOptionsWithValidityWarning(pendingTx: PendingTx): PendingTx =
    if (pendingTx.validationState !in setOf(ValidationState.CAN_EXECUTE, ValidationState.UNINITIALISED)) {
        pendingTx.addOrReplaceOption(
            TxConfirmationValue.ErrorNotice(
                status = pendingTx.validationState,
                money = if (pendingTx.validationState == ValidationState.UNDER_MIN_LIMIT) pendingTx.minLimit else null
            )
        )
    } else {
        pendingTx.removeOption(TxConfirmation.ERROR_NOTICE)
    }

sealed class TxResult(val amount: Money) {
    class HashedTxResult(val txHash: String, amount: Money) : TxResult(amount)
    class UnHashedTxResult(amount: Money) : TxResult(amount)
}

internal fun <K, V> Map<K, V>.copyAndPut(k: K, v: V): Map<K, V> =
    toMutableMap().apply { put(k, v) }.toMap()