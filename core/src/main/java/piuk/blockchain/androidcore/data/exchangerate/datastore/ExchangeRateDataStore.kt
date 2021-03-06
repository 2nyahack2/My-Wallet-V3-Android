package piuk.blockchain.androidcore.data.exchangerate.datastore

import info.blockchain.balance.CryptoCurrency
import info.blockchain.wallet.prices.data.PriceDatum
import io.reactivex.Completable
import io.reactivex.Single
import piuk.blockchain.androidcore.data.exchangerate.ExchangeRateService
import piuk.blockchain.androidcore.utils.PersistentPrefs
import timber.log.Timber
import java.math.BigDecimal

class ExchangeRateDataStore(
    private val exchangeRateService: ExchangeRateService,
    private val prefs: PersistentPrefs
) {

    // Ticker data
    private var btcTickerData: Map<String, PriceDatum>? = null
    private var ethTickerData: Map<String, PriceDatum>? = null
    private var bchTickerData: Map<String, PriceDatum>? = null
    private var xlmTickerData: Map<String, PriceDatum>? = null
    private var paxTickerData: Map<String, PriceDatum>? = null
    private var algTickerData: Map<String, PriceDatum>? = null
    private var usdtTickerData: Map<String, PriceDatum>? = null
    private var dgldTickerData: Map<String, PriceDatum>? = null

    fun updateExchangeRates(): Completable = Single.merge(
        listOf(
            exchangeRateService.getExchangeRateMap(CryptoCurrency.BTC)
                .doOnSuccess { btcTickerData = it.toMap() },
            exchangeRateService.getExchangeRateMap(CryptoCurrency.BCH)
                .doOnSuccess { bchTickerData = it.toMap() },
            exchangeRateService.getExchangeRateMap(CryptoCurrency.ETHER)
                .doOnSuccess { ethTickerData = it.toMap() },
            exchangeRateService.getExchangeRateMap(CryptoCurrency.XLM)
                .doOnSuccess { xlmTickerData = it.toMap() },
            exchangeRateService.getExchangeRateMap(CryptoCurrency.PAX)
                .doOnSuccess { paxTickerData = it.toMap() },
            exchangeRateService.getExchangeRateMap(CryptoCurrency.ALGO)
                .doOnSuccess { algTickerData = it.toMap() },
            exchangeRateService.getExchangeRateMap(CryptoCurrency.USDT)
                .doOnSuccess { usdtTickerData = it.toMap() },
            exchangeRateService.getExchangeRateMap(CryptoCurrency.DGLD)
                .doOnSuccess { dgldTickerData = it.toMap() }
        )
    ).ignoreElements()

    fun getCurrencyLabels(): Array<String> = btcTickerData!!.keys.toTypedArray()

    fun getLastPrice(cryptoCurrency: CryptoCurrency, fiatCurrency: String): Double {
        if (fiatCurrency.isEmpty()) {
            throw IllegalArgumentException("No currency supplied")
        }

        val tickerData = cryptoCurrency.tickerData()

        val prefsKey = "LAST_KNOWN_${cryptoCurrency.networkTicker}_VALUE_FOR_CURRENCY_$fiatCurrency"

        val lastKnown = try {
            prefs.getValue(prefsKey, "0.0").toDouble()
        } catch (e: NumberFormatException) {
            Timber.e(e)
            prefs.setValue(prefsKey, "0.0")
            0.0
        }

        val lastPrice: Double? = tickerData?.get(fiatCurrency)?.price

        if (lastPrice != null) {
            prefs.setValue("$prefsKey$fiatCurrency", lastPrice.toString())
        }

        return lastPrice ?: lastKnown
    }

    fun getFiatLastPrice(targetFiat: String, sourceFiat: String): Double {
        val targetCurrencyPrice =
            CryptoCurrency.BTC.tickerData()?.get(targetFiat)?.price ?: return 0.0
        val sourceCurrencyPrice =
            CryptoCurrency.BTC.tickerData()?.get(sourceFiat)?.price ?: return 0.0
        return targetCurrencyPrice.div(sourceCurrencyPrice)
    }

    private fun CryptoCurrency.tickerData() =
        when (this) {
            CryptoCurrency.BTC -> btcTickerData
            CryptoCurrency.ETHER -> ethTickerData
            CryptoCurrency.BCH -> bchTickerData
            CryptoCurrency.XLM -> xlmTickerData
            CryptoCurrency.PAX -> paxTickerData
            CryptoCurrency.STX -> TODO("STUB: STX NOT IMPLEMENTED")
            CryptoCurrency.ALGO -> algTickerData
            CryptoCurrency.USDT -> usdtTickerData
            CryptoCurrency.DGLD -> dgldTickerData
        }

    fun getHistoricPrice(
        cryptoCurrency: CryptoCurrency,
        fiat: String,
        timeInSeconds: Long
    ): Single<BigDecimal> =
        exchangeRateService.getHistoricPrice(cryptoCurrency, fiat, timeInSeconds)
            .map { it.toBigDecimal() }
}
