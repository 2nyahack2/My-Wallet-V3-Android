package piuk.blockchain.android.coincore.impl.txEngine.sell

import com.blockchain.nabu.datamanagers.CustodialWalletManager
import com.blockchain.nabu.datamanagers.TransferDirection
import com.blockchain.nabu.service.TierService
import info.blockchain.balance.CryptoValue
import info.blockchain.balance.Money
import io.reactivex.Single
import piuk.blockchain.android.coincore.FeeLevel
import piuk.blockchain.android.coincore.FiatAccount
import piuk.blockchain.android.coincore.PendingTx
import piuk.blockchain.android.coincore.TxResult
import piuk.blockchain.android.coincore.ValidationState
import piuk.blockchain.android.coincore.impl.CryptoNonCustodialAccount
import piuk.blockchain.android.coincore.impl.txEngine.OnChainTxEngineBase
import piuk.blockchain.android.coincore.impl.txEngine.TransferQuotesEngine
import piuk.blockchain.android.coincore.updateTxValidity
import piuk.blockchain.androidcore.data.api.EnvironmentConfig

class OnChainSellTxEngine(
    private val engine: OnChainTxEngineBase,
    environmentConfig: EnvironmentConfig,
    walletManager: CustodialWalletManager,
    kycTierService: TierService,
    quotesEngine: TransferQuotesEngine
) : SellTxEngineBase(
    walletManager, kycTierService, quotesEngine, environmentConfig
) {
    override val direction: TransferDirection
        get() = TransferDirection.FROM_USERKEY

    override val availableBalance: Single<Money>
        get() = sourceAccount.accountBalance

    override fun assertInputsValid() {
        check(sourceAccount is CryptoNonCustodialAccount)
        check(txTarget is FiatAccount)
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
                px.copy(
                    feeLevel = defaultFeeLevel(px),
                    selectedFiat = userFiat
                )
            }.handlePendingOrdersError(
                PendingTx(
                    amount = CryptoValue.zero(asset),
                    totalBalance = CryptoValue.zero(asset),
                    availableBalance = CryptoValue.zero(asset),
                    fees = CryptoValue.zero(asset),
                    selectedFiat = userFiat,
                    feeLevel = FeeLevel.Regular,
                    availableFeeLevels = setOf(FeeLevel.Regular)
                )
            )

    private fun defaultFeeLevel(pendingTx: PendingTx): FeeLevel =
        if (pendingTx.availableFeeLevels.contains(FeeLevel.Priority))
            FeeLevel.Priority
        else
            pendingTx.feeLevel

    override fun doValidateAmount(pendingTx: PendingTx): Single<PendingTx> =
        engine.doValidateAmount(pendingTx)
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

    override fun doValidateAll(pendingTx: PendingTx): Single<PendingTx> =
        engine.doValidateAll(pendingTx)
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

    override fun doUpdateAmount(amount: Money, pendingTx: PendingTx): Single<PendingTx> =
        engine.doUpdateAmount(amount, pendingTx)
            .updateQuotePrice()
            .clearConfirmations()

    override fun doUpdateFeeLevel(
        pendingTx: PendingTx,
        level: FeeLevel,
        customFeeAmount: Long
    ): Single<PendingTx> {
        return engine.doUpdateFeeLevel(pendingTx, level, customFeeAmount)
    }

    override fun doExecute(pendingTx: PendingTx, secondPassword: String): Single<TxResult> =
        createSellOrder(pendingTx)
            .flatMap { order ->
                engine.restartFromOrder(order, pendingTx)
                    .flatMap { px ->
                        engine.doExecute(px, secondPassword).updateOrderStatus(order.id)
                    }
            }
}