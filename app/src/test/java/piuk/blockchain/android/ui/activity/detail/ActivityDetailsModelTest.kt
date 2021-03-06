package piuk.blockchain.android.ui.activity.detail

import com.blockchain.android.testutils.rxInit
import com.blockchain.nabu.datamanagers.InterestState
import com.blockchain.nabu.datamanagers.OrderState
import com.blockchain.nabu.datamanagers.custodialwalletimpl.OrderType
import com.blockchain.nabu.datamanagers.custodialwalletimpl.PaymentMethodType
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import info.blockchain.balance.CryptoCurrency
import info.blockchain.balance.CryptoValue
import info.blockchain.wallet.multiaddress.TransactionSummary
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import piuk.blockchain.android.coincore.CryptoAccount
import piuk.blockchain.android.coincore.CustodialInterestActivitySummaryItem
import piuk.blockchain.android.coincore.CustodialTradingActivitySummaryItem
import piuk.blockchain.android.coincore.NonCustodialActivitySummaryItem
import piuk.blockchain.android.ui.activity.CryptoActivityType
import piuk.blockchain.androidcore.data.exchangerate.ExchangeRateDataManager
import java.util.Date

class ActivityDetailsModelTest {

    private lateinit var model: ActivityDetailsModel
    private var state = ActivityDetailState()
    private val interactor: ActivityDetailsInteractor = mock()

    private data class NonCustodialTestClass(
        override val exchangeRates: ExchangeRateDataManager = mock(),
        override val cryptoCurrency: CryptoCurrency = mock(),
        override val txId: String = "123",
        override val timeStampMs: Long = 1L,
        override val value: CryptoValue = mock(),
        override val transactionType: TransactionSummary.TransactionType = TransactionSummary.TransactionType.SENT,
        override val fee: Observable<CryptoValue> = mock(),
        override val inputsMap: Map<String, CryptoValue> = mock(),
        override val outputsMap: Map<String, CryptoValue> = mock(),
        override val description: String? = "desc",
        override val account: CryptoAccount = mock()
    ) : NonCustodialActivitySummaryItem()

    private val custodialItem = CustodialTradingActivitySummaryItem(
        exchangeRates = mock(),
        cryptoCurrency = mock(),
        txId = "123",
        timeStampMs = 1L,
        value = CryptoValue.zero(CryptoCurrency.BTC),
        fundedFiat = mock(),
        status = OrderState.FINISHED,
        fee = mock(),
        account = mock(),
        paymentMethodId = "123",
        paymentMethodType = PaymentMethodType.PAYMENT_CARD,
        type = OrderType.BUY,
        depositPaymentId = ""
    )

    private val custodialInterestItem = CustodialInterestActivitySummaryItem(
        exchangeRates = mock(),
        cryptoCurrency = mock(),
        txId = "123",
        timeStampMs = 1L,
        value = CryptoValue.zero(CryptoCurrency.BTC),
        status = InterestState.COMPLETE,
        account = mock(),
        type = TransactionSummary.TransactionType.INTEREST_EARNED,
        confirmations = 0,
        accountRef = "",
        recipientAddress = ""
    )

    @get:Rule
    val rx = rxInit {
        ioTrampoline()
        computationTrampoline()
    }

    @Before
    fun setup() {
        model = ActivityDetailsModel(state, Schedulers.io(), interactor)
    }

    @Test
    fun `starting the model with non custodial item loads non custodial details`() {
        val item = NonCustodialTestClass()
        val crypto = CryptoCurrency.BCH
        val txId = "123455"
        whenever(interactor.getNonCustodialActivityDetails(crypto, txId)).thenReturn(item)

        model.process(LoadActivityDetailsIntent(crypto, txId, CryptoActivityType.NON_CUSTODIAL))

        verify(interactor).getNonCustodialActivityDetails(crypto, txId)
    }

    @Test
    fun `starting the model with custodial item loads custodial details`() {
        val crypto = CryptoCurrency.BCH
        val txId = "123455"
        whenever(interactor.getCustodialTradingActivityDetails(crypto, txId)).thenReturn(custodialItem)
        whenever(interactor.loadCustodialTradingItems(custodialItem)).thenReturn(
            Single.just(emptyList())
        )

        model.process(LoadActivityDetailsIntent(crypto, txId, CryptoActivityType.CUSTODIAL_TRADING))

        verify(interactor).getCustodialTradingActivityDetails(crypto, txId)
    }

    @Test
    fun `starting the model with custodial interest item loads custodial details`() {
        val crypto = CryptoCurrency.BCH
        val txId = "123455"
        whenever(interactor.getCustodialInterestActivityDetails(crypto, txId)).thenReturn(custodialInterestItem)
        whenever(interactor.loadCustodialInterestItems(custodialInterestItem)).thenReturn(
            Single.just(emptyList())
        )

        model.process(LoadActivityDetailsIntent(crypto, txId, CryptoActivityType.CUSTODIAL_INTEREST))

        verify(interactor).getCustodialInterestActivityDetails(crypto, txId)
    }

    @Test
    fun `processing non custodial item loads header details correctly`() {
        val item = NonCustodialTestClass()

        val testObserver = model.state.test()
        model.process(LoadNonCustodialHeaderDataIntent(item))

        testObserver.assertValueAt(0, state)
        testObserver.assertValueAt(1, state.copy(
            transactionType = item.transactionType,
            amount = item.value,
            isPending = item.isPending,
            isFeeTransaction = item.isFeeTransaction,
            confirmations = item.confirmations,
            totalConfirmations = item.cryptoCurrency.requiredConfirmations
        ))
    }

    @Test
    fun `processing custodial item loads header details correctly`() {
        val testObserver = model.state.test()
        model.process(LoadCustodialTradingHeaderDataIntent(custodialItem))

        testObserver.assertValueAt(0, state)
        testObserver.assertValueAt(1, state.copy(
            transactionType = TransactionSummary.TransactionType.BUY,
            amount = custodialItem.value as CryptoValue,
            isPending = false,
            isFeeTransaction = false,
            confirmations = 0,
            totalConfirmations = 0
        ))
    }

    @Test
    fun `processing creation date returns correct values`() {
        val item = NonCustodialTestClass()
        val returnDate = Date()
        whenever(interactor.loadCreationDate(item)).thenReturn(returnDate)
        whenever(interactor.loadConfirmedSentItems(item)).thenReturn(Single.just(listOf()))

        val testObserver = model.state.test()
        model.process(LoadNonCustodialCreationDateIntent(item))

        verify(interactor).loadCreationDate(item)

        val list = state.listOfItems.toMutableSet()
        list.add(Created(returnDate))
        testObserver.assertValueAt(0, state)
        testObserver.assertValueAt(1, state.copy(listOfItems = list))
    }

    @Test
    fun `failing to load non custodial details updates state correctly`() {
        val crypto = CryptoCurrency.BCH
        val txId = "123455"
        whenever(interactor.getNonCustodialActivityDetails(crypto, txId)).thenReturn(null)

        val testObserver = model.state.test()
        model.process(LoadActivityDetailsIntent(crypto, txId, CryptoActivityType.NON_CUSTODIAL))

        testObserver.assertValueAt(0, state)
        testObserver.assertValueAt(1, state.copy(isError = true))
    }

    @Test
    fun `failing to load custodial details updates state correctly`() {
        val crypto = CryptoCurrency.BCH
        val txId = "123455"
        whenever(interactor.getCustodialTradingActivityDetails(crypto, txId)).thenReturn(null)

        val testObserver = model.state.test()
        model.process(LoadActivityDetailsIntent(crypto, txId, CryptoActivityType.CUSTODIAL_TRADING))

        testObserver.assertValueAt(0, state)
        testObserver.assertValueAt(1, state.copy(isError = true))
    }

    @Test
    fun `failing to load custodial interest details updates state correctly`() {
        val crypto = CryptoCurrency.BCH
        val txId = "123455"
        whenever(interactor.getCustodialInterestActivityDetails(crypto, txId)).thenReturn(null)

        val testObserver = model.state.test()
        model.process(LoadActivityDetailsIntent(crypto, txId, CryptoActivityType.CUSTODIAL_INTEREST))

        testObserver.assertValueAt(0, state)
        testObserver.assertValueAt(1, state.copy(isError = true))
    }

    @Test
    fun `activity items load correctly`() {
        val list = listOf(Fee(mock()), Amount(mock()), To(""), From(""))

        val currentList = state.listOfItems.toMutableSet()
        currentList.addAll(list.toSet())

        val testObserver = model.state.test()
        model.process(ListItemsLoadedIntent(list))

        testObserver.assertValueAt(0, state)
        testObserver.assertValueAt(1, state.copy(
            listOfItems = currentList
        ))
    }
}