package piuk.blockchain.android.coincore.impl.txEngine

import androidx.annotation.CallSuper
import com.blockchain.preferences.WalletStatus
import info.blockchain.balance.CryptoCurrency
import info.blockchain.wallet.api.data.FeeOptions
import io.reactivex.Completable
import io.reactivex.Single
import piuk.blockchain.android.coincore.CryptoAddress
import piuk.blockchain.android.coincore.FeeState
import piuk.blockchain.android.coincore.FeeLevel
import piuk.blockchain.android.coincore.PendingTx
import piuk.blockchain.android.coincore.TxConfirmationValue
import piuk.blockchain.android.coincore.TxEngine
import piuk.blockchain.android.coincore.TxResult

abstract class OnChainTxEngineBase(
    override val requireSecondPassword: Boolean,
    private val walletPreferences: WalletStatus
) : TxEngine() {

    override fun assertInputsValid() {
        val tgt = txTarget
        check(tgt is CryptoAddress)
        check(tgt.address.isNotEmpty())
        check(sourceAccount.asset == tgt.asset)
    }

    override fun doPostExecute(txResult: TxResult): Completable =
        txTarget.onTxCompleted(txResult)

    protected fun mapSavedFeeToFeeLevel(feeType: Int?): FeeLevel =
        when (feeType) {
            FeeLevel.Priority.ordinal -> FeeLevel.Priority
            FeeLevel.Regular.ordinal -> FeeLevel.Regular
            else -> FeeLevel.Regular
        }

    private fun FeeLevel.mapFeeLevelToSavedValue() =
        this.ordinal

    private fun storeDefaultFeeLevel(cryptoCurrency: CryptoCurrency, feeLevel: FeeLevel) =
        walletPreferences.setFeeTypeForAsset(cryptoCurrency, feeLevel.mapFeeLevelToSavedValue())

    protected fun fetchDefaultFeeLevel(cryptoCurrency: CryptoCurrency): Int? =
        walletPreferences.getFeeTypeForAsset(cryptoCurrency)

    protected fun getFeeState(pTx: PendingTx, feeOptions: FeeOptions? = null) =
        if (pTx.feeLevel == FeeLevel.Custom) {
            when {
                pTx.customFeeAmount == -1L -> FeeState.ValidCustomFee
                pTx.customFeeAmount < MINIMUM_CUSTOM_FEE -> {
                    FeeState.FeeUnderMinLimit
                }
                pTx.customFeeAmount >= MINIMUM_CUSTOM_FEE &&
                    pTx.customFeeAmount <= feeOptions?.limits?.min ?: 0L -> {
                    FeeState.FeeUnderRecommended
                }
                pTx.customFeeAmount >= feeOptions?.limits?.max ?: 0L -> {
                    FeeState.FeeOverRecommended
                }
                else -> FeeState.ValidCustomFee
            }
        } else {
            if (pTx.availableBalance < pTx.amount) {
                FeeState.FeeTooHigh
            } else {
                FeeState.FeeDetails(pTx.fees)
            }
        }

    final override fun doUpdateFeeLevel(
        pendingTx: PendingTx,
        level: FeeLevel,
        customFeeAmount: Long
    ): Single<PendingTx> {
        require(pendingTx.availableFeeLevels.contains(level))

        return if (pendingTx.hasFeeLevelChanged(level, customFeeAmount)) {
            updateFeeSelection(
                asset,
                pendingTx,
                level,
                customFeeAmount
            )
        } else {
            Single.just(pendingTx)
        }
    }

    @CallSuper
    override fun doOptionUpdateRequest(pendingTx: PendingTx, newConfirmation: TxConfirmationValue): Single<PendingTx> =
        if (newConfirmation is TxConfirmationValue.FeeSelection) {
            if (pendingTx.hasFeeLevelChanged(newConfirmation.selectedLevel, newConfirmation.customFeeAmount)) {
                updateFeeSelection(
                    asset,
                    pendingTx,
                    newConfirmation.selectedLevel,
                    newConfirmation.customFeeAmount
                )
                .flatMap { pTx -> doValidateAmount(pTx) }
                .flatMap { pTx -> doBuildConfirmations(pTx) }
            } else {
                super.doOptionUpdateRequest(pendingTx, makeFeeSelectionOption(pendingTx))
            }
        } else {
            super.doOptionUpdateRequest(pendingTx, newConfirmation)
        }

    protected abstract fun makeFeeSelectionOption(pendingTx: PendingTx): TxConfirmationValue.FeeSelection

    private fun updateFeeSelection(
        cryptoCurrency: CryptoCurrency,
        pendingTx: PendingTx,
        newFeeLevel: FeeLevel,
        customFeeAmount: Long = -1
    ): Single<PendingTx> {
        storeDefaultFeeLevel(cryptoCurrency, newFeeLevel)

        return doUpdateAmount(
            amount = pendingTx.amount,
            pendingTx = pendingTx.copy(
                feeLevel = newFeeLevel,
                customFeeAmount = customFeeAmount
            )
        )
    }

    private fun PendingTx.hasFeeLevelChanged(newLevel: FeeLevel, newAmount: Long) =
        feeLevel != newLevel || (feeLevel == FeeLevel.Custom && newAmount != customFeeAmount)

    companion object {
        const val MINIMUM_CUSTOM_FEE = 1L
    }
}
