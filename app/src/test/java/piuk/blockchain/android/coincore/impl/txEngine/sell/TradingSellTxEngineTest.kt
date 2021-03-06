package piuk.blockchain.android.coincore.impl.txEngine.sell

import com.blockchain.android.testutils.rxInit
import com.blockchain.koin.payloadScopeQualifier
import com.blockchain.nabu.datamanagers.CurrencyPair
import com.blockchain.nabu.datamanagers.CustodialWalletManager
import com.blockchain.nabu.datamanagers.TransferDirection
import com.blockchain.nabu.datamanagers.TransferLimits
import com.blockchain.nabu.models.responses.nabu.KycTiers
import com.blockchain.nabu.models.responses.nabu.NabuApiException
import com.blockchain.nabu.models.responses.nabu.NabuErrorCodes
import com.blockchain.nabu.service.TierService
import com.blockchain.preferences.CurrencyPrefs
import com.blockchain.testutils.bitcoin
import com.nhaarman.mockito_kotlin.atLeastOnce
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.verifyNoMoreInteractions
import com.nhaarman.mockito_kotlin.whenever
import info.blockchain.balance.CryptoCurrency
import info.blockchain.balance.CryptoValue
import info.blockchain.balance.FiatValue
import info.blockchain.balance.Money
import io.reactivex.Observable
import io.reactivex.Single
import org.amshove.kluent.itReturns
import org.amshove.kluent.shouldEqual
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import piuk.blockchain.android.coincore.CryptoAccount
import piuk.blockchain.android.coincore.FeeLevel
import piuk.blockchain.android.coincore.FiatAccount
import piuk.blockchain.android.coincore.PendingTx
import piuk.blockchain.android.coincore.TransactionTarget
import piuk.blockchain.android.coincore.ValidationState
import piuk.blockchain.android.coincore.btc.BtcCryptoWalletAccount
import piuk.blockchain.android.coincore.impl.CustodialTradingAccount
import piuk.blockchain.android.coincore.impl.injectMocks
import piuk.blockchain.android.coincore.impl.txEngine.PricedQuote
import piuk.blockchain.android.coincore.impl.txEngine.TransferQuotesEngine
import piuk.blockchain.androidcore.data.api.EnvironmentConfig
import piuk.blockchain.androidcore.data.exchangerate.ExchangeRateDataManager

class TradingSellTxEngineTest {

    @get:Rule
    val initSchedulers = rxInit {
        mainTrampoline()
        ioTrampoline()
        computationTrampoline()
    }

    private val walletManager: CustodialWalletManager = mock()
    private val quotesEngine: TransferQuotesEngine = mock()
    private val kycTierService: TierService = mock()
    private val environmentConfig: EnvironmentConfig = mock()

    private val exchangeRates: ExchangeRateDataManager = mock {
        on { getLastPrice(SRC_ASSET, TGT_ASSET) } itReturns EXCHANGE_RATE
    }

    private val currencyPrefs: CurrencyPrefs = mock {
        on { selectedFiatCurrency } itReturns SELECTED_FIAT
    }

    private val subject = TradingSellTxEngine(
        walletManager = walletManager,
        quotesEngine = quotesEngine,
        kycTierService = kycTierService,
        environmentConfig = environmentConfig
    )

    @Before
    fun setup() {
        injectMocks(
            module {
                scope(payloadScopeQualifier) {
                    factory {
                        currencyPrefs
                    }
                }
            }
        )
    }

    @After
    fun teardown() {
        stopKoin()
    }

    @Test
    fun `inputs validate when correct`() {
        val sourceAccount: CustodialTradingAccount = mock {
            on { asset } itReturns SRC_ASSET
        }
        val txTarget: FiatAccount = mock {
            on { fiatCurrency } itReturns TGT_ASSET
        }

        // Act
        subject.start(
            sourceAccount,
            txTarget,
            exchangeRates
        )

        subject.assertInputsValid()

        // Assert
        verify(txTarget).fiatCurrency
        verify(sourceAccount, atLeastOnce()).asset
        verify(quotesEngine).start(
            TransferDirection.INTERNAL,
            CurrencyPair.CryptoToFiatCurrencyPair(SRC_ASSET, TGT_ASSET)
        )

        noMoreInteractions(txTarget)
    }

    @Test(expected = IllegalStateException::class)
    fun `inputs fail validation when source Account incorrect`() {
        val sourceAccount: BtcCryptoWalletAccount = mock {
            on { asset } itReturns SRC_ASSET
        }

        val txTarget: FiatAccount = mock {
            on { fiatCurrency } itReturns TGT_ASSET
        }

        // Act
        subject.start(
            sourceAccount,
            txTarget,
            exchangeRates
        )

        subject.assertInputsValid()
    }

    @Test(expected = IllegalStateException::class)
    fun `inputs fail validation when target account incorrect`() {
        val sourceAccount: CustodialTradingAccount = mock {
            on { asset } itReturns SRC_ASSET
        }

        val txTarget: CryptoAccount = mock {
            on { asset } itReturns SRC_ASSET
        }

        // Act
        subject.start(
            sourceAccount,
            txTarget,
            exchangeRates
        )

        subject.assertInputsValid()
    }

    @Test
    fun `asset is returned correctly`() {
        // Arrange
        val sourceAccount: CustodialTradingAccount = mock {
            on { asset } itReturns SRC_ASSET
        }
        val txTarget: FiatAccount = mock {
            on { fiatCurrency } itReturns TGT_ASSET
        }

        // Act
        subject.start(
            sourceAccount,
            txTarget,
            exchangeRates
        )

        val asset = subject.asset

        // Assert
        asset shouldEqual SRC_ASSET

        verify(sourceAccount, atLeastOnce()).asset
        verify(txTarget).fiatCurrency
        verifyQuotesEngineStarted()

        noMoreInteractions(txTarget)
    }

    @Test
    fun `PendingTx is correctly initialised`() {
        // Arrange
        val totalBalance: Money = 21.bitcoin()
        val actionableBalance: Money = 20.bitcoin()

        whenUserIsGold()

        val sourceAccount = fundedSourceAccount(totalBalance, actionableBalance)
        val txTarget: FiatAccount = mock {
            on { fiatCurrency } itReturns TGT_ASSET
        }

        val pricedQuote: PricedQuote = mock()

        whenever(quotesEngine.pricedQuote).thenReturn(Observable.just(pricedQuote))

        subject.start(
            sourceAccount,
            txTarget,
            exchangeRates
        )

        // Act
        subject.doInitialiseTx()
            .test()
            .assertValue {
                it.amount == CryptoValue.zero(SRC_ASSET) &&
                it.totalBalance == totalBalance &&
                it.availableBalance == totalBalance &&
                it.fees == CryptoValue.zero(SRC_ASSET) &&
                it.selectedFiat == TGT_ASSET &&
                it.customFeeAmount == -1L &&
                it.confirmations.isEmpty() &&
                it.minLimit == MIN_GOLD_LIMIT_ASSET &&
                it.maxLimit == MAX_GOLD_LIMIT_ASSET &&
                it.validationState == ValidationState.UNINITIALISED &&
                it.engineState.isEmpty()
            }
            .assertValue { verifyFeeLevels(it, FeeLevel.None) }
            .assertNoErrors()
            .assertComplete()

        verify(sourceAccount, atLeastOnce()).asset
        verify(sourceAccount).accountBalance
        verify(txTarget, atLeastOnce()).fiatCurrency
        verifyQuotesEngineStarted()
        verify(quotesEngine).pricedQuote
        verifyLimitsFetched()
        verify(exchangeRates).getLastPrice(SRC_ASSET, TGT_ASSET)

        noMoreInteractions(txTarget)
    }

    @Test
    fun `PendingTx initialisation when limit reached`() {
        // Arrange
        val totalBalance: Money = 21.bitcoin()
        val actionableBalance: Money = 20.bitcoin()

        val sourceAccount = fundedSourceAccount(totalBalance, actionableBalance)
        val txTarget: FiatAccount = mock {
            on { fiatCurrency } itReturns TGT_ASSET
        }

        val error: NabuApiException = mock {
            on { getErrorCode() } itReturns NabuErrorCodes.PendingOrdersLimitReached
        }

        whenever(quotesEngine.pricedQuote).thenReturn(Observable.error(error))

        subject.start(
            sourceAccount,
            txTarget,
            exchangeRates
        )

        // Act
        subject.doInitialiseTx()
            .test()
            .assertValue {
                it.amount == CryptoValue.zero(SRC_ASSET) &&
                it.totalBalance == CryptoValue.zero(SRC_ASSET) &&
                it.availableBalance == CryptoValue.zero(SRC_ASSET) &&
                it.fees == CryptoValue.zero(SRC_ASSET) &&
                it.selectedFiat == TGT_ASSET &&
                it.feeLevel == FeeLevel.None &&
                it.customFeeAmount == -1L &&
                it.confirmations.isEmpty() &&
                it.minLimit == null &&
                it.maxLimit == null &&
                it.validationState == ValidationState.PENDING_ORDERS_LIMIT_REACHED &&
                it.engineState.isEmpty()
            }
            .assertValue { verifyFeeLevels(it, FeeLevel.None) }
            .assertNoErrors()
            .assertComplete()

        verify(sourceAccount, atLeastOnce()).asset
        verify(sourceAccount).accountBalance
        verify(txTarget, atLeastOnce()).fiatCurrency
        verifyQuotesEngineStarted()
        verify(quotesEngine).pricedQuote

        noMoreInteractions(txTarget)
    }

    @Test
    fun `update amount modifies the pendingTx correctly`() {
        // Arrange
        val totalBalance: Money = 21.bitcoin()
        val actionableBalance: Money = 20.bitcoin()

        val sourceAccount = fundedSourceAccount(totalBalance, actionableBalance)
        val txTarget: FiatAccount = mock {
            on { fiatCurrency } itReturns TGT_ASSET
        }

        subject.start(
            sourceAccount,
            txTarget,
            exchangeRates
        )

        val pendingTx = PendingTx(
            amount = CryptoValue.zero(SRC_ASSET),
            totalBalance = CryptoValue.zero(SRC_ASSET),
            availableBalance = CryptoValue.zero(SRC_ASSET),
            fees = CryptoValue.zero(SRC_ASSET),
            selectedFiat = TGT_ASSET,
            feeLevel = FeeLevel.None,
            availableFeeLevels = EXPECTED_AVAILABLE_FEE_LEVELS
        )

        val inputAmount = 2.bitcoin()
        val expectedFee = 0.bitcoin()

        // Act
        subject.doUpdateAmount(
            inputAmount,
            pendingTx
        ).test()
            .assertComplete()
            .assertNoErrors()
            .assertValue {
                val v = -100
                it.amount == inputAmount &&
                it.totalBalance == totalBalance &&
                it.availableBalance == totalBalance &&
                it.fees == expectedFee
            }
            .assertValue { verifyFeeLevels(it, FeeLevel.None) }

        verify(sourceAccount, atLeastOnce()).asset
        verify(sourceAccount).accountBalance
        verify(txTarget, atLeastOnce()).fiatCurrency
        verifyQuotesEngineStarted()
        verify(quotesEngine).updateAmount(inputAmount)

        noMoreInteractions(txTarget)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `update fee level from NONE to PRIORITY is rejected`() {
        // Arrange
        val totalBalance: Money = 21.bitcoin()
        val actionableBalance: Money = 20.bitcoin()
        val inputAmount = 2.bitcoin()
        val initialFee = 0.bitcoin()

        val sourceAccount = fundedSourceAccount(totalBalance, actionableBalance)
        val txTarget: FiatAccount = mock {
            on { fiatCurrency } itReturns TGT_ASSET
        }

        subject.start(
            sourceAccount,
            txTarget,
            exchangeRates
        )

        val pendingTx = PendingTx(
            amount = inputAmount,
            totalBalance = totalBalance,
            availableBalance = totalBalance,
            fees = initialFee,
            selectedFiat = TGT_ASSET,
            feeLevel = FeeLevel.None,
            availableFeeLevels = EXPECTED_AVAILABLE_FEE_LEVELS
        )

        // Act
        subject.doUpdateFeeLevel(
            pendingTx,
            FeeLevel.Priority,
            -1
        ).test()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `update fee level from NONE to REGULAR is rejected`() {
        // Arrange
        val totalBalance: Money = 21.bitcoin()
        val actionableBalance: Money = 20.bitcoin()
        val inputAmount = 2.bitcoin()
        val initialFee = 0.bitcoin()

        val sourceAccount = fundedSourceAccount(totalBalance, actionableBalance)
        val txTarget: FiatAccount = mock {
            on { fiatCurrency } itReturns TGT_ASSET
        }

        subject.start(
            sourceAccount,
            txTarget,
            exchangeRates
        )

        val pendingTx = PendingTx(
            amount = inputAmount,
            totalBalance = totalBalance,
            availableBalance = totalBalance,
            fees = initialFee,
            selectedFiat = TGT_ASSET,
            feeLevel = FeeLevel.None,
            availableFeeLevels = EXPECTED_AVAILABLE_FEE_LEVELS
        )

        // Act
        subject.doUpdateFeeLevel(
            pendingTx,
            FeeLevel.Regular,
            -1
        ).test()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `update fee level from NONE to CUSTOM is rejected`() {
        // Arrange
        val totalBalance: Money = 21.bitcoin()
        val actionableBalance: Money = 20.bitcoin()
        val inputAmount = 2.bitcoin()
        val initialFee = 0.bitcoin()

        val sourceAccount = fundedSourceAccount(totalBalance, actionableBalance)
        val txTarget: FiatAccount = mock {
            on { fiatCurrency } itReturns TGT_ASSET
        }

        subject.start(
            sourceAccount,
            txTarget,
            exchangeRates
        )

        val pendingTx = PendingTx(
            amount = inputAmount,
            totalBalance = totalBalance,
            availableBalance = totalBalance,
            fees = initialFee,
            selectedFiat = TGT_ASSET,
            feeLevel = FeeLevel.None,
            availableFeeLevels = EXPECTED_AVAILABLE_FEE_LEVELS
        )

        // Act
        subject.doUpdateFeeLevel(
            pendingTx,
            FeeLevel.Custom,
            100
        ).test()
    }

    @Test
    fun `update fee level from NONE to NONE has no effect`() {
        // Arrange
        val totalBalance: Money = 21.bitcoin()
        val actionableBalance: Money = 20.bitcoin()
        val inputAmount = 2.bitcoin()
        val initialFee = 0.bitcoin()

        val sourceAccount = fundedSourceAccount(totalBalance, actionableBalance)
        val txTarget: FiatAccount = mock {
            on { fiatCurrency } itReturns TGT_ASSET
        }

        subject.start(
            sourceAccount,
            txTarget,
            exchangeRates
        )

        val pendingTx = PendingTx(
            amount = inputAmount,
            totalBalance = totalBalance,
            availableBalance = totalBalance,
            fees = initialFee,
            selectedFiat = TGT_ASSET,
            feeLevel = FeeLevel.None,
            availableFeeLevels = EXPECTED_AVAILABLE_FEE_LEVELS
        )

        // Act
        subject.doUpdateFeeLevel(
            pendingTx,
            FeeLevel.None,
        -1
        ).test()
            .assertComplete()
            .assertNoErrors()
            .assertValue {
                it.amount == inputAmount &&
                it.totalBalance == totalBalance &&
                it.availableBalance == totalBalance &&
                it.fees == initialFee
            }
            .assertValue { verifyFeeLevels(it, FeeLevel.None) }

        verify(sourceAccount, atLeastOnce()).asset
        verify(txTarget, atLeastOnce()).fiatCurrency
        verifyQuotesEngineStarted()

        noMoreInteractions(txTarget)
    }

    private fun fundedSourceAccount(totalBalance: Money, availableBalance: Money) =
        mock<CustodialTradingAccount> {
            on { asset } itReturns SRC_ASSET
            on { accountBalance } itReturns Single.just(totalBalance)
            on { actionableBalance } itReturns Single.just(availableBalance)
        }

    private fun whenUserIsGold() {
        val kycTiers: KycTiers = mock()
        whenever(kycTierService.tiers()).thenReturn(Single.just(kycTiers))

        whenever(walletManager.getSwapLimits(TGT_ASSET))
            .itReturns(
                Single.just(
                    TransferLimits(
                        minLimit = MIN_GOLD_LIMIT,
                        maxOrder = MAX_GOLD_ORDER,
                        maxLimit = MAX_GOLD_LIMIT
                    )
                )
            )
    }

    private fun verifyLimitsFetched() {
        verify(kycTierService).tiers()
        verify(walletManager).getSwapLimits(TGT_ASSET)
    }

    private fun verifyQuotesEngineStarted() {
        verify(quotesEngine).start(
            TransferDirection.INTERNAL,
            CurrencyPair.CryptoToFiatCurrencyPair(SRC_ASSET, TGT_ASSET)
        )
    }

    private fun verifyFeeLevels(pendingTx: PendingTx, expectedLevel: FeeLevel) =
        pendingTx.feeLevel == expectedLevel &&
            pendingTx.availableFeeLevels == EXPECTED_AVAILABLE_FEE_LEVELS &&
            pendingTx.availableFeeLevels.contains(pendingTx.feeLevel)

    private fun noMoreInteractions(txTarget: TransactionTarget) {
        verifyNoMoreInteractions(txTarget)
        verifyNoMoreInteractions(walletManager)
        verifyNoMoreInteractions(currencyPrefs)
        verifyNoMoreInteractions(exchangeRates)
        verifyNoMoreInteractions(quotesEngine)
        verifyNoMoreInteractions(kycTierService)
        verifyNoMoreInteractions(environmentConfig)
    }

    companion object {
        private const val SELECTED_FIAT = "INR"
        private val SRC_ASSET = CryptoCurrency.BTC
        private const val TGT_ASSET = "EUR"
        private val EXCHANGE_RATE = 2.toBigDecimal() // 1 btc == 2 EUR

        private val MIN_GOLD_LIMIT = FiatValue.fromMajor(TGT_ASSET, 100.toBigDecimal())
        private val MAX_GOLD_ORDER = FiatValue.fromMajor(TGT_ASSET, 500.toBigDecimal())
        private val MAX_GOLD_LIMIT = FiatValue.fromMajor(TGT_ASSET, 2000.toBigDecimal())

        private val MIN_GOLD_LIMIT_ASSET = CryptoValue.fromMajor(SRC_ASSET, 50.toBigDecimal())
        private val MAX_GOLD_ORDER_ASSET = CryptoValue.fromMajor(SRC_ASSET, 250.toBigDecimal())
        private val MAX_GOLD_LIMIT_ASSET = CryptoValue.fromMajor(SRC_ASSET, 1000.toBigDecimal())

        private val EXPECTED_AVAILABLE_FEE_LEVELS = setOf(FeeLevel.None)
    }
}
