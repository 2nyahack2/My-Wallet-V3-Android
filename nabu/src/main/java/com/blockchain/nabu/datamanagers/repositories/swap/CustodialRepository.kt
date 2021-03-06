package com.blockchain.nabu.datamanagers.repositories.swap

import com.blockchain.rx.TimedCacheRequest
import com.blockchain.nabu.datamanagers.CurrencyPair
import com.blockchain.nabu.datamanagers.TransferDirection
import info.blockchain.balance.CryptoCurrency
import io.reactivex.Single

class CustodialRepository(pairsProvider: TradingPairsProvider, activityProvider: SwapActivityProvider) {

    private val pairsCache = TimedCacheRequest(
        cacheLifetimeSeconds = LONG_CACHE,
        refreshFn = {
            pairsProvider.getAvailablePairs()
        }
    )

    private val swapActivityCache = TimedCacheRequest(
        cacheLifetimeSeconds = SHORT_CACHE,
        refreshFn = {
            activityProvider.getSwapActivity()
        }
    )

    fun getSwapAvailablePairs(): Single<List<CurrencyPair.CryptoCurrencyPair>> =
        pairsCache.getCachedSingle().map { it.filterIsInstance<CurrencyPair.CryptoCurrencyPair>() }

    fun getCustodialActivityForAsset(
        cryptoCurrency: CryptoCurrency,
        directions: Set<TransferDirection>
    ): Single<List<TradeTransactionItem>> =
        swapActivityCache.getCachedSingle().map { list ->
            list.filter {
                when (it.currencyPair) {
                    is CurrencyPair.CryptoCurrencyPair -> it.currencyPair.source == cryptoCurrency &&
                            directions.contains(it.direction)
                    is CurrencyPair.CryptoToFiatCurrencyPair -> it.currencyPair.source == cryptoCurrency &&
                            directions.contains(it.direction)
                }
            }
        }

    companion object {
        const val LONG_CACHE = 60000L
        const val SHORT_CACHE = 120L
    }
}