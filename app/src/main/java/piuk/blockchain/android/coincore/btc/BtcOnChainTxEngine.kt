package piuk.blockchain.android.coincore.btc

import com.blockchain.logging.CrashLogger
import com.blockchain.nabu.datamanagers.TransactionError
import com.blockchain.preferences.WalletStatus
import info.blockchain.api.data.UnspentOutputs
import info.blockchain.balance.CryptoCurrency
import info.blockchain.balance.CryptoValue
import info.blockchain.balance.Money
import info.blockchain.wallet.api.data.FeeOptions
import info.blockchain.wallet.payment.Payment
import info.blockchain.wallet.payment.SpendableUnspentOutputs
import info.blockchain.wallet.util.FormatsUtil
import io.reactivex.Completable
import io.reactivex.Single
import io.reactivex.rxkotlin.Singles
import org.bitcoinj.core.NetworkParameters
import org.bitcoinj.core.Transaction
import org.koin.core.KoinComponent
import org.koin.core.inject
import org.spongycastle.util.encoders.Hex
import piuk.blockchain.android.coincore.CryptoAddress
import piuk.blockchain.android.coincore.FeeLevel
import piuk.blockchain.android.coincore.PendingTx
import piuk.blockchain.android.coincore.TxConfirmation
import piuk.blockchain.android.coincore.TxConfirmationValue
import piuk.blockchain.android.coincore.TxResult
import piuk.blockchain.android.coincore.TxValidationFailure
import piuk.blockchain.android.coincore.ValidationState
import piuk.blockchain.android.coincore.copyAndPut
import piuk.blockchain.android.coincore.impl.txEngine.BitPayClientEngine
import piuk.blockchain.android.coincore.impl.txEngine.EngineTransaction
import piuk.blockchain.android.coincore.impl.txEngine.OnChainTxEngineBase
import piuk.blockchain.android.coincore.updateTxValidity
import piuk.blockchain.androidcore.data.fees.FeeDataManager
import piuk.blockchain.androidcore.data.payload.PayloadDataManager
import piuk.blockchain.androidcore.data.payments.SendDataManager
import piuk.blockchain.androidcore.utils.extensions.then
import piuk.blockchain.androidcore.utils.helperfunctions.unsafeLazy
import timber.log.Timber
import java.math.BigDecimal
import java.math.BigInteger

private const val STATE_UTXO = "btc_utxo"
private const val FEE_OPTIONS = "fee_options"

private val PendingTx.utxoBundle: SpendableUnspentOutputs
    get() = (this.engineState[STATE_UTXO] as? SpendableUnspentOutputs) ?: SpendableUnspentOutputs()

private val PendingTx.feeOptions: FeeOptions
    get() = (this.engineState[FEE_OPTIONS] as? FeeOptions) ?: FeeOptions()

private class BtcPreparedTx(
    val btcTx: Transaction
) : EngineTransaction {
    override val encodedMsg: String
        get() = String(Hex.encode(btcTx.bitcoinSerialize()))
    override val msgSize: Int = btcTx.messageSize
    override val txHash: String = btcTx.hashAsString
}

class BtcOnChainTxEngine(
    private val btcDataManager: PayloadDataManager,
    private val sendDataManager: SendDataManager,
    private val feeManager: FeeDataManager,
    private val btcNetworkParams: NetworkParameters,
    walletPreferences: WalletStatus,
    requireSecondPassword: Boolean
) : OnChainTxEngineBase(
    requireSecondPassword,
    walletPreferences
), BitPayClientEngine, KoinComponent {

    override fun assertInputsValid() {
        check(sourceAccount is BtcCryptoWalletAccount)
        check(txTarget is CryptoAddress)
        check((txTarget as CryptoAddress).asset == CryptoCurrency.BTC)
        check(asset == CryptoCurrency.BTC)
    }

    private val btcTarget: CryptoAddress
        get() = txTarget as CryptoAddress

    private val btcSource: BtcCryptoWalletAccount by unsafeLazy {
        sourceAccount as BtcCryptoWalletAccount
    }

    private val sourceAddress: String by unsafeLazy {
        btcSource.xpubAddress
    }

    override fun doInitialiseTx(): Single<PendingTx> =
        Single.just(
            PendingTx(
                amount = CryptoValue.zero(asset),
                totalBalance = CryptoValue.zero(asset),
                availableBalance = CryptoValue.zero(asset),
                fees = CryptoValue.zero(asset),
                feeLevel = mapSavedFeeToFeeLevel(fetchDefaultFeeLevel(asset)),
                availableFeeLevels = AVAILABLE_FEE_OPTIONS,
                selectedFiat = userFiat
            )
        )

    override fun doUpdateAmount(amount: Money, pendingTx: PendingTx): Single<PendingTx> =
        Singles.zip(
            sourceAccount.accountBalance.map { it as CryptoValue },
            getDynamicFeePerKb(pendingTx),
            getUnspentApiResponse(sourceAddress)
        ) { total, optionsAndFeePerKb, coins ->
            updatePendingTxFromAmount(
                amount as CryptoValue,
                total,
                pendingTx,
                optionsAndFeePerKb.second,
                optionsAndFeePerKb.first,
                coins
            )
        }.onErrorReturnItem(
            pendingTx.copy(
                validationState = ValidationState.INSUFFICIENT_FUNDS
            )
        )

    private fun getUnspentApiResponse(address: String): Single<UnspentOutputs> =
        if (btcDataManager.getAddressBalance(address) > CryptoValue.zero(asset)) {
            sendDataManager.getUnspentBtcOutputs(address)
                // If we get here, we should have balance... but if we have no UTXOs then we have
                // a problem:
                .map { utxo ->
                    if (utxo.unspentOutputs.isEmpty()) {
                        throw fatalError(IllegalStateException("No BTC UTXOs found for non-zero balance"))
                    } else {
                        utxo
                    }
                }
                .singleOrError()
        } else {
            Single.error(Throwable("No BTC funds"))
        }

    private fun getDynamicFeePerKb(pendingTx: PendingTx): Single<Pair<FeeOptions, CryptoValue>> =
        feeManager.btcFeeOptions
            .map { feeOptions ->
                when (pendingTx.feeLevel) {
                    FeeLevel.None -> Pair(feeOptions, CryptoValue.zero(asset))
                    FeeLevel.Regular -> Pair(feeOptions, feeToCrypto(feeOptions.regularFee))
                    FeeLevel.Priority -> Pair(feeOptions, feeToCrypto(feeOptions.priorityFee))
                    FeeLevel.Custom -> Pair(feeOptions, feeToCrypto(pendingTx.customFeeAmount))
                }
            }.singleOrError()

    private fun feeToCrypto(feePerKb: Long): CryptoValue =
        CryptoValue.fromMinor(asset, (feePerKb * 1000).toBigInteger())

    private fun updatePendingTxFromAmount(
        amount: CryptoValue,
        balance: CryptoValue,
        pendingTx: PendingTx,
        feePerKb: CryptoValue,
        feeOptions: FeeOptions,
        coins: UnspentOutputs
    ): PendingTx {
        val maxAvailable = sendDataManager.getMaximumAvailable(
            cryptoCurrency = asset,
            unspentCoins = coins,
            feePerKb = feePerKb
        ) // This is total balance, with fees deducted

        val utxoBundle = sendDataManager.getSpendableCoins(
            unspentCoins = coins,
            paymentAmount = amount,
            feePerKb = feePerKb
        )

        return pendingTx.copy(
            amount = amount,
            totalBalance = balance,
            availableBalance = maxAvailable,
            fees = CryptoValue.fromMinor(CryptoCurrency.BTC, utxoBundle.absoluteFee),
            engineState = pendingTx.engineState
                .copyAndPut(STATE_UTXO, utxoBundle)
                .copyAndPut(FEE_OPTIONS, feeOptions)
        )
    }

    override fun doValidateAmount(pendingTx: PendingTx): Single<PendingTx> =
        validateAmounts(pendingTx)
            .then { validateSufficientFunds(pendingTx) }
            .updateTxValidity(pendingTx)

    override fun doBuildConfirmations(pendingTx: PendingTx): Single<PendingTx> =
        Single.just(
            pendingTx.copy(
                confirmations = mutableListOf(
                    TxConfirmationValue.From(from = sourceAccount.label),
                    TxConfirmationValue.To(to = txTarget.label),
                    makeFeeSelectionOption(pendingTx),
                    TxConfirmationValue.FeedTotal(
                        amount = pendingTx.amount,
                        fee = pendingTx.fees,
                        exchangeFee = pendingTx.fees.toFiat(exchangeRates, userFiat),
                        exchangeAmount = pendingTx.amount.toFiat(exchangeRates, userFiat)
                    ),
                    TxConfirmationValue.Description()
                ).apply {
                    if (isLargeTransaction(pendingTx)) {
                        add(TxConfirmationValue.TxBooleanConfirmation<Unit>(TxConfirmation.LARGE_TRANSACTION_WARNING))
                    }
                }.toList()
            )
        )

    override fun makeFeeSelectionOption(pendingTx: PendingTx): TxConfirmationValue.FeeSelection =
        TxConfirmationValue.FeeSelection(
            feeDetails = getFeeState(pendingTx, pendingTx.feeOptions),
            customFeeAmount = pendingTx.customFeeAmount,
            exchange = pendingTx.fees.toFiat(exchangeRates, userFiat),
            selectedLevel = pendingTx.feeLevel,
            availableLevels = AVAILABLE_FEE_OPTIONS,
            feeInfo = buildFeeInfo(pendingTx),
            asset = sourceAccount.asset
        )

    private fun buildFeeInfo(pendingTx: PendingTx): TxConfirmationValue.FeeSelection.FeeInfo =
        TxConfirmationValue.FeeSelection.FeeInfo(
            regularFee = pendingTx.feeOptions.regularFee,
            priorityFee = pendingTx.feeOptions.priorityFee
        )

    // Returns true if bitcoin transaction is large by checking against 3 criteria:
    //  * If the fee > $0.50 AND
    //  * the Tx size is over 1kB AND
    //  * the ratio of fee/amount is over 1%
    private fun isLargeTransaction(pendingTx: PendingTx): Boolean {
        val fiatValue = pendingTx.fees.toFiat(exchangeRates, LARGE_TX_FIAT)

        val txSize = sendDataManager.estimateSize(
            inputs = pendingTx.utxoBundle.spendableOutputs.size,
            outputs = 2 // assumes change required
        )

        val relativeFee = BigDecimal(100) * (pendingTx.fees.toBigDecimal() / pendingTx.amount.toBigDecimal())
        return fiatValue.toBigDecimal() > BigDecimal(LARGE_TX_FEE) &&
            txSize > LARGE_TX_SIZE &&
            relativeFee > LARGE_TX_PERCENTAGE
    }

    override fun doValidateAll(pendingTx: PendingTx): Single<PendingTx> =
        validateAddress()
            .then { validateAmounts(pendingTx) }
            .then { validateSufficientFunds(pendingTx) }
            .then { validateOptions(pendingTx) }
            .logValidityFailure()
            .updateTxValidity(pendingTx)

    private fun validateAddress(): Completable =
        Completable.fromCallable {
            if (!FormatsUtil.isValidBitcoinAddress(btcNetworkParams, btcTarget.address)) {
                throw TxValidationFailure(ValidationState.INVALID_ADDRESS)
            }
        }

    private fun validateAmounts(pendingTx: PendingTx): Completable =
        Completable.fromCallable {
            val amount = pendingTx.amount.toBigInteger()
            if (amount < Payment.DUST) {
                throw TxValidationFailure(ValidationState.INVALID_AMOUNT)
            }

            if (amount > MAX_BTC_AMOUNT) {
                throw TxValidationFailure(ValidationState.INVALID_AMOUNT)
            }

            if (amount <= BigInteger.ZERO) {
                throw TxValidationFailure(ValidationState.INVALID_AMOUNT)
            }
        }

    private fun validateSufficientFunds(pendingTx: PendingTx): Completable =
        Completable.fromCallable {
            if (pendingTx.availableBalance < pendingTx.amount) {
                throw TxValidationFailure(ValidationState.INSUFFICIENT_FUNDS)
            }

            if (pendingTx.utxoBundle.spendableOutputs.isEmpty()) {
                throw TxValidationFailure(ValidationState.INSUFFICIENT_FUNDS)
            }
        }

    private fun validateOptions(pendingTx: PendingTx): Completable =
        Completable.fromCallable {
            // If the large_fee warning is present, make sure it's ack'd.
            // If it's not, then there's nothing to do
            if (pendingTx.getOption<TxConfirmationValue.TxBooleanConfirmation<Unit>>(
                    TxConfirmation.LARGE_TRANSACTION_WARNING)?.value == false
            ) {
                throw TxValidationFailure(ValidationState.OPTION_INVALID)
            }

            if (pendingTx.feeLevel == FeeLevel.Custom) {
                when {
                    pendingTx.customFeeAmount == -1L -> throw TxValidationFailure(ValidationState.INVALID_AMOUNT)
                    pendingTx.customFeeAmount < MINIMUM_CUSTOM_FEE -> throw TxValidationFailure(
                        ValidationState.UNDER_MIN_LIMIT)
                }
            }
        }

    override fun doExecute(pendingTx: PendingTx, secondPassword: String): Single<TxResult> =
        doPrepareTransaction(pendingTx, secondPassword)
            .flatMap { engineTx ->
                val btcTx = engineTx as BtcPreparedTx
                sendDataManager.submitBtcPayment(btcTx.btcTx)
            }.doOnSuccess {
                doOnTransactionSuccess(pendingTx)
            }.doOnError { e ->
                doOnTransactionFailed(pendingTx, e)
            }.onErrorResumeNext {
                Single.error(TransactionError.ExecutionFailed)
            }.map {
                TxResult.HashedTxResult(it, pendingTx.amount)
            }

    override fun doPrepareTransaction(
        pendingTx: PendingTx,
        secondPassword: String
    ): Single<EngineTransaction> =
        Singles.zip(
            btcSource.getChangeAddress(),
            btcSource.getSigningKeys(pendingTx.utxoBundle, secondPassword)
        ).map { (changeAddress, keys) ->
            BtcPreparedTx(
                sendDataManager.createAndSignBtcTransaction(
                    pendingTx.utxoBundle,
                    keys,
                    btcTarget.address,
                    changeAddress,
                    pendingTx.fees.toBigInteger(),
                    pendingTx.amount.toBigInteger()
                )
            )
        }

    override fun doOnTransactionSuccess(pendingTx: PendingTx) {
        btcSource.incrementReceiveAddress()
        updateInternalBtcBalances(pendingTx)
    }

    override fun doOnTransactionFailed(pendingTx: PendingTx, e: Throwable) {
        Timber.e("BTC Send failed: $e")
        crashLogger.logException(e)
    }

    // Update balance immediately after spend - until refresh from server
    private fun updateInternalBtcBalances(pendingTx: PendingTx) {
        try {
            val totalSent = pendingTx.totalSent.toBigInteger()
            val address = btcSource.xpubAddress
            btcDataManager.subtractAmountFromAddressBalance(
                address,
                totalSent.toLong()
            )
        } catch (e: Exception) {
            Timber.e(e)
        }
    }

    override fun doPostExecute(txResult: TxResult): Completable =
        super.doPostExecute(txResult)
            .doOnComplete { btcSource.forceRefresh() }

    companion object {
        const val LARGE_TX_FIAT = "USD"
        const val LARGE_TX_FEE = 0.5
        const val LARGE_TX_SIZE = 1024
        val LARGE_TX_PERCENTAGE = BigDecimal(1.0)

        private val AVAILABLE_FEE_OPTIONS = setOf(FeeLevel.Regular, FeeLevel.Priority, FeeLevel.Custom)
        private val MAX_BTC_AMOUNT = 2_100_000_000_000_000L.toBigInteger()
    }

    // TEMP diagnostics - TODO Remove this once we're stable
    private val crashLogger: CrashLogger by inject()

    private fun Completable.logValidityFailure(): Completable =
        this.doOnError { crashLogger.logException(it) }

    private fun fatalError(e: Throwable): Throwable {
        crashLogger.logException(e)
        return e
    }
}

private val PendingTx.totalSent: Money
    get() = amount + fees
