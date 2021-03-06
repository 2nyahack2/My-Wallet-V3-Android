package piuk.blockchain.android.coincore.bch

import com.blockchain.nabu.datamanagers.TransactionError
import com.blockchain.preferences.WalletStatus
import info.blockchain.api.data.UnspentOutputs
import info.blockchain.balance.CryptoCurrency
import info.blockchain.balance.CryptoValue
import info.blockchain.balance.Money
import info.blockchain.wallet.exceptions.HDWalletException
import info.blockchain.wallet.payment.Payment
import info.blockchain.wallet.payment.SpendableUnspentOutputs
import info.blockchain.wallet.util.FormatsUtil
import io.reactivex.Completable
import io.reactivex.Single
import io.reactivex.rxkotlin.Singles
import org.bitcoinj.core.ECKey
import org.bitcoinj.core.NetworkParameters
import piuk.blockchain.android.coincore.CryptoAddress
import piuk.blockchain.android.coincore.FeeLevel
import piuk.blockchain.android.coincore.PendingTx
import piuk.blockchain.android.coincore.TxConfirmationValue
import piuk.blockchain.android.coincore.TxResult
import piuk.blockchain.android.coincore.TxValidationFailure
import piuk.blockchain.android.coincore.ValidationState
import piuk.blockchain.android.coincore.copyAndPut
import piuk.blockchain.android.coincore.impl.txEngine.OnChainTxEngineBase
import piuk.blockchain.android.coincore.updateTxValidity
import piuk.blockchain.androidcore.data.bitcoincash.BchDataManager
import piuk.blockchain.androidcore.data.fees.FeeDataManager
import piuk.blockchain.androidcore.data.payload.PayloadDataManager
import piuk.blockchain.androidcore.data.payments.SendDataManager
import piuk.blockchain.androidcore.utils.extensions.then
import piuk.blockchain.androidcore.utils.helperfunctions.unsafeLazy
import timber.log.Timber
import java.math.BigInteger

private const val STATE_UTXO = "bch_utxo"

private val PendingTx.unspentOutputBundle: SpendableUnspentOutputs
    get() = (this.engineState[STATE_UTXO] as SpendableUnspentOutputs)

class BchOnChainTxEngine(
    private val bchDataManager: BchDataManager,
    private val payloadDataManager: PayloadDataManager,
    private val sendDataManager: SendDataManager,
    private val feeManager: FeeDataManager,
    private val networkParams: NetworkParameters,
    walletPreferences: WalletStatus,
    requireSecondPassword: Boolean
) : OnChainTxEngineBase(
    requireSecondPassword,
    walletPreferences
) {

    private val bchSource: BchCryptoWalletAccount by unsafeLazy {
        sourceAccount as BchCryptoWalletAccount
    }

    private val bchTarget: CryptoAddress
        get() = txTarget as CryptoAddress

    override fun assertInputsValid() {
        check(txTarget is CryptoAddress)
        check((txTarget as CryptoAddress).asset == CryptoCurrency.BCH)
        check(asset == CryptoCurrency.BCH)
    }

    override fun doInitialiseTx(): Single<PendingTx> =
        Single.just(
            PendingTx(
                amount = CryptoValue.zero(CryptoCurrency.BCH),
                totalBalance = CryptoValue.zero(CryptoCurrency.BCH),
                availableBalance = CryptoValue.zero(CryptoCurrency.BCH),
                fees = CryptoValue.zero(CryptoCurrency.BCH),
                feeLevel = FeeLevel.Regular,
                availableFeeLevels = AVAILABLE_FEE_LEVELS,
                selectedFiat = userFiat
            )
        )

    override fun doUpdateAmount(amount: Money, pendingTx: PendingTx): Single<PendingTx> {
        require(amount is CryptoValue)
        require(amount.currency == CryptoCurrency.BCH)

        return Singles.zip(
            sourceAccount.accountBalance.map { it as CryptoValue },
            getUnspentApiResponse(bchSource.xpubAddress),
            getDynamicFeePerKb(pendingTx)
        ) { balance, coins, feePerKb ->
            updatePendingTx(amount, balance, pendingTx, feePerKb, coins)
        }.onErrorReturn {
            pendingTx.copy(
                validationState = ValidationState.INSUFFICIENT_FUNDS
            )
        }
    }

    private fun getUnspentApiResponse(address: String): Single<UnspentOutputs> =
        if (bchDataManager.getAddressBalance(address) > CryptoValue.zero(asset)) {
            sendDataManager.getUnspentBchOutputs(address)
                // If we get here, we should have balance and valid UTXOs. IF we don't, then, um... we'd best fail hard
                .map { utxo ->
                    if (utxo.unspentOutputs.isEmpty()) {
                        Timber.e("No BTC UTXOs found for non-zero balance!")
                        throw IllegalStateException("No BTC UTXOs found for non-zero balance")
                    } else {
                        utxo
                    }
                }.singleOrError()
        } else {
            Single.error(Throwable("No BCH funds"))
        }

    private fun updatePendingTx(
        amount: CryptoValue,
        balance: CryptoValue,
        pendingTx: PendingTx,
        feePerKb: CryptoValue,
        coins: UnspentOutputs
    ): PendingTx {
        val maxAvailable = sendDataManager.getMaximumAvailable(
            cryptoCurrency = CryptoCurrency.BCH,
            unspentCoins = coins,
            feePerKb = feePerKb
        )

        val unspentOutputs = sendDataManager.getSpendableCoins(
            unspentCoins = coins,
            paymentAmount = amount,
            feePerKb = feePerKb
        )

        return pendingTx.copy(
            amount = amount,
            totalBalance = balance,
            availableBalance = maxAvailable,
            fees = CryptoValue.fromMinor(CryptoCurrency.BCH, unspentOutputs.absoluteFee),
            engineState = pendingTx.engineState.copyAndPut(STATE_UTXO, unspentOutputs)
        )
    }

    private fun getDynamicFeePerKb(pendingTx: PendingTx): Single<CryptoValue> =
        feeManager.bchFeeOptions
            .map { feeOptions ->
                check(pendingTx.feeLevel == FeeLevel.Regular) {
                    "Fee level ${pendingTx.feeLevel} is not supported by BCH"
                }
                feeToCrypto(feeOptions.regularFee)
            }.singleOrError()

    private fun feeToCrypto(feePerKb: Long): CryptoValue =
        CryptoValue.fromMinor(CryptoCurrency.BCH, (feePerKb * 1000).toBigInteger())

    override fun doValidateAmount(pendingTx: PendingTx): Single<PendingTx> =
        validateAmounts(pendingTx)
            .then { validateSufficientFunds(pendingTx) }
            .updateTxValidity(pendingTx)

    private fun validateAmounts(pendingTx: PendingTx): Completable =
        Completable.fromCallable {
            val amount = pendingTx.amount.toBigInteger()
            if (amount < Payment.DUST || amount > MAX_BCH_AMOUNT || amount <= BigInteger.ZERO) {
                throw TxValidationFailure(ValidationState.INVALID_AMOUNT)
            }
        }

    private fun validateSufficientFunds(pendingTx: PendingTx): Completable =
        Completable.fromCallable {
            if (!pendingTx.hasSufficientFunds()) {
                throw TxValidationFailure(ValidationState.INSUFFICIENT_FUNDS)
            }
        }

    private fun PendingTx.hasSufficientFunds() =
        availableBalance >= amount && unspentOutputBundle.spendableOutputs.isNotEmpty()

    override fun doBuildConfirmations(pendingTx: PendingTx): Single<PendingTx> =
        Single.just(
            pendingTx.copy(
                confirmations = listOf(
                    TxConfirmationValue.From(from = sourceAccount.label),
                    TxConfirmationValue.To(to = txTarget.label),
                    makeFeeSelectionOption(pendingTx),
                    TxConfirmationValue.FeedTotal(
                        amount = pendingTx.amount,
                        fee = pendingTx.fees,
                        exchangeFee = pendingTx.fees.toFiat(exchangeRates, userFiat),
                        exchangeAmount = pendingTx.amount.toFiat(exchangeRates, userFiat)
                    )
                )
            )
        )

    override fun makeFeeSelectionOption(pendingTx: PendingTx): TxConfirmationValue.FeeSelection =
        TxConfirmationValue.FeeSelection(
            feeDetails = getFeeState(pendingTx),
            exchange = pendingTx.fees.toFiat(exchangeRates, userFiat),
            selectedLevel = pendingTx.feeLevel,
            availableLevels = AVAILABLE_FEE_LEVELS,
            asset = sourceAccount.asset
        )

    override fun doValidateAll(pendingTx: PendingTx): Single<PendingTx> =
        validateAddress()
            .then { validateAmounts(pendingTx) }
            .then { validateSufficientFunds(pendingTx) }
            .updateTxValidity(pendingTx)

    private fun validateAddress() =
        Completable.fromCallable {
            if (!FormatsUtil.isValidBCHAddress(networkParams, bchTarget.address) &&
                !FormatsUtil.isValidBitcoinAddress(networkParams, bchTarget.address)
            ) {
                throw TxValidationFailure(ValidationState.INVALID_ADDRESS)
            }
        }

    override fun doExecute(pendingTx: PendingTx, secondPassword: String): Single<TxResult> =
        Singles.zip(
            getBchChangeAddress(),
            getBchKeys(pendingTx, secondPassword)
        ).flatMap { (changeAddress, keys) ->
            sendDataManager.submitBchPayment(
                pendingTx.unspentOutputBundle,
                keys,
                getFullBitcoinCashAddressFormat(bchTarget.address),
                changeAddress,
                pendingTx.fees.toBigInteger(),
                pendingTx.amount.toBigInteger()
            ).singleOrError()
        }.doOnSuccess {
            // logPaymentSentEvent(true, CryptoCurrency.BCH, pendingTransaction.bigIntAmount)
            incrementBchReceiveAddress(pendingTx)
        }.doOnError { e ->
            Timber.e("BCH Send failed: $e")
            // logPaymentSentEvent(false, BCH.BTC, pendingTransaction.bigIntAmount)
        }.onErrorResumeNext {
            Single.error(TransactionError.ExecutionFailed)
        }.map {
            TxResult.HashedTxResult(it, pendingTx.amount)
        }

    private fun getFullBitcoinCashAddressFormat(cashAddress: String): String {
        return if (!cashAddress.startsWith(networkParams.bech32AddressPrefix) &&
            FormatsUtil.isValidBCHAddress(networkParams, cashAddress)
        ) {
            networkParams.bech32AddressPrefix + networkParams.bech32AddressSeparator.toChar() + cashAddress
        } else {
            cashAddress
        }
    }

    private fun getBchChangeAddress(): Single<String> {
        val position =
            bchDataManager.getAccountMetadataList().indexOfFirst { it.xpub == bchSource.xpubAddress }
        return bchDataManager.getNextChangeCashAddress(position).singleOrError()
    }

    private fun getBchKeys(pendingTx: PendingTx, secondPassword: String): Single<List<ECKey>> {
        if (payloadDataManager.isDoubleEncrypted) {
            payloadDataManager.decryptHDWallet(secondPassword)
            bchDataManager.decryptWatchOnlyWallet(payloadDataManager.mnemonic)
        }

        val hdAccountList = bchDataManager.getAccountList()
        val acc = hdAccountList.find {
            it.node.serializePubB58(networkParams) == bchSource.xpubAddress
        } ?: throw HDWalletException("No matching private key found for ${bchSource.xpubAddress}")

        return Single.just(
            bchDataManager.getHDKeysForSigning(
                acc,
                pendingTx.unspentOutputBundle.spendableOutputs
            )
        )
    }

    private fun incrementBchReceiveAddress(pendingTx: PendingTx) {
        val xpub = bchSource.xpubAddress
        bchDataManager.incrementNextChangeAddress(xpub)
        bchDataManager.incrementNextReceiveAddress(xpub)
        updateInternalBchBalances(pendingTx, xpub)
    }

    private fun updateInternalBchBalances(pendingTx: PendingTx, xpub: String) {
        try {
            bchDataManager.subtractAmountFromAddressBalance(xpub, pendingTx.totalSent.toBigInteger())
        } catch (e: Exception) {
            Timber.e(e)
        }
    }

    override fun doPostExecute(txResult: TxResult): Completable =
        super.doPostExecute(txResult)
            .doOnComplete { bchSource.forceRefresh() }

    companion object {
        private val AVAILABLE_FEE_LEVELS = setOf(FeeLevel.Regular)
        private val MAX_BCH_AMOUNT = 2_100_000_000_000_000L.toBigInteger()
    }
}

private val PendingTx.totalSent: Money
    get() = amount + fees