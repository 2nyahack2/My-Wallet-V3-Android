package info.blockchain.wallet.prices

import info.blockchain.balance.CryptoCurrency
import info.blockchain.wallet.ApiCode
import info.blockchain.wallet.prices.data.PriceDatum
import io.reactivex.Single
import java.math.BigDecimal

/**
 * @see [Blockchain Price API specs](https://api.blockchain.info/price/specs)
 */
class PriceApi(private val endpoints: PriceEndpoints, private val apiCode: ApiCode) : CurrentPriceApi {

    /**
     * Returns a [List] of [PriceDatum] objects, containing a timestamp and a price for
     * that given time.
     *
     * @param cryptoCurrency The cryptoCurrency cryptocurrency for which to gather prices, eg "eth", "btc" or "bcc"
     * @param fiat The fiat currency in which to return the prices, eg "usd"
     * @param start The start time, in epoch seconds, from which to gather historic data
     * @param scale The scale which you want to use between price data, eg [TimeInterval.ONE_DAY]
     * @return An [Observable] wrapping a [List] of [PriceDatum] objects
     * @see TimeInterval
     */
    fun getHistoricPriceSeries(
        cryptoCurrency: String,
        fiat: String,
        start: Long,
        scale: Int
    ): Single<List<PriceDatum>> {
        return endpoints.getHistoricPriceSeries(cryptoCurrency,
            fiat,
            start,
            scale,
            apiCode.apiCode
        )
    }

    /**
     * Provides the exchange rate between a cryptocurrency and a fiat currency for this moment in
     * time. Returns a single [PriceDatum] object.
     *
     * @param cryptoCurrency The cryptoCurrency cryptocurrency for which to gather prices, eg "eth", "btc" or "bcc"
     * @param fiat The fiat currency in which to return the price, eg "usd"
     * @return An [Observable] wrapping a [PriceDatum] object
     */
    fun getCurrentPrice(cryptoCurrency: String, fiat: String): Single<Double> {
        return getCurrentPriceDatum(cryptoCurrency, fiat)
            .map { (_, price) -> price }
    }

    private fun getCurrentPriceDatum(cryptoCurrency: String, fiat: String): Single<PriceDatum> {
        return endpoints.getCurrentPrice(
            cryptoCurrency,
            fiat,
            apiCode.apiCode
        )
    }

    /**
     * Provides the exchange rate between a cryptocurrency and a fiat currency for a given moment in
     * epochTime, supplied in seconds since epoch. Returns a single [PriceDatum] object.
     *
     * @param cryptoCurrency The cryptoCurrency cryptocurrency for which to gather prices, eg "eth", "btc" or "bcc"
     * @param fiat The fiat currency in which to return the price, eg "usd"
     * @param epochTime The epochTime in seconds since epoch for which you want to return a price
     * @return An [Observable] wrapping a [PriceDatum] object
     */
    fun getHistoricPrice(cryptoCurrency: String, fiat: String, epochTime: Long): Single<Double> {
        return endpoints.getHistoricPrice(cryptoCurrency,
            fiat,
            epochTime,
            apiCode.apiCode)
            .map { (_, price) -> price }
    }

    /**
     * Provides a [Map] of currency codes to current [PriceDatum] objects for a given
     * cryptoCurrency cryptocurrency. For instance, getting "USD" would return the current price, timestamp
     * and volume in an object. This is a direct replacement for the Ticker.
     *
     * @param cryptoCurrency The cryptoCurrency cryptocurrency that you want prices for, eg. ETH
     * @return A [Map] of [PriceDatum] objects.
     */
    fun getPriceIndexes(cryptoCurrency: String): Single<Map<String, PriceDatum>> {
        return endpoints.getPriceIndexes(cryptoCurrency, apiCode.apiCode)
    }

    override fun currentPrice(base: CryptoCurrency, quoteFiatCode: String): Single<BigDecimal> {
        return getCurrentPriceDatum(base.networkTicker, quoteFiatCode)
            .map { (_, price) -> BigDecimal.valueOf(price ?: 0.toDouble()) }
    }
}