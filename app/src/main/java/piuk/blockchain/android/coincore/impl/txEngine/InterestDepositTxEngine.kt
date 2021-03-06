package piuk.blockchain.android.coincore.impl.txEngine

import com.blockchain.nabu.datamanagers.CustodialWalletManager
import info.blockchain.balance.Money
import io.reactivex.Completable
import io.reactivex.Single
import piuk.blockchain.android.coincore.CryptoAccount
import piuk.blockchain.android.coincore.FeeLevel
import piuk.blockchain.android.coincore.PendingTx
import piuk.blockchain.android.coincore.ReceiveAddress
import piuk.blockchain.android.coincore.TransactionTarget
import piuk.blockchain.android.coincore.TxConfirmation
import piuk.blockchain.android.coincore.TxConfirmationValue
import piuk.blockchain.android.coincore.TxEngine
import piuk.blockchain.android.coincore.TxFee
import piuk.blockchain.android.coincore.TxResult
import piuk.blockchain.android.coincore.ValidationState
import piuk.blockchain.android.coincore.impl.CryptoNonCustodialAccount
import piuk.blockchain.androidcore.data.exchangerate.ExchangeRateDataManager

class InterestDepositTxEngine(
    private val walletManager: CustodialWalletManager,
    private val onChainEngine: OnChainTxEngineBase
) : TxEngine() {

    override fun assertInputsValid() {
        check(sourceAccount is CryptoNonCustodialAccount)
        check(txTarget is ReceiveAddress)

        // TODO: Re-enable this once start() has been refactored to be Completable
        // We have to pass the receiveAddress here cause we need to start the onchain engine with that
        // and so we need a way to get the receiveAddress from the CryptoInterestAccount.
        // This will be possible when start() returns a completable
        // check(sourceAccount.asset == (txTarget as CryptoInterestAccount).asset)
        // check(txTarget is CryptoInterestAccount)
        // onChainEngine.assertInputsValid()
    }

    override fun start(
        sourceAccount: CryptoAccount,
        txTarget: TransactionTarget,
        exchangeRates: ExchangeRateDataManager,
        refreshTrigger: RefreshTrigger
    ) {
        super.start(sourceAccount, txTarget, exchangeRates, refreshTrigger)
        onChainEngine.start(sourceAccount, txTarget, exchangeRates, refreshTrigger)
    }

    override fun doInitialiseTx(): Single<PendingTx> =
        onChainEngine.doInitialiseTx()
            .flatMap { pendingTx ->
                walletManager.getInterestLimits(asset)
                    .toSingle()
                    .map {
                        pendingTx.copy(
                            minLimit = it.minDepositAmount,
                            feeLevel = FeeLevel.Priority,
                            availableFeeLevels = AVAILABLE_FEE_LEVELS
                        )
                    }
                }

    override fun doUpdateAmount(amount: Money, pendingTx: PendingTx): Single<PendingTx> =
        onChainEngine.doUpdateAmount(amount, pendingTx)

    override fun doUpdateFeeLevel(
        pendingTx: PendingTx,
        level: FeeLevel,
        customFeeAmount: Long
    ): Single<PendingTx> {
        require(pendingTx.availableFeeLevels.contains(level))
        return Single.just(pendingTx)
    }

    override fun doBuildConfirmations(pendingTx: PendingTx): Single<PendingTx> =
        onChainEngine.doBuildConfirmations(pendingTx).map { pTx ->
            modifyEngineConfirmations(pTx)
        }.flatMap {
            if (it.hasOption(TxConfirmation.MEMO)) {
                it.getOption<TxConfirmationValue.Memo>(TxConfirmation.MEMO)?.let { memo ->
                    onChainEngine.doOptionUpdateRequest(it, memo.copy(editable = false))
                }
            } else {
                Single.just(it)
            }
        }

    private fun modifyEngineConfirmations(
        pendingTx: PendingTx,
        termsChecked: Boolean = false,
        agreementChecked: Boolean = false
    ): PendingTx =
        pendingTx.removeOption(TxConfirmation.DESCRIPTION)
            .removeOption(TxConfirmation.FEE_SELECTION)
            .addOrReplaceOption(
                TxConfirmationValue.NetworkFee(
                    txFee = TxFee(
                        pendingTx.fees,
                        TxFee.FeeType.DEPOSIT_FEE,
                        sourceAccount.asset
                    )
                )
            )
            .addOrReplaceOption(
                TxConfirmationValue.TxBooleanConfirmation<Unit>(
                    confirmation = TxConfirmation.AGREEMENT_INTEREST_T_AND_C,
                    value = termsChecked
                )
            )
            .addOrReplaceOption(
                TxConfirmationValue.TxBooleanConfirmation(
                    confirmation = TxConfirmation.AGREEMENT_INTEREST_TRANSFER,
                    data = pendingTx.amount,
                    value = agreementChecked
                )
            )

    override fun doOptionUpdateRequest(pendingTx: PendingTx, newConfirmation: TxConfirmationValue): Single<PendingTx> =
        if (newConfirmation.confirmation in setOf(
                TxConfirmation.AGREEMENT_INTEREST_T_AND_C,
                TxConfirmation.AGREEMENT_INTEREST_TRANSFER
            )
        ) {
            Single.just(pendingTx.addOrReplaceOption(newConfirmation))
        } else {
            onChainEngine.doOptionUpdateRequest(pendingTx, newConfirmation)
                .map { pTx ->
                    modifyEngineConfirmations(
                        pendingTx = pTx,
                        termsChecked = getTermsOptionValue(pendingTx),
                        agreementChecked = getAgreementOptionValue(pendingTx)
                    )
                }
        }

    override fun doValidateAmount(pendingTx: PendingTx): Single<PendingTx> =
        onChainEngine.doValidateAmount(pendingTx)
            .map {
                if (it.amount.isPositive && it.amount < it.minLimit!!) {
                    it.copy(validationState = ValidationState.UNDER_MIN_LIMIT)
                } else {
                    it
                }
            }

    override fun doValidateAll(pendingTx: PendingTx): Single<PendingTx> =
        onChainEngine.doValidateAll(pendingTx)
            .map {
                if (it.validationState == ValidationState.CAN_EXECUTE && !areOptionsValid(pendingTx)) {
                    it.copy(validationState = ValidationState.OPTION_INVALID)
                } else {
                    it
                }
            }

    private fun areOptionsValid(pendingTx: PendingTx): Boolean {
        val terms = getTermsOptionValue(pendingTx)
        val agreement = getAgreementOptionValue(pendingTx)
        return (terms && agreement)
    }

    private fun getTermsOptionValue(pendingTx: PendingTx): Boolean =
        pendingTx.getOption<TxConfirmationValue.TxBooleanConfirmation<Unit>>(
            TxConfirmation.AGREEMENT_INTEREST_T_AND_C
        )?.value ?: false

    private fun getAgreementOptionValue(pendingTx: PendingTx): Boolean =
        pendingTx.getOption<TxConfirmationValue.TxBooleanConfirmation<Money>>(
            TxConfirmation.AGREEMENT_INTEREST_TRANSFER
        )?.value ?: false

    override fun doExecute(pendingTx: PendingTx, secondPassword: String): Single<TxResult> =
        onChainEngine.doExecute(pendingTx, secondPassword)

    override fun doPostExecute(txResult: TxResult): Completable = txTarget.onTxCompleted(txResult)

    companion object {
        private val AVAILABLE_FEE_LEVELS = setOf(FeeLevel.Priority)
    }
}
