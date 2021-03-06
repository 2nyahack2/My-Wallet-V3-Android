package piuk.blockchain.android.coincore.impl.txEngine.swap

import com.blockchain.nabu.datamanagers.CustodialWalletManager
import com.blockchain.nabu.datamanagers.TransferDirection
import com.blockchain.nabu.service.TierService
import info.blockchain.balance.CryptoValue
import info.blockchain.balance.Money
import io.reactivex.Single
import piuk.blockchain.android.coincore.CryptoAccount
import piuk.blockchain.android.coincore.FeeLevel
import piuk.blockchain.android.coincore.PendingTx
import piuk.blockchain.android.coincore.TxResult
import piuk.blockchain.android.coincore.ValidationState
import piuk.blockchain.android.coincore.impl.CryptoNonCustodialAccount
import piuk.blockchain.android.coincore.impl.CustodialTradingAccount
import piuk.blockchain.android.coincore.impl.txEngine.OnChainTxEngineBase
import piuk.blockchain.android.coincore.impl.txEngine.TransferQuotesEngine
import piuk.blockchain.android.coincore.updateTxValidity
import piuk.blockchain.androidcore.data.api.EnvironmentConfig
import piuk.blockchain.androidcore.utils.helperfunctions.unsafeLazy
import java.lang.IllegalStateException

class OnChainSwapTxEngine(
    quotesEngine: TransferQuotesEngine,
    walletManager: CustodialWalletManager,
    kycTierService: TierService,
    private val engine: OnChainTxEngineBase,
    environmentConfig: EnvironmentConfig
) : SwapTxEngineBase(
    quotesEngine, walletManager, kycTierService, environmentConfig
) {
    override val direction: TransferDirection by unsafeLazy {
        when (txTarget) {
            is CustodialTradingAccount -> TransferDirection.FROM_USERKEY
            is CryptoNonCustodialAccount -> TransferDirection.ON_CHAIN
            else -> throw IllegalStateException("Illegal target for on-chain swap engine")
        }
    }

    override val availableBalance: Single<Money>
        get() = sourceAccount.accountBalance

    override fun assertInputsValid() {
        check(sourceAccount is CryptoNonCustodialAccount)
        if (direction == TransferDirection.ON_CHAIN) {
            check(txTarget is CryptoNonCustodialAccount)
        } else {
            check(txTarget is CustodialTradingAccount)
        }
        check(asset != (txTarget as CryptoAccount).asset)
        // TODO: Re-enable this once start() has been refactored to be Completable
//        engine.assertInputsValid()
    }

    override fun doInitialiseTx(): Single<PendingTx> =
        quotesEngine.pricedQuote
            .firstOrError()
            .doOnSuccess { pricedQuote ->
                engine.startFromQuote(pricedQuote)
            }.flatMap { quote ->
                engine.doInitialiseTx()
                    .flatMap {
                        updateLimits(it, quote)
                    }
            }.map { px ->
                px.copy(feeLevel = defaultFeeLevel(px))
            }.handlePendingOrdersError(
                PendingTx(
                    amount = CryptoValue.zero(sourceAccount.asset),
                    totalBalance = CryptoValue.zero(sourceAccount.asset),
                    availableBalance = CryptoValue.zero(sourceAccount.asset),
                    fees = CryptoValue.zero(sourceAccount.asset),
                    feeLevel = FeeLevel.Regular,
                    availableFeeLevels = setOf(FeeLevel.Regular),
                    selectedFiat = userFiat
                )
            )

    private fun defaultFeeLevel(pendingTx: PendingTx): FeeLevel =
        if (pendingTx.availableFeeLevels.contains(FeeLevel.Priority))
            FeeLevel.Priority
        else
            pendingTx.feeLevel

    override fun doUpdateAmount(amount: Money, pendingTx: PendingTx): Single<PendingTx> {
        return engine.doUpdateAmount(amount, pendingTx)
            .updateQuotePrice()
            .clearConfirmations()
    }

    override fun doUpdateFeeLevel(
        pendingTx: PendingTx,
        level: FeeLevel,
        customFeeAmount: Long
    ): Single<PendingTx> =
        engine.doUpdateFeeLevel(pendingTx, level, customFeeAmount)

    override fun doValidateAmount(pendingTx: PendingTx): Single<PendingTx> {
        return engine.doValidateAmount(pendingTx)
            .flatMap {
                if (
                    it.validationState == ValidationState.CAN_EXECUTE ||
                    it.validationState == ValidationState.INVALID_AMOUNT
                ) {
                    super.doValidateAmount(pendingTx)
                } else {
                    Single.just(it)
                }
            }.updateTxValidity(pendingTx)
    }

    override fun doValidateAll(pendingTx: PendingTx): Single<PendingTx> {
        return engine.doValidateAll(pendingTx)
            .flatMap {
                if (
                    it.validationState == ValidationState.CAN_EXECUTE ||
                    it.validationState == ValidationState.INVALID_AMOUNT
                ) {
                    super.doValidateAll(pendingTx)
                } else {
                    Single.just(it)
                }
            }.updateTxValidity(pendingTx)
    }

    override fun doExecute(pendingTx: PendingTx, secondPassword: String): Single<TxResult> =
        createOrder(pendingTx)
            .flatMap { order ->
                engine.restartFromOrder(order, pendingTx)
                    .flatMap { px ->
                        engine.doExecute(px, secondPassword)
                            .updateOrderStatus(order.id)
                    }
            }
}