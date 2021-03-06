package piuk.blockchain.android.ui.account

import android.annotation.SuppressLint
import com.blockchain.preferences.CurrencyPrefs
import com.blockchain.testutils.satoshi
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import info.blockchain.wallet.payload.data.LegacyAddress
import info.blockchain.wallet.payload.data.Wallet
import io.reactivex.Completable
import io.reactivex.Observable
import org.amshove.kluent.any
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyList
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.RETURNS_DEEP_STUBS
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import piuk.blockchain.android.R
import piuk.blockchain.android.ui.backup.transfer.ConfirmFundsTransferPresenter
import piuk.blockchain.android.ui.backup.transfer.ConfirmFundsTransferView
import piuk.blockchain.android.util.StringUtils
import piuk.blockchain.android.ui.chooser.WalletAccountHelper
import piuk.blockchain.androidcore.data.events.PayloadSyncedEvent
import piuk.blockchain.androidcore.data.events.PaymentFailedEvent
import piuk.blockchain.androidcore.data.events.PaymentSentEvent
import piuk.blockchain.androidcore.data.exchangerate.ExchangeRateDataManager
import piuk.blockchain.androidcore.data.payload.PayloadDataManager
import piuk.blockchain.androidcoreui.ui.customviews.ToastCustom
import java.util.Locale

class ConfirmFundsTransferPresenterTest {

    private val view: ConfirmFundsTransferView = mock()
    private val walletAccountHelper: WalletAccountHelper = mock()
    private val transferFundsDataManager: TransferFundsDataManager = mock()
    private val payloadDataManager: PayloadDataManager = mock()
    private val stringUtils: StringUtils = mock()
    private val exchangeRates: ExchangeRateDataManager = mock()
    private val currencyPrefs: CurrencyPrefs = mock {
        on { selectedFiatCurrency }.thenReturn("USD")
    }

    private val subject = ConfirmFundsTransferPresenter(
        walletAccountHelper,
        transferFundsDataManager,
        payloadDataManager,
        stringUtils,
        exchangeRates,
        currencyPrefs
    )

    @Before
    fun setUp() {
        subject.initView(view)
        Locale.setDefault(Locale.US)
    }

    @Test
    fun onViewReady() {
        // Arrange
        val mockPayload = mock(Wallet::class.java, RETURNS_DEEP_STUBS)
        whenever(payloadDataManager.wallet).thenReturn(mockPayload)
        whenever(mockPayload.hdWallets[0].defaultAccountIdx).thenReturn(0)

        val transaction = PendingTransaction()
        val transactions = listOf(transaction, transaction)

        val result = TransferableFundTransactionList(
            pendingTransactions = transactions,
            totalToSend = 100000000.toBigInteger(),
            totalFee = 10000.toBigInteger()
        )

        whenever(transferFundsDataManager.getTransferableFundTransactionList(0))
            .thenReturn(Observable.just(result))

        // Act
        subject.onViewReady()

        // Assert
        verify(view).setPaymentButtonEnabled(false)
        assertEquals(2, subject.pendingTransactions.size)
    }

    @Test
    fun `accountSelected error`() {
        // Arrange
        whenever(payloadDataManager.getPositionOfAccountFromActiveList(0)).thenReturn(1)
        whenever(transferFundsDataManager.getTransferableFundTransactionList(1))
            .thenReturn(Observable.error(Throwable()))

        // Act
        subject.accountSelected(0)
        // Assert
        verify(view).setPaymentButtonEnabled(false)
        verify(view).showToast(R.string.unexpected_error, ToastCustom.TYPE_ERROR)
        verify(view).dismissDialog()
    }

    @SuppressLint("VisibleForTests")
    @Test
    fun updateUi() {
        // Arrange
        val total = 100000000.satoshi()
        val fee = 10000.satoshi()

        whenever(stringUtils.getQuantityString(anyInt(), anyInt())).thenReturn("test string")
        whenever(exchangeRates.getLastPrice(any(), any())).thenReturn(100.0)

        // Act
        subject.updateUi(total, fee)

        // Assert
        verify(view).updateFromLabel("test string")
        verify(view).updateTransferAmountBtc("1.0 BTC")
        verify(view).updateTransferAmountFiat("$100.00")
        verify(view).updateFeeAmountBtc("0.0001 BTC")
        verify(view).updateFeeAmountFiat("$0.01")
        verify(view).setPaymentButtonEnabled(true)
        verify(view).onUiUpdated()
        verifyNoMoreInteractions(view)
    }

    @Test
    fun `sendPayment and archive`() {
        // Arrange
        whenever(transferFundsDataManager.sendPayment(anyList<PendingTransaction>(), anyString()))
            .thenReturn(Observable.just("hash"))
        whenever(view.getIfArchiveChecked()).thenReturn(true)

        val transaction = PendingTransaction()
        transaction.sendingObject = ItemAccount()
        transaction.sendingObject!!.accountObject = LegacyAddress()
        subject.pendingTransactions.addAll(mutableListOf(transaction))

        whenever(payloadDataManager.syncPayloadWithServer()).thenReturn(Completable.complete())

        // Act
        subject.sendPayment("password")
        // Assert
        verify(view).getIfArchiveChecked()
        verify(view).setPaymentButtonEnabled(false)
        verify(view, times(2)).showProgressDialog()
        verify(view, times(2)).hideProgressDialog()
        verify(view).showToast(R.string.transfer_confirmed, ToastCustom.TYPE_OK)
        verify(view).showToast(R.string.transfer_archive, ToastCustom.TYPE_OK)
        verify(view).dismissDialog()
        verify(view).sendBroadcast(any<PayloadSyncedEvent>())
        verifyNoMoreInteractions(view)
    }

    @Test
    fun `sendPayment no archive`() {
        // Arrange
        whenever(transferFundsDataManager.sendPayment(anyList<PendingTransaction>(), anyString()))
            .thenReturn(Observable.just("hash"))
        whenever(view.getIfArchiveChecked()).thenReturn(false)
        // Act
        subject.sendPayment("password")
        // Assert
        verify(view).getIfArchiveChecked()
        verify(view).setPaymentButtonEnabled(false)
        verify(view).showProgressDialog()
        verify(view).hideProgressDialog()
        verify(view).showToast(R.string.transfer_confirmed, ToastCustom.TYPE_OK)
        verify(view).sendBroadcast(any<PaymentSentEvent>())
        verify(view).dismissDialog()
        verifyNoMoreInteractions(view)
    }

    @Test
    fun `sendPayment error`() {
        // Arrange
        whenever(transferFundsDataManager.sendPayment(anyList<PendingTransaction>(), anyString()))
            .thenReturn(Observable.error<String>(Throwable()))
        whenever(view.getIfArchiveChecked()).thenReturn(false)
        // Act
        subject.sendPayment("password")
        // Assert
        verify(view).getIfArchiveChecked()
        verify(view).setPaymentButtonEnabled(false)
        verify(view).showProgressDialog()
        verify(view).hideProgressDialog()
        verify(view).showToast(R.string.unexpected_error, ToastCustom.TYPE_ERROR)
        verify(view).dismissDialog()
        verify(view).sendBroadcast(any<PaymentFailedEvent>())
        verifyNoMoreInteractions(view)
    }

    @Test
    fun getReceiveToList() {
        // Arrange
        whenever(walletAccountHelper.getHdAccounts()).thenReturn(listOf())
        // Act
        val value = subject.getReceiveToList()
        // Assert
        assertNotNull(value)
        assertTrue(value.isEmpty())
    }

    @Test
    fun getDefaultAccount() {
        // Arrange
        whenever(payloadDataManager.defaultAccountIndex).thenReturn(0)
        whenever(payloadDataManager.getPositionOfAccountFromActiveList(0)).thenReturn(1)
        // Act
        val value = subject.getDefaultAccount()
        // Assert
        assertEquals(0, value.toLong())
    }

    @Test
    fun `archiveAll successful`() {
        // Arrange
        val transaction = PendingTransaction()
        transaction.sendingObject = ItemAccount()
        transaction.sendingObject!!.accountObject = LegacyAddress()
        subject.pendingTransactions.addAll(listOf(transaction))

        whenever(payloadDataManager.syncPayloadWithServer()).thenReturn(Completable.complete())
        // Act
        subject.archiveAll()
        // Assert
        verify(view).showProgressDialog()
        verify(view).hideProgressDialog()
        verify(view).showToast(R.string.transfer_archive, ToastCustom.TYPE_OK)
        verify(view).dismissDialog()
        verify(view).sendBroadcast(any<PayloadSyncedEvent>())
        verifyNoMoreInteractions(view)
    }

    @Test
    fun `archiveAll unsuccessful`() {
        // Arrange
        val transaction = PendingTransaction()
        transaction.sendingObject = ItemAccount()
        transaction.sendingObject!!.accountObject = LegacyAddress()
        subject.pendingTransactions.addAll(listOf(transaction))

        whenever(payloadDataManager.syncPayloadWithServer()).thenReturn(Completable.error(Throwable()))
        // Act
        subject.archiveAll()
        // Assert
        verify(view).showProgressDialog()
        verify(view).hideProgressDialog()
        verify(view).showToast(R.string.unexpected_error, ToastCustom.TYPE_ERROR)
        verify(view).dismissDialog()
        verify(view).sendBroadcast(any<PayloadSyncedEvent>())
        verifyNoMoreInteractions(view)
    }
}