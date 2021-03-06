package piuk.blockchain.android.coincore.bch

import com.blockchain.android.testutils.rxInit
import com.blockchain.koin.payloadScopeQualifier
import com.blockchain.preferences.CurrencyPrefs
import com.blockchain.preferences.WalletStatus
import com.blockchain.testutils.bitcoinCash
import com.blockchain.testutils.satoshiCash
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
import piuk.blockchain.androidcore.data.bitcoincash.BchDataManager
import piuk.blockchain.androidcore.data.exchangerate.ExchangeRateDataManager
import piuk.blockchain.androidcore.data.fees.FeeDataManager
import piuk.blockchain.androidcore.data.payload.PayloadDataManager
import piuk.blockchain.androidcore.data.payments.SendDataManager

class BchOnChainTxEngineTest {

    @get:Rule
    val initSchedulers = rxInit {
        mainTrampoline()
        ioTrampoline()
        computationTrampoline()
    }

    private val bchDataManager: BchDataManager = mock()
    private val payloadDataManager: PayloadDataManager = mock()
    private val sendDataManager: SendDataManager = mock()
    private val bchNetworkParams: NetworkParameters = mock()

    private val bchFeeOptions: FeeOptions = mock {
        on { regularFee } itReturns FEE_REGULAR
        on { priorityFee } itReturns FEE_PRIORITY
    }
    private val feeManager: FeeDataManager = mock {
        on { bchFeeOptions } itReturns Observable.just(bchFeeOptions)
    }

    private val walletPreferences: WalletStatus = mock {
        on { getFeeTypeForAsset(ASSET) } itReturns FeeLevel.Regular.ordinal
    }
    private val exchangeRates: ExchangeRateDataManager = mock()

    private val currencyPrefs: CurrencyPrefs = mock {
        on { selectedFiatCurrency } itReturns SELECTED_FIAT
    }

    private val subject = BchOnChainTxEngine(
        bchDataManager = bchDataManager,
        payloadDataManager = payloadDataManager,
        sendDataManager = sendDataManager,
        networkParams = bchNetworkParams,
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
        val sourceAccount: BchCryptoWalletAccount = mock {
            on { asset } itReturns ASSET
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
        verify(txTarget).asset
        verify(sourceAccount).asset

        noMoreInteractions(sourceAccount, txTarget)
    }

    @Test(expected = IllegalStateException::class)
    fun `inputs fail validation when source Asset incorrect`() {
        val sourceAccount: BchCryptoWalletAccount = mock {
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
        verify(txTarget).asset
        verify(sourceAccount).asset

        noMoreInteractions(sourceAccount, txTarget)
    }

    @Test
    fun `asset is returned correctly`() {
        // Arrange
        val sourceAccount: BchCryptoWalletAccount = mock {
            on { asset } itReturns ASSET
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

        val asset = subject.asset

        // Assert
        assertEquals(asset, ASSET)
        verify(sourceAccount).asset

        noMoreInteractions(sourceAccount, txTarget)
    }

    @Test
    fun `PendingTx is correctly initialised`() {
        // Arrange
        val sourceAccount: BchCryptoWalletAccount = mock {
            on { asset } itReturns ASSET
        }

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

        verify(currencyPrefs).selectedFiatCurrency

        noMoreInteractions(sourceAccount, txTarget)
    }

    @Test
    fun `update amount modifies the pendingTx correctly for regular fees`() {
        // Arrange
        val inputAmount = 2.bitcoinCash()
        val feePerKb = (FEE_REGULAR * 1000).satoshiCash()
        val totalFee = (FEE_REGULAR * 1000 * 3).satoshiCash()

        val txTarget: CryptoAddress = mock {
            on { asset } itReturns ASSET
            on { address } itReturns TARGET_ADDRESS
        }

        val totalBalance = 21.bitcoinCash()
        val availableBalance = 19.bitcoinCash()
        val totalSweepable = totalBalance - totalFee

        val sourceAccount = fundedSourceAccount(totalBalance, availableBalance)

        whenever(bchDataManager.getAddressBalance(SOURCE_XPUB)).thenReturn(totalBalance)

        val unspentOutputs: UnspentOutputs = mock {
            on { unspentOutputs } itReturns arrayListOf<UnspentOutput>(mock(), mock())
        }
        whenever(sendDataManager.getUnspentBchOutputs(SOURCE_XPUB))
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
            .assertValue {
                it.amount == inputAmount &&
                it.totalBalance == totalBalance &&
                it.availableBalance == totalSweepable &&
                it.fees == totalFee
            }
            .assertValue { verifyFeeLevels(it, FeeLevel.Regular) }
            .assertComplete()
            .assertNoErrors()

        verify(sourceAccount, atLeastOnce()).asset
        verify(sourceAccount).xpubAddress
        verify(sourceAccount).accountBalance
        verify(bchDataManager).getAddressBalance(SOURCE_XPUB)
        verify(feeManager).bchFeeOptions
        verify(bchFeeOptions).regularFee
        verify(unspentOutputs).unspentOutputs
        verify(sendDataManager).getUnspentBchOutputs(SOURCE_XPUB)
        verify(sendDataManager).getMaximumAvailable(ASSET, unspentOutputs, feePerKb)
        verify(utxoBundle).absoluteFee
        verify(sendDataManager).getSpendableCoins(unspentOutputs, inputAmount, feePerKb)

        noMoreInteractions(sourceAccount, txTarget)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `update fee level from REGULAR to PRIORITY is rejected`() {
        // Arrange
        val inputAmount = 2.bitcoinCash()
        val totalFee = (FEE_REGULAR * 1000 * 3).satoshiCash()

        val txTarget: CryptoAddress = mock {
            on { asset } itReturns ASSET
            on { address } itReturns TARGET_ADDRESS
        }

        val totalBalance = 21.bitcoinCash()
        val availableBalance = 19.bitcoinCash()
        val totalSweepable = totalBalance - totalFee

        val sourceAccount = fundedSourceAccount(totalBalance, availableBalance)

        subject.start(
            sourceAccount,
            txTarget,
            exchangeRates
        )

        val pendingTx = PendingTx(
            amount = inputAmount,
            totalBalance = totalBalance,
            availableBalance = totalSweepable,
            fees = totalFee,
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

        noMoreInteractions(sourceAccount, txTarget)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `update fee level from REGULAR to NONE is rejected`() {
        // Arrange
        val inputAmount = 2.bitcoinCash()
        val totalFee = (FEE_REGULAR * 1000 * 3).satoshiCash()

        val txTarget: CryptoAddress = mock {
            on { asset } itReturns ASSET
            on { address } itReturns TARGET_ADDRESS
        }

        val totalBalance = 21.bitcoinCash()
        val availableBalance = 19.bitcoinCash()
        val totalSweepable = totalBalance - totalFee

        val sourceAccount = fundedSourceAccount(totalBalance, availableBalance)

        subject.start(
            sourceAccount,
            txTarget,
            exchangeRates
        )

        val pendingTx = PendingTx(
            amount = inputAmount,
            totalBalance = totalBalance,
            availableBalance = totalSweepable,
            fees = totalFee,
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

    @Test(expected = IllegalArgumentException::class)
    fun `update fee level from REGULAR to CUSTOM is rejected`() {
        // Arrange
        val inputAmount = 2.bitcoinCash()
        val totalFee = (FEE_REGULAR * 1000 * 3).satoshiCash()

        val txTarget: CryptoAddress = mock {
            on { asset } itReturns ASSET
            on { address } itReturns TARGET_ADDRESS
        }

        val totalBalance = 21.bitcoinCash()
        val availableBalance = 19.bitcoinCash()
        val totalSweepable = totalBalance - totalFee

        val sourceAccount = fundedSourceAccount(totalBalance, availableBalance)

        subject.start(
            sourceAccount,
            txTarget,
            exchangeRates
        )

        val pendingTx = PendingTx(
            amount = inputAmount,
            totalBalance = totalBalance,
            availableBalance = totalSweepable,
            fees = totalFee,
            selectedFiat = SELECTED_FIAT,
            feeLevel = FeeLevel.Regular,
            availableFeeLevels = EXPECTED_AVAILABLE_FEE_LEVELS
        )

        // Act
        subject.doUpdateFeeLevel(
            pendingTx,
            FeeLevel.Custom,
            100
        ).test()

        noMoreInteractions(sourceAccount, txTarget)
    }

    @Test
    fun `update fee level from REGULAR to REGULAR has no effect`() {
        // Arrange
        val inputAmount = 2.bitcoinCash()
        val totalFee = (FEE_REGULAR * 1000 * 3).satoshiCash()

        val txTarget: CryptoAddress = mock {
            on { asset } itReturns ASSET
            on { address } itReturns TARGET_ADDRESS
        }

        val totalBalance = 21.bitcoinCash()
        val availableBalance = 19.bitcoinCash()
        val totalSweepable = totalBalance - totalFee

        val sourceAccount = fundedSourceAccount(totalBalance, availableBalance)

        subject.start(
            sourceAccount,
            txTarget,
            exchangeRates
        )

        val pendingTx = PendingTx(
            amount = inputAmount,
            totalBalance = totalBalance,
            availableBalance = totalSweepable,
            fees = totalFee,
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
            .assertValue {
                it.amount == inputAmount &&
                it.totalBalance == totalBalance &&
                it.availableBalance == totalSweepable &&
                it.fees == totalFee
            }
            .assertValue { verifyFeeLevels(it, FeeLevel.Regular) }
            .assertComplete()
            .assertNoErrors()

        noMoreInteractions(sourceAccount, txTarget)
    }

    private fun fundedSourceAccount(totalBalance: Money, availableBalance: Money) =
        mock<BchCryptoWalletAccount> {
            on { asset } itReturns ASSET
            on { accountBalance } itReturns Single.just(totalBalance)
            on { actionableBalance } itReturns Single.just(availableBalance)
            on { xpubAddress } itReturns SOURCE_XPUB
        }

    private fun verifyFeeLevels(pendingTx: PendingTx, expectedLevel: FeeLevel) =
        pendingTx.feeLevel == expectedLevel &&
            pendingTx.availableFeeLevels == EXPECTED_AVAILABLE_FEE_LEVELS &&
            pendingTx.availableFeeLevels.contains(pendingTx.feeLevel)

    private fun noMoreInteractions(sourceAccount: BlockchainAccount, txTarget: TransactionTarget) {
        verifyNoMoreInteractions(txTarget)
        verifyNoMoreInteractions(bchDataManager)
        verifyNoMoreInteractions(sendDataManager)
        verifyNoMoreInteractions(bchNetworkParams)
        verifyNoMoreInteractions(feeManager)
        verifyNoMoreInteractions(bchFeeOptions)
        verifyNoMoreInteractions(walletPreferences)
        verifyNoMoreInteractions(sourceAccount)
        verifyNoMoreInteractions(exchangeRates)
        verifyNoMoreInteractions(currencyPrefs)
    }

    companion object {
        private val ASSET = CryptoCurrency.BCH
        private val WRONG_ASSET = CryptoCurrency.ETHER
        private const val SOURCE_XPUB = "VALID_BCH_XPUB"
        private const val TARGET_ADDRESS = "VALID_BCH_ADDRESS"
        private const val FEE_REGULAR = 5L
        private const val FEE_PRIORITY = 11L
        private const val SELECTED_FIAT = "INR"

        private val EXPECTED_AVAILABLE_FEE_LEVELS = setOf(FeeLevel.Regular)
    }
}
