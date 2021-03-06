package piuk.blockchain.android.coincore.impl

import com.blockchain.notifications.analytics.Analytics
import com.blockchain.preferences.WalletStatus
import com.blockchain.nabu.datamanagers.CustodialWalletManager
import com.blockchain.nabu.service.TierService
import io.reactivex.Single
import piuk.blockchain.android.coincore.AssetAction
import piuk.blockchain.android.coincore.CryptoAccount
import piuk.blockchain.android.coincore.CryptoAddress
import piuk.blockchain.android.coincore.FiatAccount
import piuk.blockchain.android.coincore.TradingAccount
import piuk.blockchain.android.coincore.TransactionProcessor
import piuk.blockchain.android.coincore.TransactionTarget
import piuk.blockchain.android.coincore.TransferError
import piuk.blockchain.android.coincore.impl.txEngine.BitpayTxEngine
import piuk.blockchain.android.coincore.impl.txEngine.sell.TradingSellTxEngine
import piuk.blockchain.android.coincore.impl.txEngine.InterestDepositTxEngine
import piuk.blockchain.android.coincore.impl.txEngine.OnChainTxEngineBase
import piuk.blockchain.android.coincore.impl.txEngine.TradingToOnChainTxEngine
import piuk.blockchain.android.coincore.impl.txEngine.TransferQuotesEngine
import piuk.blockchain.android.coincore.impl.txEngine.sell.OnChainSellTxEngine
import piuk.blockchain.android.coincore.impl.txEngine.swap.OnChainSwapTxEngine
import piuk.blockchain.android.coincore.impl.txEngine.swap.TradingToTradingSwapTxEngine
import piuk.blockchain.android.data.api.bitpay.BitPayDataManager
import piuk.blockchain.androidcore.data.api.EnvironmentConfig
import piuk.blockchain.androidcore.data.exchangerate.ExchangeRateDataManager

class TxProcessorFactory(
    private val bitPayManager: BitPayDataManager,
    private val exchangeRates: ExchangeRateDataManager,
    private val walletManager: CustodialWalletManager,
    private val walletPrefs: WalletStatus,
    private val quotesEngine: TransferQuotesEngine,
    private val analytics: Analytics,
    private val kycTierService: TierService,
    private val environmentConfig: EnvironmentConfig
) {
    fun createProcessor(
        source: CryptoAccount,
        target: TransactionTarget,
        action: AssetAction
    ): Single<TransactionProcessor> =
        when (source) {
            is CryptoNonCustodialAccount -> createOnChainProcessor(source, target, action)
            is CustodialTradingAccount -> createTradingProcessor(source, target, action)
            else -> Single.error(NotImplementedError())
        }

    private fun createOnChainProcessor(
        source: CryptoNonCustodialAccount,
        target: TransactionTarget,
        action: AssetAction
    ): Single<TransactionProcessor> {
        val engine = source.createTxEngine() as OnChainTxEngineBase

        return when (target) {
            is BitPayInvoiceTarget -> Single.just(
                TransactionProcessor(
                    exchangeRates = exchangeRates,
                    sourceAccount = source,
                    txTarget = target,
                    engine = BitpayTxEngine(
                        bitPayDataManager = bitPayManager,
                        walletPrefs = walletPrefs,
                        assetEngine = engine,
                        analytics = analytics
                    )
                )
            )
            is CryptoInterestAccount ->
                target.receiveAddress
                    .map {
                        TransactionProcessor(
                            exchangeRates = exchangeRates,
                            sourceAccount = source,
                            txTarget = it,
                            engine = InterestDepositTxEngine(
                                walletManager = walletManager,
                                onChainEngine = engine
                            )
                        )
                    }
            is CryptoAddress -> Single.just(
                TransactionProcessor(
                    exchangeRates = exchangeRates,
                    sourceAccount = source,
                    txTarget = target,
                    engine = engine
                )
            )
            is CryptoAccount ->
                if (action != AssetAction.Swap)
                    target.receiveAddress.map {
                        TransactionProcessor(
                        exchangeRates = exchangeRates,
                        sourceAccount = source,
                        txTarget = it,
                        engine = engine
                    )
                } else {
                    Single.just(
                        TransactionProcessor(
                            exchangeRates = exchangeRates,
                            sourceAccount = source,
                            txTarget = target,
                            engine = OnChainSwapTxEngine(
                                quotesEngine = quotesEngine,
                                walletManager = walletManager,
                                kycTierService = kycTierService,
                                engine = engine,
                                environmentConfig = environmentConfig
                            )
                        )
                    )
                }
            is FiatAccount -> Single.just(
                TransactionProcessor(
                    exchangeRates = exchangeRates,
                    sourceAccount = source,
                    txTarget = target,
                    engine = OnChainSellTxEngine(
                        quotesEngine = quotesEngine,
                        walletManager = walletManager,
                        kycTierService = kycTierService,
                        engine = engine,
                        environmentConfig = environmentConfig
                    )
                )
            )
            else -> Single.error(TransferError("Cannot send non-custodial crypto to a non-crypto target"))
        }
    }

    private fun createTradingProcessor(
        source: CustodialTradingAccount,
        target: TransactionTarget,
        action: AssetAction
    ) = when (target) {
        is CryptoAddress ->
            Single.just(
                TransactionProcessor(
                    exchangeRates = exchangeRates,
                    sourceAccount = source,
                    txTarget = target,
                    engine = TradingToOnChainTxEngine(
                        walletManager = walletManager,
                        isNoteSupported = source.isNoteSupported
                    )
                )
            )
        is TradingAccount ->
            Single.just(
                TransactionProcessor(
                    exchangeRates = exchangeRates,
                    sourceAccount = source,
                    txTarget = target,
                    engine = TradingToTradingSwapTxEngine(
                        walletManager = walletManager,
                        quotesEngine = quotesEngine,
                        kycTierService = kycTierService,
                        environmentConfig = environmentConfig
                    )
                )
            )
        is CryptoAccount -> target.receiveAddress
            .map {
                TransactionProcessor(
                    exchangeRates = exchangeRates,
                    sourceAccount = source,
                    txTarget = it,
                    engine = TradingToOnChainTxEngine(
                        walletManager = walletManager,
                        isNoteSupported = source.isNoteSupported
                    )
                )
            }
        is FiatAccount ->
            Single.just(
                TransactionProcessor(
                    exchangeRates = exchangeRates,
                    sourceAccount = source,
                    txTarget = target,
                    engine = TradingSellTxEngine(
                        walletManager = walletManager,
                        quotesEngine = quotesEngine,
                        kycTierService = kycTierService,
                        environmentConfig = environmentConfig
                    )
                )
            )
        else -> Single.error(TransferError("Cannot send custodial crypto to a non-crypto target"))
    }
}
