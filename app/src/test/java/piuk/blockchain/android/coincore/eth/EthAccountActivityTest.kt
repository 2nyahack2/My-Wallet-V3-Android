package piuk.blockchain.android.coincore.eth

import com.blockchain.android.testutils.rxInit
import com.blockchain.preferences.CurrencyPrefs
import com.blockchain.preferences.WalletStatus
import com.blockchain.nabu.datamanagers.CurrencyPair
import com.blockchain.nabu.datamanagers.CustodialWalletManager
import com.blockchain.nabu.datamanagers.TransferDirection
import com.blockchain.nabu.datamanagers.CustodialOrderState
import com.blockchain.nabu.datamanagers.repositories.swap.TradeTransactionItem
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import info.blockchain.balance.CryptoCurrency
import info.blockchain.balance.CryptoValue
import info.blockchain.balance.FiatValue
import info.blockchain.wallet.ethereum.data.EthLatestBlockNumber
import info.blockchain.wallet.ethereum.data.EthTransaction
import io.reactivex.Single
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.spy
import piuk.blockchain.android.coincore.NonCustodialActivitySummaryItem
import piuk.blockchain.android.coincore.TradeActivitySummaryItem
import piuk.blockchain.androidcore.data.ethereum.EthDataManager
import piuk.blockchain.androidcore.data.ethereum.models.CombinedEthModel
import piuk.blockchain.androidcore.data.exchangerate.ExchangeRateDataManager
import piuk.blockchain.androidcore.data.fees.FeeDataManager
import piuk.blockchain.androidcore.data.payload.PayloadDataManager

class EthAccountActivityTest {

    private val payloadManager: PayloadDataManager = mock()
    private val ethDataManager: EthDataManager = mock()
    private val feeDataManager: FeeDataManager = mock()
    private val exchangeRates: ExchangeRateDataManager = mock()
    private val currencyPrefs: CurrencyPrefs = mock()
    private val walletPrefs: WalletStatus = mock()
    private val custodialWalletManager: CustodialWalletManager = mock()

    private val subject =
        spy(EthCryptoWalletAccount(
            payloadManager = payloadManager,
            label = "TestEthAccount",
            address = ETH_ADDRESS,
            ethDataManager = ethDataManager,
            fees = feeDataManager,
            exchangeRates = exchangeRates,
            walletPreferences = walletPrefs,
            custodialWalletManager = custodialWalletManager
        ))

    @get:Rule
    val rxSchedulers = rxInit {
        mainTrampoline()
        ioTrampoline()
        computationTrampoline()
    }

    @Before
    fun setup() {
        whenever(currencyPrefs.selectedFiatCurrency).thenReturn("USD")
    }

    @Test
    fun fetchTransactionsEthereumReplaceSendWithSwap() {
        // Arrange
        val latestBlock = EthLatestBlockNumber()
        val transaction = EthTransaction(
            hash = "hash",
            from = ETH_ADDRESS,
            to = "122345"
        )

        val ethModel: CombinedEthModel = mock()

        whenever(ethDataManager.getLatestBlockNumber())
            .thenReturn(Single.just(latestBlock))

        whenever(ethDataManager.getEthTransactions())
            .thenReturn(Single.just(listOf(transaction)))

        whenever(ethDataManager.getEthResponseModel())
            .thenReturn(ethModel)

        val swapSummary = TradeTransactionItem(
            "hash",
            1L,
            TransferDirection.ON_CHAIN,
            "sendingAddress",
            "receivingAddress",
            CustodialOrderState.FINISHED,
            CryptoValue.ZeroEth,
            CryptoValue.ZeroBtc,
            CryptoValue.ZeroBtc,
            CurrencyPair.CryptoCurrencyPair(CryptoCurrency.ETHER, CryptoCurrency.BTC),
            FiatValue.zero("USD"),
            "USD"
        )

        val summaryList = listOf(swapSummary)
        whenever(custodialWalletManager.getCustodialActivityForAsset(any(), any()))
            .thenReturn(Single.just(summaryList))

        doReturn(false).`when`(subject).isErc20FeeTransaction(any())

        subject.activity
            .test()
            .assertComplete()
            .assertNoErrors()
            .assertValue {
                it.size == 1 &&
                        it[0].run {
                            this is TradeActivitySummaryItem &&
                                    txId == swapSummary.txId &&
                                    direction == swapSummary.direction &&
                                    currencyPair == CurrencyPair.CryptoCurrencyPair(CryptoCurrency.ETHER,
                                CryptoCurrency.BTC) &&
                                    sendingAddress == swapSummary.sendingAddress &&
                                    receivingAddress == swapSummary.receivingAddress &&
                                    state == swapSummary.state &&
                                    fiatValue == swapSummary.fiatValue &&
                                    fiatCurrency == swapSummary.fiatCurrency
                        }
            }

        verify(ethDataManager).getLatestBlockNumber()
        verify(ethDataManager).getEthTransactions()
    }

    @Test
    fun fetchTransactionsEthereumReceive() {
        // Arrange
        val latestBlock = EthLatestBlockNumber()
        val transaction: EthTransaction = mock()

        val to = "12345"
        whenever(transaction.hash).thenReturn("hash")
        whenever(transaction.to).thenReturn(to)

        val ethModel: CombinedEthModel = mock()

        whenever(ethDataManager.getLatestBlockNumber())
            .thenReturn(Single.just(latestBlock))

        whenever(ethDataManager.getEthTransactions())
            .thenReturn(Single.just(listOf(transaction)))

        whenever(ethDataManager.getEthResponseModel())
            .thenReturn(ethModel)

        val swapSummary = TradeTransactionItem(
            "123",
            1L,
            TransferDirection.ON_CHAIN,
            "sendingAddress",
            "receivingAddress",
            CustodialOrderState.FINISHED,
            CryptoValue.ZeroEth,
            CryptoValue.ZeroBtc,
            CryptoValue.ZeroBtc,
            CurrencyPair.CryptoCurrencyPair(CryptoCurrency.ETHER, CryptoCurrency.BTC),
            FiatValue.zero("USD"),
            "USD"
        )

        val summaryList = listOf(swapSummary)
        whenever(custodialWalletManager.getCustodialActivityForAsset(any(), any()))
            .thenReturn(Single.just(summaryList))

        doReturn(false).`when`(subject).isErc20FeeTransaction(to)

        subject.activity
            .test()
            .assertComplete()
            .assertNoErrors()
            .assertValue {
                it.size == 1 &&
                        it[0].run {
                            this is EthActivitySummaryItem
                        }
            }

        verify(ethDataManager).getLatestBlockNumber()
        verify(ethDataManager).getEthTransactions()
    }

    @Test
    fun getEthTransactionsListWithOneErc20FeeTransactionInTheList() {
        // Arrange
        val ethTransaction = EthTransaction(
            to = "0x8E870D67F660D95d5be530380D0eC0bd388289E1"
        )

        whenever(ethDataManager.getLatestBlockNumber())
            .thenReturn(Single.just(EthLatestBlockNumber()))

        whenever(ethDataManager.getEthResponseModel())
            .thenReturn(mock())
        whenever(ethDataManager.getEthTransactions())
            .thenReturn(Single.just(listOf(ethTransaction)))

        doReturn(true).`when`(subject).isErc20FeeTransaction(ethTransaction.to)

        whenever(custodialWalletManager.getCustodialActivityForAsset(any(), any()))
            .thenReturn(Single.just(emptyList()))

        subject.activity
            .test()
            .assertValueCount(1)
            .assertComplete()
            .assertNoErrors()
            .assertValue {
                it.size == 1
            }
            .assertValue {
                (it[0] as NonCustodialActivitySummaryItem).isFeeTransaction
            }

        verify(ethDataManager).getEthTransactions()
    }

    @Test
    fun getEthTransactionsListWithNoErc20FeeTransactionInTheList() {
        // Arrange
        val ethTransaction = EthTransaction(
            to = "0x8E870D67F660D95d5be530380D0eC0bd388289E1"
        )

        whenever(ethDataManager.getLatestBlockNumber())
            .thenReturn(Single.just(EthLatestBlockNumber()))
        doReturn(false).`when`(subject).isErc20FeeTransaction(ethTransaction.to)

        whenever(ethDataManager.getEthResponseModel())
            .thenReturn(mock())
        whenever(ethDataManager.getEthTransactions())
            .thenReturn(Single.just(listOf(ethTransaction)))

        whenever(custodialWalletManager.getCustodialActivityForAsset(any(), any()))
            .thenReturn(Single.just(emptyList()))

        subject.activity
            .test()
            .assertValueCount(1)
            .assertComplete()
            .assertNoErrors()
            .assertValue {
                it.size == 1
            }
            .assertValue {
                !(it[0] as NonCustodialActivitySummaryItem).isFeeTransaction
            }

        verify(ethDataManager).getEthTransactions()
    }

    companion object {
        private const val ETH_ADDRESS = "test_address"
    }
}
