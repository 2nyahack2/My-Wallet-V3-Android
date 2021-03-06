package piuk.blockchain.android.coincore.impl.txEngine.swap

import com.blockchain.nabu.datamanagers.CustodialWalletManager
import com.blockchain.nabu.datamanagers.TransferDirection
import com.blockchain.nabu.service.TierService
import info.blockchain.balance.CryptoValue
import info.blockchain.balance.Money
import io.reactivex.Single
import piuk.blockchain.android.coincore.FeeLevel
import piuk.blockchain.android.coincore.PendingTx
import piuk.blockchain.android.coincore.TxResult
import piuk.blockchain.android.coincore.impl.CustodialTradingAccount
import piuk.blockchain.android.coincore.impl.txEngine.TransferQuotesEngine
import piuk.blockchain.androidcore.data.api.EnvironmentConfig

class TradingToTradingSwapTxEngine(
    walletManager: CustodialWalletManager,
    quotesEngine: TransferQuotesEngine,
    kycTierService: TierService,
    environmentConfig: EnvironmentConfig
) : SwapTxEngineBase(quotesEngine, walletManager, kycTierService, environmentConfig) {

    override val availableBalance: Single<Money>
        get() = sourceAccount.accountBalance

    override fun assertInputsValid() {
        check(txTarget is CustodialTradingAccount)
        check(sourceAccount is CustodialTradingAccount)
        check((txTarget as CustodialTradingAccount).asset != sourceAccount.asset)
    }

    override fun doInitialiseTx(): Single<PendingTx> =
        quotesEngine.pricedQuote.firstOrError()
            .flatMap { pricedQuote ->
                availableBalance.flatMap { balance ->
                    Single.just(
                        PendingTx(
                            amount = CryptoValue.zero(sourceAccount.asset),
                            totalBalance = balance,
                            availableBalance = balance,
                            fees = CryptoValue.zero(sourceAccount.asset),
                            feeLevel = FeeLevel.None,
                            availableFeeLevels = AVAILABLE_FEE_LEVELS,
                            selectedFiat = userFiat
                        )
                    ).flatMap {
                        updateLimits(it, pricedQuote)
                    }
                }
            }.handlePendingOrdersError(
                PendingTx(
                    amount = CryptoValue.zero(sourceAccount.asset),
                    totalBalance = CryptoValue.zero(sourceAccount.asset),
                    availableBalance = CryptoValue.zero(sourceAccount.asset),
                    fees = CryptoValue.zero(sourceAccount.asset),
                    feeLevel = FeeLevel.None,
                    selectedFiat = userFiat
                )
            )

    override val direction: TransferDirection
        get() = TransferDirection.INTERNAL

    override fun doExecute(pendingTx: PendingTx, secondPassword: String): Single<TxResult> =
        createOrder(pendingTx).map {
            TxResult.UnHashedTxResult(pendingTx.amount)
        }

    override fun doUpdateAmount(amount: Money, pendingTx: PendingTx): Single<PendingTx> =
        availableBalance.map { balance ->
            balance as CryptoValue
        }.map { available ->
            pendingTx.copy(
                amount = amount,
                availableBalance = available,
                totalBalance = available
            )
        }.updateQuotePrice().clearConfirmations()

    override fun doUpdateFeeLevel(
        pendingTx: PendingTx,
        level: FeeLevel,
        customFeeAmount: Long
    ): Single<PendingTx> {
        require(pendingTx.availableFeeLevels.contains(level))
        // This engine only supports FeeLevel.None, so
        return Single.just(pendingTx)
    }

    companion object {
        private val AVAILABLE_FEE_LEVELS = setOf(FeeLevel.None)
    }
}
