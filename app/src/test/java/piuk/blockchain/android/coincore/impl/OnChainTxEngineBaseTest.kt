package piuk.blockchain.android.coincore.impl

import com.blockchain.android.testutils.rxInit
import com.blockchain.koin.payloadScopeQualifier
import com.blockchain.preferences.CurrencyPrefs
import com.blockchain.preferences.WalletStatus
import com.nhaarman.mockito_kotlin.atLeastOnce
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.verifyNoMoreInteractions
import com.nhaarman.mockito_kotlin.whenever
import info.blockchain.balance.CryptoCurrency
import info.blockchain.balance.CryptoValue
import info.blockchain.balance.Money
import io.reactivex.Completable
import io.reactivex.Single
import org.amshove.kluent.mock
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.Module
import org.koin.dsl.module
import kotlin.test.assertEquals
import piuk.blockchain.android.coincore.CryptoAccount
import piuk.blockchain.android.coincore.PendingTx
import piuk.blockchain.android.coincore.TransactionTarget
import piuk.blockchain.android.coincore.TxConfirmationValue
import piuk.blockchain.android.coincore.TxEngine
import piuk.blockchain.android.coincore.TxResult
import piuk.blockchain.android.coincore.impl.txEngine.OnChainTxEngineBase
import piuk.blockchain.androidcore.data.exchangerate.ExchangeRateDataManager

@Suppress("TestFunctionName")
fun STUB_THIS(): Nothing = throw NotImplementedError("This method should be moccked")

fun injectMocks(module: Module) {
    startKoin {
        modules(
            listOf(
                module
            )
        )
    }
}

class OnChainTxEngineBaseTest {

    @get:Rule
    val initSchedulers = rxInit {
        mainTrampoline()
        ioTrampoline()
        computationTrampoline()
    }

    private val walletPreferences: WalletStatus = mock()
    private val sourceAccount: CryptoAccount = mock()
    private val txTarget: TransactionTarget = mock()
    private val exchangeRates: ExchangeRateDataManager = mock()
    private val currencyPrefs: CurrencyPrefs = mock()

    private class OnChainTxEngineTestSubject(
        requireSecondPassword: Boolean,
        walletPreferences: WalletStatus
    ) : OnChainTxEngineBase(
        requireSecondPassword,
        walletPreferences
    ) {
        override fun doBuildConfirmations(pendingTx: PendingTx): Single<PendingTx> {
            STUB_THIS()
        }
        override fun doInitialiseTx(): Single<PendingTx> {
            STUB_THIS()
        }
        override fun doUpdateAmount(amount: Money, pendingTx: PendingTx): Single<PendingTx> {
            STUB_THIS()
        }

        override fun doValidateAmount(pendingTx: PendingTx): Single<PendingTx> {
            STUB_THIS()
        }
        override fun doValidateAll(pendingTx: PendingTx): Single<PendingTx> {
            STUB_THIS()
        }
        override fun doExecute(pendingTx: PendingTx, secondPassword: String): Single<TxResult> {
            STUB_THIS()
        }

        override fun makeFeeSelectionOption(pendingTx: PendingTx): TxConfirmationValue.FeeSelection {
            TODO("Not yet implemented")
        }
    }

    private val subject: OnChainTxEngineBase =
        OnChainTxEngineTestSubject(
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
    fun `asset is returned correctly`() {
        // Arrange
        whenever(sourceAccount.asset).thenReturn(ASSET)

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

        noMoreInteractions()
    }

    @Test
    fun `userFiat returns value from stored prefs`() {
        // Arrange
        whenever(currencyPrefs.selectedFiatCurrency).thenReturn(SELECTED_FIAT)

        // Act
        val result = subject.userFiat

        assertEquals(result, SELECTED_FIAT)
        verify(currencyPrefs).selectedFiatCurrency

        noMoreInteractions()
    }

    @Test
    fun `exchange rate stream is returned`() {
        // Arrange
        whenever(sourceAccount.asset).thenReturn(ASSET)
        whenever(currencyPrefs.selectedFiatCurrency).thenReturn(SELECTED_FIAT)
        whenever(exchangeRates.getLastPrice(ASSET, SELECTED_FIAT)).thenReturn(EXCHANGE_RATE)

        // Act
        subject.start(
            sourceAccount,
            txTarget,
            exchangeRates
        )

        subject.userExchangeRate()
            .test()
            .assertValueAt(0) {
                it.rate == EXCHANGE_RATE
            }
            .assertComplete()
            .assertNoErrors()

        verify(sourceAccount, atLeastOnce()).asset
        verify(currencyPrefs).selectedFiatCurrency
        verify(exchangeRates).getLastPrice(ASSET, SELECTED_FIAT)

        noMoreInteractions()
    }

    @Test
    fun `confirmations are refreshed`() {
        // Arrange
        val balance = CryptoValue.fromMajor(ASSET, 10.1.toBigDecimal())
        whenever(sourceAccount.accountBalance).thenReturn(Single.just(balance))

        val refreshTrigger = object : TxEngine.RefreshTrigger {
            override fun refreshConfirmations(revalidate: Boolean): Completable =
                Completable.fromAction { sourceAccount.accountBalance }
        }

        // Act
        subject.start(
            sourceAccount,
            txTarget,
            exchangeRates,
            refreshTrigger
        )

        subject.refreshConfirmations(false)

        // Assert
        verify(sourceAccount).accountBalance

        noMoreInteractions()
    }

    private fun noMoreInteractions() {
        verifyNoMoreInteractions(walletPreferences)
        verifyNoMoreInteractions(sourceAccount)
        verifyNoMoreInteractions(txTarget)
        verifyNoMoreInteractions(exchangeRates)
        verifyNoMoreInteractions(currencyPrefs)
    }

    companion object {
        private val ASSET = CryptoCurrency.ETHER
        private const val SELECTED_FIAT = "INR"
        private val EXCHANGE_RATE = 0.01.toBigDecimal()
    }
}
