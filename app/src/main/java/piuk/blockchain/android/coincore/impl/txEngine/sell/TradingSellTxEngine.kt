package piuk.blockchain.android.coincore.impl.txEngine.sell

import com.blockchain.nabu.datamanagers.CustodialWalletManager
import com.blockchain.nabu.datamanagers.TransferDirection
import com.blockchain.nabu.service.TierService
import info.blockchain.balance.CryptoValue
import info.blockchain.balance.Money
import io.reactivex.Single
import io.reactivex.rxkotlin.zipWith
import piuk.blockchain.android.coincore.FeeLevel
import piuk.blockchain.android.coincore.FiatAccount
import piuk.blockchain.android.coincore.PendingTx
import piuk.blockchain.android.coincore.TxResult
import piuk.blockchain.android.coincore.impl.CustodialTradingAccount
import piuk.blockchain.android.coincore.impl.txEngine.TransferQuotesEngine
import piuk.blockchain.androidcore.data.api.EnvironmentConfig

class TradingSellTxEngine(
    walletManager: CustodialWalletManager,
    quotesEngine: TransferQuotesEngine,
    kycTierService: TierService,
    environmentConfig: EnvironmentConfig
) : SellTxEngineBase(walletManager, kycTierService, quotesEngine, environmentConfig) {

    override val direction: TransferDirection
        get() = TransferDirection.INTERNAL

    override val availableBalance: Single<Money>
        get() = sourceAccount.accountBalance

    override fun assertInputsValid() {
        check(sourceAccount is CustodialTradingAccount)
        check(txTarget is FiatAccount)
    }

    override fun doInitialiseTx(): Single<PendingTx> =
        quotesEngine.pricedQuote.firstOrError()
            .zipWith(sourceAccount.accountBalance).flatMap { (quote, balance) ->
                Single.just(
                    PendingTx(
                        amount = CryptoValue.zero(asset),
                        totalBalance = balance,
                        availableBalance = balance,
                        fees = CryptoValue.zero(asset),
                        selectedFiat = userFiat,
                        feeLevel = FeeLevel.None,
                        availableFeeLevels = AVAILABLE_FEE_LEVELS
                    )
                ).flatMap {
                    updateLimits(it, quote)
                }
            }.handlePendingOrdersError(
                PendingTx(
                    amount = CryptoValue.zero(asset),
                    totalBalance = CryptoValue.zero(asset),
                    availableBalance = CryptoValue.zero(asset),
                    fees = CryptoValue.zero(asset),
                    selectedFiat = userFiat,
                    feeLevel = FeeLevel.None
                )
            )

    override fun doUpdateAmount(amount: Money, pendingTx: PendingTx): Single<PendingTx> {
        return sourceAccount.accountBalance
            .map { it as CryptoValue }
            .map { available ->
                pendingTx.copy(
                    amount = amount,
                    availableBalance = available,
                    totalBalance = available
                )
            }
            .updateQuotePrice()
            .clearConfirmations()
    }

    override fun doUpdateFeeLevel(
        pendingTx: PendingTx,
        level: FeeLevel,
        customFeeAmount: Long
    ): Single<PendingTx> {
        require(pendingTx.availableFeeLevels.contains(level))
        // This engine only supports FeeLevel.None, so
        return Single.just(pendingTx)
    }

    override val requireSecondPassword: Boolean
        get() = false

    override fun doExecute(pendingTx: PendingTx, secondPassword: String): Single<TxResult> =
        createSellOrder(pendingTx).map {
            TxResult.UnHashedTxResult(pendingTx.amount)
        }

    companion object {
        private val AVAILABLE_FEE_LEVELS = setOf(FeeLevel.None)
    }
}