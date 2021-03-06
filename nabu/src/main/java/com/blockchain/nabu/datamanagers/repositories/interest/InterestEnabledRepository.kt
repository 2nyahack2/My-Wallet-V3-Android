package com.blockchain.swap.nabu.datamanagers.repositories.interest

import com.blockchain.rx.TimedCacheRequest
import info.blockchain.balance.CryptoCurrency
import io.reactivex.Single

class InterestEnabledRepository(interestEnabledProvider: InterestEnabledProvider) {

    private val cache = TimedCacheRequest(
        cacheLifetimeSeconds = CACHE_LIFETIME,
        refreshFn = { interestEnabledProvider.getEnabledStatusForAllAssets() }
    )

    fun getEnabledForAsset(ccy: CryptoCurrency): Single<Boolean> =
        cache.getCachedSingle().flatMap { enabledList ->
            Single.just(enabledList.contains(ccy))
        }.onErrorResumeNext(Single.just(false))

    fun getEnabledAssets(): Single<List<CryptoCurrency>> =
        cache.getCachedSingle()

    companion object {
        private const val CACHE_LIFETIME = 3600L // this is unlikely to change in one user session
    }
}
