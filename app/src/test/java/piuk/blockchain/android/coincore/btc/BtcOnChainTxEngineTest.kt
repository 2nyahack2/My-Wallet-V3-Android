package piuk.blockchain.android.coincore.btc

import com.blockchain.android.testutils.rxInit
import com.blockchain.koin.payloadScopeQualifier
import com.blockchain.preferences.CurrencyPrefs
import com.blockchain.preferences.WalletStatus
import com.blockchain.testutils.bitcoin
import com.blockchain.testutils.satoshi
import com.nhaarman.mockito_kotlin.atLeastOnce
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.verifyNoMoreInteractions
import com.nhaarman.mockito_kotlin.whenever
import info.blockchain.api.data.UnspentOutput
import info.blockchain.api.data.UnspentOutputs
import info.blockchain.balance.CryptoCurrency
import info.blockchain.balance.CryptoValue
import info.blockchain.balance.Money
import info.blockchain.wallet.api.data.FeeOptions
import info.blockchain.wallet.payment.SpendableUnspentOutputs
import io.reactivex.Observable
import io.reactivex.Single
import org.amshove.kluent.itReturns
import org.amshove.kluent.mock
import org.bitcoinj.core.NetworkParameters
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import piuk.blockchain.android.coincore.BlockchainAccount
import kotlin.test.assertEquals
import piuk.blockchain.android.coincore.CryptoAddress
import piuk.blockchain.android.coincore.FeeLevel
import piuk.blockchain.android.coincore.PendingTx
import piuk.blockchain.android.coincore.TransactionTarget
import piuk.blockchain.android.coincore.ValidationState
import piuk.blockchain.android.coincore.impl.injectMocks
import piuk.blockchain.androidcore.data.exchangerate.ExchangeRateDataManager
import piuk.blockchain.androidcore.data.fees.FeeDataManager
import piuk.blockchain.androidcore.data.payload.PayloadDataManager
import piuk.blockchain.androidcore.data.payments.SendDataManager

class BtcOnChainTxEngineTest {

    @get:Rule
    val initSchedulers = rxInit {
        mainTrampoline()
        ioTrampoline()
        computationTrampoline()
    }

    private val btcDataManager: PayloadDataManager = mock()
    private val sendDataManager: SendDataManager = mock()
    private val btcNetworkParams: NetworkParameters = mock()

    private val btcFeeOptions: FeeOptions = mock {
        on { regularFee } itReturns FEE_REGULAR
        on { priorityFee } itReturns FEE_PRIORITY
    }
    private val feeManager: FeeDataManager = mock {
        on { btcFeeOptions } itReturns Observable.just(btcFeeOptions)
    }

    private val walletPreferences: WalletStatus = mock {
        on { getFeeTypeForAsset(ASSET) } itReturns FeeLevel.Regular.ordinal
    }
    private val exchangeRates: ExchangeRateDataManager = mock()

    private val currencyPrefs: CurrencyPrefs = mock {
        on { selectedFiatCurrency } itReturns SELECTED_FIAT
    }

    private val subject = BtcOnChainTxEngine(
        btcDataManager = btcDataManager,
        sendDataManager = sendDataManager,
        btcNetworkParams = btcNetworkParams,
        feeManager = feeManager,
        requireSecondPassword = false,
        walletPreferences = walletPreferences
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
        val txTarget: CryptoAddress = mock {
            on { asset } itReturns ASSET
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
        verify(txTarget).asset

        noMoreInteractions(sourceAccount, txTarget)
    }

    @Test(expected = IllegalStateException::class)
    fun `inputs fail validation when source Asset incorrect`() {
        val sourceAccount: BtcCryptoWalletAccount = mock {
            on { asset } itReturns WRONG_ASSET
        }

        val txTarget: CryptoAddress = mock {
            on { asset } itReturns ASSET
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
        verify(txTarget).asset

        noMoreInteractions(sourceAccount, txTarget)
    }

    @Test
    fun `asset is returned correctly`() {
        // Arrange
        val sourceAccount = mockSourceAccount()
        val txTarget: CryptoAddress = mock {
            on { asset } itReturns ASSET
        }

        // Act
        subject.start(
            sourceAccount,
            txTarget,
            exchangeRates
        )

        val asset = subject.asset

        // Assert
        assertEquals(asset, ASSET)
        verify(sourceAccount, atLeastOnce()).asset

        noMoreInteractions(sourceAccount, txTarget)
    }

    @Test
    fun `PendingTx is correctly initialised`() {
        // Arrange
        val sourceAccount = mockSourceAccount()
        val txTarget: CryptoAddress = mock {
            on { asset } itReturns ASSET
        }

        subject.start(
            sourceAccount,
            txTarget,
            exchangeRates
        )

        // Act
        subject.doInitialiseTx()
            .test()
            .assertValue {
                it.amount == CryptoValue.zero(ASSET) &&
                    it.totalBalance == CryptoValue.zero(ASSET) &&
                    it.availableBalance == CryptoValue.zero(ASSET) &&
                    it.fees == CryptoValue.zero(ASSET) &&
                    it.selectedFiat == SELECTED_FIAT &&
                    it.customFeeAmount == -1L &&
                    it.confirmations.isEmpty() &&
                    it.minLimit == null &&
                    it.maxLimit == null &&
                    it.validationState == ValidationState.UNINITIALISED &&
                    it.engineState.isEmpty()
            }
            .assertValue { verifyFeeLevels(it, FeeLevel.Regular) }
            .assertNoErrors()
            .assertComplete()

        verify(sourceAccount, atLeastOnce()).asset
        verify(walletPreferences).getFeeTypeForAsset(ASSET)
        verify(currencyPrefs).selectedFiatCurrency

        noMoreInteractions(sourceAccount, txTarget)
    }

    @Test
    fun `update amount modifies the pendingTx correctly for regular fees`() {
        // Arrange
        val inputAmount = 2.bitcoin()
        val feePerKb = (FEE_REGULAR * 1000).satoshi()
        val totalFee = (FEE_REGULAR * 1000 * 3).satoshi()

        val txTarget: CryptoAddress = mock {
            on { asset } itReturns ASSET
            on { address } itReturns TARGET_ADDRESS
        }

        val totalBalance = 21.bitcoin()
        val actionableBalance = 19.bitcoin()
        val totalSweepable = totalBalance - totalFee

        val sourceAccount = mockSourceAccount(totalBalance, actionableBalance)

        whenever(btcDataManager.getAddressBalance(SOURCE_XPUB)).thenReturn(totalBalance)

        val unspentOutputs: UnspentOutputs = mock {
            on { unspentOutputs } itReturns arrayListOf<UnspentOutput>(mock(), mock())
        }
        whenever(sendDataManager.getUnspentBtcOutputs(SOURCE_XPUB))
            .thenReturn(Observable.just(unspentOutputs))

        whenever(
            sendDataManager.getMaximumAvailable(
                ASSET,
                unspentOutputs,
                feePerKb
            )
        ).thenReturn(totalSweepable as CryptoValue)

        val utxoBundle: SpendableUnspentOutputs = mock {
            on { absoluteFee } itReturns totalFee.toBigInteger()
        }

        whenever(sendDataManager.getSpendableCoins(
            unspentOutputs,
            inputAmount,
            feePerKb
        )).thenReturn(utxoBundle)

        subject.start(
            sourceAccount,
            txTarget,
            exchangeRates
        )

        val pendingTx = PendingTx(
            amount = CryptoValue.zero(ASSET),
            totalBalance = CryptoValue.zero(ASSET),
            availableBalance = CryptoValue.zero(ASSET),
            fees = CryptoValue.zero(ASSET),
            selectedFiat = SELECTED_FIAT,
            feeLevel = FeeLevel.Regular,
            availableFeeLevels = EXPECTED_AVAILABLE_FEE_LEVELS
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
                it.availableBalance == totalSweepable &&
                it.fees == totalFee
            }
            .assertValue { verifyFeeLevels(it, FeeLevel.Regular) }

        verify(sourceAccount, atLeastOnce()).asset
        verify(sourceAccount).xpubAddress
        verify(sourceAccount).accountBalance
        verify(btcDataManager).getAddressBalance(SOURCE_XPUB)
        verify(feeManager).btcFeeOptions
        verify(btcFeeOptions).regularFee
        verify(unspentOutputs).unspentOutputs
        verify(sendDataManager).getUnspentBtcOutputs(SOURCE_XPUB)
        verify(sendDataManager).getMaximumAvailable(ASSET, unspentOutputs, feePerKb)
        verify(utxoBundle).absoluteFee
        verify(sendDataManager).getSpendableCoins(unspentOutputs, inputAmount, feePerKb)

        noMoreInteractions(sourceAccount, txTarget)
    }

    @Test
    fun `update amount modifies the pendingTx correctly for priority fees`() {
        // Arrange
        val inputAmount = 2.bitcoin()
        val feePerKb = (FEE_PRIORITY * 1000).satoshi()
        val totalFee = (FEE_REGULAR * 1000 * 3).satoshi()

        val txTarget: CryptoAddress = mock {
            on { asset } itReturns ASSET
            on { address } itReturns TARGET_ADDRESS
        }

        val totalBalance = 21.bitcoin()
        val actionableBalance = 19.bitcoin()
        val totalSweepable = totalBalance - totalFee

        val sourceAccount = mockSourceAccount(totalBalance, actionableBalance)

        whenever(btcDataManager.getAddressBalance(SOURCE_XPUB)).thenReturn(totalBalance)

        val unspentOutputs: UnspentOutputs = mock {
            on { unspentOutputs } itReturns arrayListOf<UnspentOutput>(mock(), mock())
        }
        whenever(sendDataManager.getUnspentBtcOutputs(SOURCE_XPUB))
            .thenReturn(Observable.just(unspentOutputs))

        whenever(
            sendDataManager.getMaximumAvailable(
                ASSET,
                unspentOutputs,
                feePerKb
            )
        ).thenReturn(totalSweepable as CryptoValue)

        val utxoBundle: SpendableUnspentOutputs = mock {
            on { absoluteFee } itReturns totalFee.toBigInteger()
        }

        whenever(sendDataManager.getSpendableCoins(
            unspentOutputs,
            inputAmount,
            feePerKb
        )).thenReturn(utxoBundle)

        subject.start(
            sourceAccount,
            txTarget,
            exchangeRates
        )

        val pendingTx = PendingTx(
            amount = CryptoValue.zero(ASSET),
            totalBalance = CryptoValue.zero(ASSET),
            availableBalance = CryptoValue.zero(ASSET),
            fees = CryptoValue.zero(ASSET),
            selectedFiat = SELECTED_FIAT,
            feeLevel = FeeLevel.Priority,
            availableFeeLevels = EXPECTED_AVAILABLE_FEE_LEVELS
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
                    it.availableBalance == totalSweepable &&
                    it.fees == totalFee
            }
            .assertValue { verifyFeeLevels(it, FeeLevel.Priority) }

        verify(sourceAccount, atLeastOnce()).asset
        verify(sourceAccount).xpubAddress
        verify(sourceAccount).accountBalance
        verify(btcDataManager).getAddressBalance(SOURCE_XPUB)
        verify(feeManager).btcFeeOptions
        verify(btcFeeOptions).priorityFee
        verify(unspentOutputs).unspentOutputs
        verify(sendDataManager).getUnspentBtcOutputs(SOURCE_XPUB)
        verify(sendDataManager).getMaximumAvailable(ASSET, unspentOutputs, feePerKb)
        verify(utxoBundle).absoluteFee
        verify(sendDataManager).getSpendableCoins(unspentOutputs, inputAmount, feePerKb)

        noMoreInteractions(sourceAccount, txTarget)
    }

    @Test
    fun `update fee level from REGULAR to PRIORITY updates the pendingTx correctly`() {
        // Arrange
        val inputAmount = 2.bitcoin()
        val regularFee = (FEE_REGULAR * 1000 * 3).satoshi()
        val totalBalance = 21.bitcoin()
        val actionableBalance = 19.bitcoin()
        val regularSweepable = totalBalance - regularFee

        val sourceAccount = mockSourceAccount(totalBalance, actionableBalance)

        val txTarget: CryptoAddress = mock {
            on { asset } itReturns ASSET
            on { address } itReturns TARGET_ADDRESS
        }

        whenever(btcDataManager.getAddressBalance(SOURCE_XPUB)).thenReturn(totalBalance)

        val unspentOutputs: UnspentOutputs = mock {
            on { unspentOutputs } itReturns arrayListOf<UnspentOutput>(mock(), mock())
        }
        whenever(sendDataManager.getUnspentBtcOutputs(SOURCE_XPUB))
            .thenReturn(Observable.just(unspentOutputs))

        val feePerKb = (FEE_PRIORITY * 1000).satoshi()
        val priorityFee = (FEE_PRIORITY * 1000 * 3).satoshi()
        val prioritySweepable = totalBalance - priorityFee

        whenever(
            sendDataManager.getMaximumAvailable(
                ASSET,
                unspentOutputs,
                feePerKb
            )
        ).thenReturn(prioritySweepable as CryptoValue)

        val utxoBundle: SpendableUnspentOutputs = mock {
            on { absoluteFee } itReturns priorityFee.toBigInteger()
        }

        whenever(sendDataManager.getSpendableCoins(
            unspentOutputs,
            inputAmount,
            feePerKb
        )).thenReturn(utxoBundle)

        subject.start(
            sourceAccount,
            txTarget,
            exchangeRates
        )

        val pendingTx = PendingTx(
            amount = inputAmount,
            totalBalance = totalBalance,
            availableBalance = regularSweepable,
            fees = regularFee,
            selectedFiat = SELECTED_FIAT,
            feeLevel = FeeLevel.Regular,
            availableFeeLevels = EXPECTED_AVAILABLE_FEE_LEVELS
        )

        // Act
        subject.doUpdateFeeLevel(
            pendingTx,
            FeeLevel.Priority,
            -1
        ).test()
            .assertComplete()
            .assertNoErrors()
            .assertValue {
                it.amount == inputAmount &&
                it.totalBalance == totalBalance &&
                it.availableBalance == prioritySweepable &&
                it.fees == priorityFee
            }
            .assertValue { verifyFeeLevels(it, FeeLevel.Priority) }

        verify(sourceAccount, atLeastOnce()).asset
        verify(sourceAccount).xpubAddress
        verify(sourceAccount).accountBalance
        verify(btcDataManager).getAddressBalance(SOURCE_XPUB)
        verify(feeManager).btcFeeOptions
        verify(btcFeeOptions).priorityFee
        verify(unspentOutputs).unspentOutputs
        verify(sendDataManager).getUnspentBtcOutputs(SOURCE_XPUB)
        verify(sendDataManager).getMaximumAvailable(ASSET, unspentOutputs, feePerKb)
        verify(utxoBundle).absoluteFee
        verify(sendDataManager).getSpendableCoins(unspentOutputs, inputAmount, feePerKb)
        verify(walletPreferences).setFeeTypeForAsset(ASSET, FeeLevel.Priority.ordinal)

        noMoreInteractions(sourceAccount, txTarget)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `update fee level from REGULAR to NONE is rejected`() {
        // Arrange
        val inputAmount = 2.bitcoin()
        val regularFee = (FEE_REGULAR * 1000 * 3).satoshi()
        val totalBalance = 21.bitcoin()
        val actionableBalance = 19.bitcoin()
        val regularSweepable = totalBalance - regularFee

        val sourceAccount = mockSourceAccount(totalBalance, actionableBalance)

        val txTarget: CryptoAddress = mock {
            on { asset } itReturns ASSET
            on { address } itReturns TARGET_ADDRESS
        }

        subject.start(
            sourceAccount,
            txTarget,
            exchangeRates
        )

        val pendingTx = PendingTx(
            amount = inputAmount,
            totalBalance = totalBalance,
            availableBalance = regularSweepable,
            fees = regularFee,
            selectedFiat = SELECTED_FIAT,
            feeLevel = FeeLevel.Regular,
            availableFeeLevels = EXPECTED_AVAILABLE_FEE_LEVELS
        )

        // Act
        subject.doUpdateFeeLevel(
            pendingTx,
            FeeLevel.None,
            -1
        ).test()

        noMoreInteractions(sourceAccount, txTarget)
    }

    @Test
    fun `update fee level from REGULAR to REGULAR has no effect`() {
        // Arrange
        val inputAmount = 2.bitcoin()
        val regularFee = (FEE_REGULAR * 1000 * 3).satoshi()
        val totalBalance = 21.bitcoin()
        val actionableBalance = 19.bitcoin()
        val regularSweepable = totalBalance - regularFee

        val sourceAccount = mockSourceAccount(totalBalance, actionableBalance)

        val txTarget: CryptoAddress = mock {
            on { asset } itReturns ASSET
            on { address } itReturns TARGET_ADDRESS
        }

        subject.start(
            sourceAccount,
            txTarget,
            exchangeRates
        )

        val pendingTx = PendingTx(
            amount = inputAmount,
            totalBalance = totalBalance,
            availableBalance = regularSweepable,
            fees = regularFee,
            selectedFiat = SELECTED_FIAT,
            feeLevel = FeeLevel.Regular,
            availableFeeLevels = EXPECTED_AVAILABLE_FEE_LEVELS
        )

        // Act
        subject.doUpdateFeeLevel(
            pendingTx,
            FeeLevel.Regular,
            -1
        ).test()
            .assertComplete()
            .assertNoErrors()
            .assertValue {
                it.amount == inputAmount &&
                it.totalBalance == totalBalance &&
                it.availableBalance == regularSweepable &&
                it.fees == regularFee
            }
            .assertValue { verifyFeeLevels(it, FeeLevel.Regular) }

        noMoreInteractions(sourceAccount, txTarget)
    }

    @Test
    fun `update fee level from REGULAR to CUSTOM is updates the pendingTx correctly`() {
        // Arrange
        val inputAmount = 2.bitcoin()
        val regularFee = (FEE_REGULAR * 1000 * 3).satoshi()
        val totalBalance = 21.bitcoin()
        val actionableBalance = 19.bitcoin()
        val regularSweepable = totalBalance - regularFee

        val sourceAccount = mockSourceAccount(totalBalance, actionableBalance)

        val txTarget: CryptoAddress = mock {
            on { asset } itReturns ASSET
            on { address } itReturns TARGET_ADDRESS
        }

        whenever(btcDataManager.getAddressBalance(SOURCE_XPUB)).thenReturn(totalBalance)

        val unspentOutputs: UnspentOutputs = mock {
            on { unspentOutputs } itReturns arrayListOf<UnspentOutput>(mock(), mock())
        }
        whenever(sendDataManager.getUnspentBtcOutputs(SOURCE_XPUB))
            .thenReturn(Observable.just(unspentOutputs))

        val feeCustom = 7L
        val feePerKb = (feeCustom * 1000).satoshi()
        val expectedFee = (feeCustom * 1000 * 3).satoshi()
        val expectedSweepable = totalBalance - expectedFee

        whenever(
            sendDataManager.getMaximumAvailable(
                ASSET,
                unspentOutputs,
                feePerKb
            )
        ).thenReturn(expectedSweepable as CryptoValue)

        val utxoBundle: SpendableUnspentOutputs = mock {
            on { absoluteFee } itReturns expectedFee.toBigInteger()
        }

        whenever(sendDataManager.getSpendableCoins(
            unspentOutputs,
            inputAmount,
            feePerKb
        )).thenReturn(utxoBundle)

        subject.start(
            sourceAccount,
            txTarget,
            exchangeRates
        )

        val pendingTx = PendingTx(
            amount = inputAmount,
            totalBalance = totalBalance,
            availableBalance = regularSweepable,
            fees = regularFee,
            selectedFiat = SELECTED_FIAT,
            feeLevel = FeeLevel.Regular,
            availableFeeLevels = EXPECTED_AVAILABLE_FEE_LEVELS
        )

        // Act
        subject.doUpdateFeeLevel(
            pendingTx,
            FeeLevel.Custom,
            feeCustom
        ).test()
            .assertComplete()
            .assertNoErrors()
            .assertValue {
                it.amount == inputAmount &&
                it.totalBalance == totalBalance &&
                it.availableBalance == expectedSweepable &&
                it.fees == expectedFee
            }
            .assertValue { verifyFeeLevels(it, FeeLevel.Custom, feeCustom) }

        verify(sourceAccount, atLeastOnce()).asset
        verify(sourceAccount).xpubAddress
        verify(sourceAccount).accountBalance
        verify(btcDataManager).getAddressBalance(SOURCE_XPUB)
        verify(feeManager).btcFeeOptions
        verify(unspentOutputs).unspentOutputs
        verify(sendDataManager).getUnspentBtcOutputs(SOURCE_XPUB)
        verify(sendDataManager).getMaximumAvailable(ASSET, unspentOutputs, feePerKb)
        verify(utxoBundle).absoluteFee
        verify(sendDataManager).getSpendableCoins(unspentOutputs, inputAmount, feePerKb)
        verify(walletPreferences).setFeeTypeForAsset(ASSET, FeeLevel.Custom.ordinal)

        noMoreInteractions(sourceAccount, txTarget)
    }

    @Test
    fun `changing the custom fee level updates the pendingTx correctly`() {
        // Arrange
        val inputAmount = 2.bitcoin()
        val totalBalance = 21.bitcoin()
        val actionableBalance = 19.bitcoin()

        val initialCustomFee = 7L
        val initialFee = (initialCustomFee * 1000 * 3).satoshi()
        val customSweepable = totalBalance - initialFee

        val sourceAccount = mockSourceAccount(totalBalance, actionableBalance)

        val txTarget: CryptoAddress = mock {
            on { asset } itReturns ASSET
            on { address } itReturns TARGET_ADDRESS
        }

        whenever(btcDataManager.getAddressBalance(SOURCE_XPUB)).thenReturn(totalBalance)

        val unspentOutputs: UnspentOutputs = mock {
            on { unspentOutputs } itReturns arrayListOf<UnspentOutput>(mock(), mock())
        }
        whenever(sendDataManager.getUnspentBtcOutputs(SOURCE_XPUB))
            .thenReturn(Observable.just(unspentOutputs))

        val feeCustom = 15L
        val feePerKb = (feeCustom * 1000).satoshi()
        val expectedFee = (feeCustom * 1000 * 3).satoshi()
        val expectedSweepable = totalBalance - expectedFee

        whenever(
            sendDataManager.getMaximumAvailable(
                ASSET,
                unspentOutputs,
                feePerKb
            )
        ).thenReturn(expectedSweepable as CryptoValue)

        val utxoBundle: SpendableUnspentOutputs = mock {
            on { absoluteFee } itReturns expectedFee.toBigInteger()
        }

        whenever(sendDataManager.getSpendableCoins(
            unspentOutputs,
            inputAmount,
            feePerKb
        )).thenReturn(utxoBundle)

        subject.start(
            sourceAccount,
            txTarget,
            exchangeRates
        )

        val pendingTx = PendingTx(
            amount = inputAmount,
            totalBalance = totalBalance,
            availableBalance = customSweepable,
            fees = initialFee,
            selectedFiat = SELECTED_FIAT,
            feeLevel = FeeLevel.Custom,
            customFeeAmount = initialCustomFee,
            availableFeeLevels = EXPECTED_AVAILABLE_FEE_LEVELS
        )

        // Act
        subject.doUpdateFeeLevel(
            pendingTx,
            FeeLevel.Custom,
            feeCustom
        ).test()
            .assertComplete()
            .assertNoErrors()
            .assertValue {
                val v = 1
                it.amount == inputAmount &&
                it.totalBalance == totalBalance &&
                it.availableBalance == expectedSweepable &&
                it.fees == expectedFee
            }
            .assertValue { verifyFeeLevels(it, FeeLevel.Custom, feeCustom) }

        verify(sourceAccount, atLeastOnce()).asset
        verify(sourceAccount).xpubAddress
        verify(sourceAccount).accountBalance
        verify(btcDataManager).getAddressBalance(SOURCE_XPUB)
        verify(feeManager).btcFeeOptions
        verify(unspentOutputs).unspentOutputs
        verify(sendDataManager).getUnspentBtcOutputs(SOURCE_XPUB)
        verify(sendDataManager).getMaximumAvailable(ASSET, unspentOutputs, feePerKb)
        verify(utxoBundle).absoluteFee
        verify(sendDataManager).getSpendableCoins(unspentOutputs, inputAmount, feePerKb)
        verify(walletPreferences).setFeeTypeForAsset(ASSET, FeeLevel.Custom.ordinal)

        noMoreInteractions(sourceAccount, txTarget)
    }

    private fun verifyFeeLevels(pendingTx: PendingTx, expectedLevel: FeeLevel, customFee: Long = -1) =
        pendingTx.feeLevel == expectedLevel &&
            pendingTx.availableFeeLevels == EXPECTED_AVAILABLE_FEE_LEVELS &&
            pendingTx.availableFeeLevels.contains(pendingTx.feeLevel) &&
            pendingTx.customFeeAmount == customFee

    private fun mockSourceAccount(
        totalBalance: Money = CryptoValue.zero(ASSET),
        availableBalance: Money = CryptoValue.zero(ASSET)
    ) = mock<BtcCryptoWalletAccount> {
        on { asset } itReturns ASSET
        on { accountBalance } itReturns Single.just(totalBalance)
        on { actionableBalance } itReturns Single.just(availableBalance)
        on { xpubAddress } itReturns SOURCE_XPUB
    }

    private fun noMoreInteractions(sourceAccount: BlockchainAccount, txTarget: TransactionTarget) {
        verifyNoMoreInteractions(txTarget)
        verifyNoMoreInteractions(btcDataManager)
        verifyNoMoreInteractions(sendDataManager)
        verifyNoMoreInteractions(btcNetworkParams)
        verifyNoMoreInteractions(feeManager)
        verifyNoMoreInteractions(btcFeeOptions)
        verifyNoMoreInteractions(walletPreferences)
        verifyNoMoreInteractions(sourceAccount)
        verifyNoMoreInteractions(exchangeRates)
        verifyNoMoreInteractions(currencyPrefs)
    }

    companion object {
        private val ASSET = CryptoCurrency.BTC
        private val WRONG_ASSET = CryptoCurrency.ETHER
        private const val SOURCE_XPUB = "VALID_BTC_XPUB"
        private const val TARGET_ADDRESS = "VALID_BTC_ADDRESS"
        private const val FEE_REGULAR = 5L
        private const val FEE_PRIORITY = 11L
        private const val SELECTED_FIAT = "INR"

        private val EXPECTED_AVAILABLE_FEE_LEVELS = setOf(FeeLevel.Regular, FeeLevel.Priority, FeeLevel.Custom)
    }
}
