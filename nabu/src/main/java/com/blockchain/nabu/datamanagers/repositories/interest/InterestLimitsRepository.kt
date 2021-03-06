package com.blockchain.swap.nabu.datamanagers.repositories.interest

import com.blockchain.rx.TimedCacheRequest
import info.blockchain.balance.CryptoCurrency
import io.reactivex.Maybe

class InterestLimitsRepository(interestLimitsProvider: InterestLimitsProvider) {

    private val cache = TimedCacheRequest(
        cacheLifetimeSeconds = CACHE_LIFETIME,
        refreshFn = { interestLimitsProvider.getLimitsForAllAssets() }
    )

    fun getLimitForAsset(ccy: CryptoCurrency): Maybe<InterestLimits> =
        cache.getCachedSingle().flatMapMaybe { limitsList ->
            val limitsForAsset = limitsList.list.find { it.cryptoCurrency == ccy }
            limitsForAsset?.let {
                Maybe.just(it)
            } ?: Maybe.empty()
        }.onErrorResumeNext(Maybe.empty())

    companion object {
        private const val CACHE_LIFETIME = 240L
    }
}
