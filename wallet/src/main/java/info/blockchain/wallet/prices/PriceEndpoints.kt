package info.blockchain.wallet.prices

import info.blockchain.wallet.prices.data.PriceDatum
import io.reactivex.Single
import retrofit2.http.GET
import retrofit2.http.Query

interface PriceEndpoints {
    @GET(PriceUrls.PRICE_SERIES)
    fun getHistoricPriceSeries(
        @Query("base") base: String,
        @Query("quote") quote: String,
        @Query("start") start: Long,
        @Query("scale") scale: Int,
        @Query("api_key") apiKey: String
    ): Single<List<PriceDatum>>

    @GET(PriceUrls.SINGLE_PRICE)
    fun getCurrentPrice(
        @Query("base") base: String,
        @Query("quote") quote: String,
        @Query("api_key") apiKey: String
    ): Single<PriceDatum>

    @GET(PriceUrls.SINGLE_PRICE)
    fun getHistoricPrice(
        @Query("base") base: String,
        @Query("quote") quote: String,
        @Query("time") time: Long,
        @Query("api_key") apiKey: String
    ): Single<PriceDatum>

    @GET(PriceUrls.PRICE_INDEXES)
    fun getPriceIndexes(
        @Query("base") base: String,
        @Query("api_key") apiKey: String
    ): Single<Map<String, PriceDatum>>
}