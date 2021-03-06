package piuk.blockchain.android.coincore.impl.txEngine.swap

import com.blockchain.android.testutils.rxInit
import com.blockchain.koin.payloadScopeQualifier
import com.blockchain.nabu.datamanagers.CurrencyPair
import com.blockchain.nabu.datamanagers.CustodialWalletManager
import com.blockchain.nabu.datamanagers.TransferDirection
import com.blockchain.nabu.datamanagers.TransferLimits
import com.blockchain.nabu.datamanagers.TransferQuote
import com.blockchain.nabu.models.responses.nabu.KycTiers
import com.blockchain.nabu.models.responses.nabu.NabuApiException
import com.blockchain.nabu.models.responses.nabu.NabuErrorCodes
import com.blockchain.nabu.service.TierService
import com.blockchain.preferences.CurrencyPrefs
import com.blockchain.testutils.bitcoin
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.argThat
import com.nhaarman.mockito_kotlin.atLeastOnce
import com.nhaarman.mockito_kotlin.eq
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
import org.bitcoinj.core.NetworkParameters
import org.junit.After
import org.junit.Before
import org.junit.Ignore
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
import piuk.blockchain.android.coincore.bch.BchCryptoWalletAccount
import piuk.blockchain.android.coincore.btc.BtcAddress
import piuk.blockchain.android.coincore.btc.BtcCryptoWalletAccount
import piuk.blockchain.android.coincore.impl.CustodialTradingAccount
import piuk.blockchain.android.coincore.impl.injectMocks
import piuk.blockchain.android.coincore.impl.txEngine.OnChainTxEngineBase
import piuk.blockchain.android.coincore.impl.txEngine.PricedQuote
import piuk.blockchain.android.coincore.impl.txEngine.TransferQuotesEngine
import piuk.blockchain.androidcore.data.api.EnvironmentConfig
import piuk.blockchain.androidcore.data.exchangerate.ExchangeRateDataManager

class OnChainSwapEngineTest {

    @get:Rule
    val initSchedulers = rxInit {
        mainTrampoline()
        ioTrampoline()
        computationTrampoline()
    }

    private val walletManager: CustodialWalletManager = mock()
    private val quotesEngine: TransferQuotesEngine = mock()
    private val kycTierService: TierService = mock()
    private val btcNetworkParams: NetworkParameters = mock()
    private val environmentConfig: EnvironmentConfig = mock {
        on { bitcoinNetworkParameters } itReturns btcNetworkParams
    }

    private val exchangeRates: ExchangeRateDataManager = mock {
        on { getLastPrice(SRC_ASSET, SELECTED_FIAT) } itReturns EXCHANGE_RATE
    }

    private val currencyPrefs: CurrencyPrefs = mock {
        on { selectedFiatCurrency } itReturns SELECTED_FIAT
    }

    private val onChainEngine: OnChainTxEngineBase = mock()

    private val subject = OnChainSwapTxEngine(
        engine = onChainEngine,
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
        val sourceAccount = mockSourceAccount()
        val txTarget = mockTransactionTarget()

        // Act
        subject.start(
            sourceAccount,
            txTarget,
            exchangeRates
        )

        subject.assertInputsValid()

        // Assert
        verify(sourceAccount, atLeastOnce()).asset
        verify(txTarget, atLeastOnce()).asset
        verifyQuotesEngineStarted()
        // todo restore once start engine returns completable
        // verify(onChainEngine).assertInputsValid()

        noMoreInteractions(sourceAccount, txTarget)
    }

    @Test
    fun `inputs validate when correct, for Custodial target`() {
        val sourceAccount = mockSourceAccount()

        val txTarget: CustodialTradingAccount = mock {
            on { asset } itReturns TGT_ASSET
        }

        // Act
        subject.start(
            sourceAccount,
            txTarget,
            exchangeRates
        )

        subject.assertInputsValid()

        // Assert
        verify(sourceAccount, atLeastOnce()).asset
        verify(txTarget, atLeastOnce()).asset
        verify(quotesEngine).start(
            TransferDirection.FROM_USERKEY,
            CurrencyPair.CryptoCurrencyPair(SRC_ASSET, TGT_ASSET)
        )
        // todo restore once start engine returns completable
        // verify(onChainEngine).assertInputsValid()

        noMoreInteractions(sourceAccount, txTarget)
    }

    @Test(expected = IllegalStateException::class)
    fun `inputs fail validation when source Account incorrect`() {
        val sourceAccount: CustodialTradingAccount = mock {
            on { asset } itReturns SRC_ASSET
        }

        val txTarget = mockTransactionTarget()

        // Act
        subject.start(
            sourceAccount,
            txTarget,
            exchangeRates
        )

        subject.assertInputsValid()
    }

    @Test(expected = IllegalStateException::class)
    fun `inputs fail validation when Account assets match`() {
        val sourceAccount = mockSourceAccount()
        val txTarget: CustodialTradingAccount = mock {
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

    @Test(expected = IllegalStateException::class)
    fun `inputs fail validation when target account incorrect`() {
        val sourceAccount = mockSourceAccount()
        val txTarget: FiatAccount = mock {
            on { fiatCurrency } itReturns SELECTED_FIAT
        }

        // Act
        subject.start(
            sourceAccount,
            txTarget,
            exchangeRates
        )

        subject.assertInputsValid()
    }

    @Ignore("restore once start engine returns completable")
    @Test(expected = IllegalStateException::class)
    fun `inputs fail validation when on chain engine validation fails`() {
        val sourceAccount = mockSourceAccount()
        val txTarget = mockTransactionTarget()

        whenever(onChainEngine.assertInputsValid()).thenThrow(IllegalStateException())

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
        val sourceAccount = mockSourceAccount()
        val txTarget = mockTransactionTarget()

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
        verify(txTarget, atLeastOnce()).asset
        verify(quotesEngine).start(
            TransferDirection.ON_CHAIN,
            CurrencyPair.CryptoCurrencyPair(SRC_ASSET, TGT_ASSET)
        )

        noMoreInteractions(sourceAccount, txTarget)
    }

    @Test
    fun `PendingTx is correctly initialised`() {
        // Arrange
        val totalBalance: Money = 21.bitcoin()
        val availableBalance: Money = 20.bitcoin()

        whenUserIsGold()
        val initialFeeLevel = FeeLevel.Regular
        val expectedFeeLevel = FeeLevel.Priority
        val expectedFeeOptions = setOf(FeeLevel.Regular, FeeLevel.Priority)
        whenOnChainEngineInitOK(totalBalance, availableBalance, initialFeeLevel, expectedFeeOptions)

        val sourceAccount = mockSourceAccount(totalBalance, availableBalance)
        val txTarget = mockTransactionTarget()

        val txQuote: TransferQuote = mock {
            on { sampleDepositAddress } itReturns SAMPLE_DEPOSIT_ADDRESS
            on { networkFee } itReturns NETWORK_FEE
        }

        val pricedQuote: PricedQuote = mock {
            on { transferQuote } itReturns txQuote
            on { price } itReturns INITIAL_QUOTE_PRICE
        }

        whenever(quotesEngine.pricedQuote).thenReturn(Observable.just(pricedQuote))
        whenever(quotesEngine.getLatestQuote()).thenReturn(pricedQuote)

        subject.start(
            sourceAccount,
            txTarget,
            exchangeRates
        )

        val minForFee = NETWORK_FEE.toBigDecimal().divide(INITIAL_QUOTE_PRICE.toBigDecimal())
        val expectedMinLimit = MIN_GOLD_LIMIT_ASSET + CryptoValue.fromMajor(SRC_ASSET, minForFee)

        // Act
        subject.doInitialiseTx()
            .test()
            .assertValue {
                it.amount == CryptoValue.zero(SRC_ASSET) &&
                    it.totalBalance == totalBalance &&
                    it.availableBalance == availableBalance &&
                    it.fees == CryptoValue.zero(SRC_ASSET) &&
                    it.selectedFiat == SELECTED_FIAT &&
                    it.customFeeAmount == -1L &&
                    it.confirmations.isEmpty() &&
                    it.minLimit == expectedMinLimit &&
                    it.maxLimit == MAX_GOLD_LIMIT_ASSET &&
                    it.validationState == ValidationState.UNINITIALISED &&
                    it.engineState[USER_TIER] != null
            }
            .assertValue { verifyFeeLevels(it, expectedFeeLevel, expectedFeeOptions) }
            .assertNoErrors()
            .assertComplete()

        verify(sourceAccount, atLeastOnce()).asset
        verify(txTarget, atLeastOnce()).asset
        verify(currencyPrefs).selectedFiatCurrency
        verifyQuotesEngineStarted()
        verifyOnChainEngineStarted(sourceAccount)
        verifyLimitsFetched()
        verify(quotesEngine).pricedQuote
        verify(quotesEngine, atLeastOnce()).getLatestQuote()
        verify(exchangeRates).getLastPrice(SRC_ASSET, SELECTED_FIAT)
        verify(environmentConfig).bitcoinNetworkParameters
        verify(onChainEngine).doInitialiseTx()

        noMoreInteractions(sourceAccount, txTarget)
    }

    @Test
    fun `PendingTx is correctly initialised with flat regular fees`() {
        // Arrange
        val totalBalance: Money = 21.bitcoin()
        val availableBalance: Money = 20.bitcoin()

        whenUserIsGold()
        val expectedFeeLevel = FeeLevel.Regular
        val expectedFeeOptions = setOf(FeeLevel.Regular)
        whenOnChainEngineInitOK(totalBalance, availableBalance, expectedFeeLevel, expectedFeeOptions)

        val sourceAccount = mockSourceAccount(totalBalance, availableBalance)
        val txTarget = mockTransactionTarget()

        val txQuote: TransferQuote = mock {
            on { sampleDepositAddress } itReturns SAMPLE_DEPOSIT_ADDRESS
            on { networkFee } itReturns NETWORK_FEE
        }

        val pricedQuote: PricedQuote = mock {
            on { transferQuote } itReturns txQuote
            on { price } itReturns INITIAL_QUOTE_PRICE
        }

        whenever(quotesEngine.pricedQuote).thenReturn(Observable.just(pricedQuote))
        whenever(quotesEngine.getLatestQuote()).thenReturn(pricedQuote)

        subject.start(
            sourceAccount,
            txTarget,
            exchangeRates
        )

        val minForFee = NETWORK_FEE.toBigDecimal().divide(INITIAL_QUOTE_PRICE.toBigDecimal())
        val expectedMinLimit = MIN_GOLD_LIMIT_ASSET + CryptoValue.fromMajor(SRC_ASSET, minForFee)

        // Act
        subject.doInitialiseTx()
            .test()
            .assertValue {
                it.amount == CryptoValue.zero(SRC_ASSET) &&
                    it.totalBalance == totalBalance &&
                    it.availableBalance == availableBalance &&
                    it.fees == CryptoValue.zero(SRC_ASSET) &&
                    it.selectedFiat == SELECTED_FIAT &&
                    it.customFeeAmount == -1L &&
                    it.confirmations.isEmpty() &&
                    it.minLimit == expectedMinLimit &&
                    it.maxLimit == MAX_GOLD_LIMIT_ASSET &&
                    it.validationState == ValidationState.UNINITIALISED &&
                    it.engineState[USER_TIER] != null
            }
            .assertValue { verifyFeeLevels(it, expectedFeeLevel, expectedFeeOptions) }
            .assertNoErrors()
            .assertComplete()

        verify(sourceAccount, atLeastOnce()).asset
        verify(txTarget, atLeastOnce()).asset
        verify(currencyPrefs).selectedFiatCurrency
        verifyQuotesEngineStarted()
        verifyOnChainEngineStarted(sourceAccount)
        verifyLimitsFetched()
        verify(quotesEngine).pricedQuote
        verify(quotesEngine, atLeastOnce()).getLatestQuote()
        verify(exchangeRates).getLastPrice(SRC_ASSET, SELECTED_FIAT)
        verify(environmentConfig).bitcoinNetworkParameters
        verify(onChainEngine).doInitialiseTx()

        noMoreInteractions(sourceAccount, txTarget)
    }

    @Test
    fun `PendingTx initialisation when limit reached`() {
        // Arrange
        val totalBalance: Money = 21.bitcoin()
        val availableBalance: Money = 20.bitcoin()

        whenUserIsGold()

        val sourceAccount = mockSourceAccount(totalBalance, availableBalance)
        val txTarget = mockTransactionTarget()

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
                it.selectedFiat == SELECTED_FIAT &&
                it.customFeeAmount == -1L &&
                it.confirmations.isEmpty() &&
                it.minLimit == null &&
                it.maxLimit == null &&
                it.validationState == ValidationState.PENDING_ORDERS_LIMIT_REACHED &&
                it.engineState.isEmpty()
            }
            .assertValue { verifyFeeLevels(it, FeeLevel.Regular, setOf(FeeLevel.Regular)) }
            .assertNoErrors()
            .assertComplete()

        verify(sourceAccount, atLeastOnce()).asset
        verify(txTarget, atLeastOnce()).asset
        verify(currencyPrefs).selectedFiatCurrency
        verifyQuotesEngineStarted()
        verify(quotesEngine).pricedQuote

        noMoreInteractions(sourceAccount, txTarget)
    }

    @Test
    fun `update amount modifies the pendingTx correctly`() {
        // Arrange
        val totalBalance: Money = 21.bitcoin()
        val availableBalance: Money = 20.bitcoin()

        val sourceAccount = mockSourceAccount(totalBalance, availableBalance)
        val txTarget = mockTransactionTarget()

        subject.start(
            sourceAccount,
            txTarget,
            exchangeRates
        )

        val expectedFeeLevel = FeeLevel.Priority
        val expectedFeeLevelOptions = setOf(FeeLevel.Regular, FeeLevel.Priority)

        val inputAmount = 2.bitcoin()
        val expectedFee = 0.bitcoin()

        val pendingTx = PendingTx(
            amount = CryptoValue.zero(SRC_ASSET),
            totalBalance = CryptoValue.zero(SRC_ASSET),
            availableBalance = CryptoValue.zero(SRC_ASSET),
            fees = CryptoValue.zero(SRC_ASSET),
            selectedFiat = SELECTED_FIAT,
            feeLevel = expectedFeeLevel,
            availableFeeLevels = expectedFeeLevelOptions
        )

        whenever(onChainEngine.doUpdateAmount(inputAmount, pendingTx))
            .thenReturn(
                Single.just(
                    pendingTx.copy(
                        amount = inputAmount,
                        totalBalance = totalBalance,
                        availableBalance = availableBalance,
                        fees = expectedFee
                    )
                )
            )

        // Act
        subject.doUpdateAmount(
            inputAmount,
            pendingTx
        ).test()
            .assertComplete()
            .assertNoErrors()
            .assertValue {
                it.amount == inputAmount &&
                it.totalBalance == totalBalance &&
                it.availableBalance == availableBalance &&
                it.fees == expectedFee
            }
            .assertValue { verifyFeeLevels(it, expectedFeeLevel, expectedFeeLevelOptions) }

        verify(sourceAccount, atLeastOnce()).asset
        verify(txTarget, atLeastOnce()).asset
        verifyQuotesEngineStarted()
        verify(onChainEngine).doUpdateAmount(inputAmount, pendingTx)
        verify(quotesEngine).updateAmount(inputAmount)

        noMoreInteractions(sourceAccount, txTarget)
    }

    @Test
    fun `doUpdateFeeLevel delegates to the on-chain engine`() {
        // Arrange
        val totalBalance: Money = 21.bitcoin()
        val availableBalance: Money = 20.bitcoin()

        val sourceAccount = mockSourceAccount(totalBalance, availableBalance)
        val txTarget = mockTransactionTarget()

        subject.start(
            sourceAccount,
            txTarget,
            exchangeRates
        )

        val initialFeeLevel = FeeLevel.Priority
        val initialFeeLevelOptions = setOf(FeeLevel.Regular, FeeLevel.Priority)

        val inputAmount = 2.bitcoin()
        val initialFee = 0.bitcoin()

        val pendingTx = PendingTx(
            amount = inputAmount,
            totalBalance = totalBalance,
            availableBalance = availableBalance,
            fees = initialFee,
            selectedFiat = SELECTED_FIAT,
            feeLevel = initialFeeLevel,
            availableFeeLevels = initialFeeLevelOptions
        )

        whenever(
            onChainEngine.doUpdateFeeLevel(
                pendingTx,
                FeeLevel.Regular,
                -1
            )
        ).thenReturn(
            Single.just(
                pendingTx.copy(
                    feeLevel = FeeLevel.Regular
                )
            )
        )

        // Act
        subject.doUpdateFeeLevel(
            pendingTx,
            FeeLevel.Regular,
            -1
        ).test()
            .assertComplete()
            .assertNoErrors()
            .assertValue { verifyFeeLevels(it, FeeLevel.Regular, initialFeeLevelOptions) }

        verify(sourceAccount, atLeastOnce()).asset
        verify(txTarget, atLeastOnce()).asset
        verifyQuotesEngineStarted()
        verify(onChainEngine).doUpdateFeeLevel(pendingTx, FeeLevel.Regular, -1)

        noMoreInteractions(sourceAccount, txTarget)
    }

    private fun mockSourceAccount(
        totalBalance: Money = CryptoValue.zero(SRC_ASSET),
        availableBalance: Money = CryptoValue.zero(SRC_ASSET)
    ) = mock<BtcCryptoWalletAccount> {
            on { asset } itReturns SRC_ASSET
            on { accountBalance } itReturns Single.just(totalBalance)
            on { actionableBalance } itReturns Single.just(availableBalance)
        }

    private fun mockTransactionTarget() = mock<BchCryptoWalletAccount> {
        on { asset } itReturns TGT_ASSET
    }

    private fun whenOnChainEngineInitOK(
        totalBalance: Money,
        availableBalance: Money,
        initialFeeLevel: FeeLevel,
        availableFeeOptions: Set<FeeLevel>
    ) {
        val initialisedPendingTx = PendingTx(
            amount = CryptoValue.zero(SRC_ASSET),
            totalBalance = totalBalance,
            availableBalance = availableBalance,
            fees = CryptoValue.zero(SRC_ASSET),
            selectedFiat = SELECTED_FIAT,
            feeLevel = initialFeeLevel,
            availableFeeLevels = availableFeeOptions
        )
        whenever(onChainEngine.doInitialiseTx()).thenReturn(Single.just(initialisedPendingTx))
    }

    private fun whenUserIsGold() {
        val kycTiers: KycTiers = mock()
        whenever(kycTierService.tiers()).thenReturn(Single.just(kycTiers))

        whenever(walletManager.getSwapLimits(SELECTED_FIAT))
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
        verify(walletManager).getSwapLimits(SELECTED_FIAT)
    }

    private fun verifyOnChainEngineStarted(srcAccount: CryptoAccount) {
        verify(onChainEngine).start(
            sourceAccount = eq(srcAccount),
            txTarget = argThat { this is BtcAddress && address == SAMPLE_DEPOSIT_ADDRESS },
            exchangeRates = eq(exchangeRates),
            refreshTrigger = any()
        )
    }

    private fun verifyQuotesEngineStarted() {
        verify(quotesEngine).start(
            TransferDirection.ON_CHAIN,
            CurrencyPair.CryptoCurrencyPair(SRC_ASSET, TGT_ASSET)
        )
    }

    private fun verifyFeeLevels(
        pendingTx: PendingTx,
        expectedLevel: FeeLevel,
        expectedFeeOptions: Set<FeeLevel>
    ) = pendingTx.feeLevel == expectedLevel &&
        pendingTx.availableFeeLevels == expectedFeeOptions &&
        pendingTx.availableFeeLevels.contains(pendingTx.feeLevel)

    private fun noMoreInteractions(sourceAccount: CryptoAccount, txTarget: TransactionTarget) {
        verifyNoMoreInteractions(txTarget)
        verifyNoMoreInteractions(sourceAccount)
        verifyNoMoreInteractions(walletManager)
        verifyNoMoreInteractions(currencyPrefs)
        verifyNoMoreInteractions(exchangeRates)
        verifyNoMoreInteractions(quotesEngine)
        verifyNoMoreInteractions(kycTierService)
        verifyNoMoreInteractions(environmentConfig)
        verifyNoMoreInteractions(onChainEngine)
    }

    companion object {
        private const val SELECTED_FIAT = "INR"
        private val SRC_ASSET = CryptoCurrency.BTC
        private val TGT_ASSET = CryptoCurrency.XLM
        private val EXCHANGE_RATE = 2.toBigDecimal() // 1 btc == 2 INR

        private const val SAMPLE_DEPOSIT_ADDRESS = "initial quote deposit address"

        private val NETWORK_FEE = CryptoValue.fromMajor(CryptoCurrency.BTC, 0.1.toBigDecimal())

        private val INITIAL_QUOTE_PRICE = CryptoValue.fromMajor(CryptoCurrency.BTC, 10.toBigDecimal())

        private val MIN_GOLD_LIMIT = FiatValue.fromMajor(SELECTED_FIAT, 100.toBigDecimal())
        private val MAX_GOLD_ORDER = FiatValue.fromMajor(SELECTED_FIAT, 500.toBigDecimal())
        private val MAX_GOLD_LIMIT = FiatValue.fromMajor(SELECTED_FIAT, 2000.toBigDecimal())

        private val MIN_GOLD_LIMIT_ASSET = CryptoValue.fromMajor(SRC_ASSET, 50.toBigDecimal())
        private val MAX_GOLD_ORDER_ASSET = CryptoValue.fromMajor(SRC_ASSET, 250.toBigDecimal())
        private val MAX_GOLD_LIMIT_ASSET = CryptoValue.fromMajor(SRC_ASSET, 1000.toBigDecimal())
    }
}
