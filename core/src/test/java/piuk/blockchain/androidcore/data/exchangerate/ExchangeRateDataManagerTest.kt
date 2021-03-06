package piuk.blockchain.androidcore.data.exchangerate

import com.blockchain.testutils.bitcoin
import com.blockchain.testutils.bitcoinCash
import com.blockchain.testutils.ether
import com.blockchain.testutils.lumens
import com.blockchain.testutils.rxInit
import com.blockchain.testutils.usd
import com.nhaarman.mockito_kotlin.whenever
import info.blockchain.balance.CryptoCurrency
import io.reactivex.Single
import org.amshove.kluent.`should equal`
import org.amshove.kluent.mock
import org.junit.Before
import org.junit.Rule
import piuk.blockchain.androidcore.data.exchangerate.datastore.ExchangeRateDataStore
import piuk.blockchain.androidcore.data.rxjava.RxBus
import java.math.BigDecimal
import kotlin.test.Test

class ExchangeRateDataManagerTest {

    private lateinit var subject: ExchangeRateDataManager
    private val exchangeRateDataStore: ExchangeRateDataStore = mock()
    private val rxBus: RxBus = mock()

    @get:Rule
    val initSchedulers = rxInit {
        ioTrampoline()
    }

    @Before
    fun setUp() {
        subject = ExchangeRateDataManager(
            exchangeRateDataStore,
            rxBus
        )
    }

    @Test
    fun getHistoricPrice() {
        givenHistoricExchangeRate(CryptoCurrency.BTC, "USD", 100L, 8100.37.toBigDecimal())
        subject.getHistoricPrice(1.bitcoin(), "USD", 100L)
            .test()
            .values()
            .first()
            .apply {
                this `should equal` 8100.37.usd()
            }
    }

    @Test
    fun `BTC toFiat`() {
        givenExchangeRate(CryptoCurrency.BTC, "USD", 5000.0)

        0.01.bitcoin().toFiat(subject, "USD") `should equal` 50.usd()
    }

    @Test
    fun `BCH toFiat`() {
        givenExchangeRate(CryptoCurrency.BCH, "USD", 1000.0)

        0.1.bitcoinCash().toFiat(subject, "USD") `should equal` 100.usd()
    }

    @Test
    fun `ETH toFiat`() {
        givenExchangeRate(CryptoCurrency.ETHER, "USD", 1000.0)

        2.ether().toFiat(subject, "USD") `should equal` 2000.usd()
    }

    @Test
    fun `USD toCrypto BTC`() {
        givenExchangeRate(CryptoCurrency.BTC, "USD", 5000.0)

        50.usd().toCrypto(subject, CryptoCurrency.BTC) `should equal` 0.01.bitcoin()
    }

    @Test
    fun `USD toCrypto BCH`() {
        givenExchangeRate(CryptoCurrency.BCH, "USD", 1000.0)

        100.usd().toCrypto(subject, CryptoCurrency.BCH) `should equal` 0.1.bitcoinCash()
    }

    @Test
    fun `USD toCrypto ETHER`() {
        givenExchangeRate(CryptoCurrency.ETHER, "USD", 1000.0)

        2000.usd().toCrypto(subject, CryptoCurrency.ETHER) `should equal` 2.ether()
    }

    @Test
    fun `toCrypto when no rate, but zero anyway`() {
        0.usd().toCrypto(subject, CryptoCurrency.ETHER) `should equal` 0.ether()
        0.usd().toCryptoOrNull(subject, CryptoCurrency.ETHER) `should equal` 0.ether()
    }

    @Test
    fun `toCrypto when no rate, but not zero`() {
        1.usd().toCrypto(subject, CryptoCurrency.BCH) `should equal` 0.bitcoinCash()
        1.usd().toCryptoOrNull(subject, CryptoCurrency.BCH) `should equal` null
    }

    @Test
    fun `toCrypto yields full precision of the currency - BTC`() {
        givenExchangeRate(CryptoCurrency.BTC, "USD", 5610.82)
        1000.82.usd().toCrypto(subject, CryptoCurrency.BTC) `should equal` 0.17837321.bitcoin()
    }

    @Test
    fun `toCrypto yields full precision of the currency - ETH`() {
        givenExchangeRate(CryptoCurrency.ETHER, "USD", 5610.83)
        val expected = BigDecimal("0.178372896701557526").ether()

        val result = BigDecimal(1000.82).usd().toCrypto(subject, CryptoCurrency.ETHER)
        result `should equal` expected
    }

    @Test
    fun `toCrypto yields full precision of the currency - XLM`() {
        givenExchangeRate(CryptoCurrency.XLM, "USD", 5610.82)
        1000.82.usd().toCrypto(subject, CryptoCurrency.XLM) `should equal` 0.1783732.lumens()
    }

    @Test
    fun `toCrypto rounds up on half`() {
        givenExchangeRate(CryptoCurrency.BTC, "USD", 9.0)
        5.usd().toCrypto(subject, CryptoCurrency.BTC) `should equal` 0.55555556.bitcoin()
    }

    private fun givenExchangeRate(
        cryptoCurrency: CryptoCurrency,
        currencyName: String,
        exchangeRate: Double
    ) {
        whenever(exchangeRateDataStore.getLastPrice(cryptoCurrency, currencyName)).thenReturn(exchangeRate)
    }

    private fun givenHistoricExchangeRate(
        cryptoCurrency: CryptoCurrency,
        currencyName: String,
        time: Long,
        price: BigDecimal
    ) {
        whenever(exchangeRateDataStore.getHistoricPrice(cryptoCurrency, currencyName, time))
            .thenReturn(Single.just(price))
    }
}
