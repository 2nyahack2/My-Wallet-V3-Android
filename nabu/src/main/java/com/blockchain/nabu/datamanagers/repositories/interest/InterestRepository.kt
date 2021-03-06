package com.blockchain.nabu.datamanagers.repositories.interest

import com.blockchain.rx.TimedCacheRequest
import info.blockchain.balance.CryptoCurrency
import io.reactivex.Maybe
import io.reactivex.Single

class InterestRepository(
    interestLimitsProvider: InterestLimitsProvider,
    interestAvailabilityProvider: InterestAvailabilityProvider,
    interestEligibilityProvider: InterestEligibilityProvider
) {
    private val limitsCache = TimedCacheRequest(
        cacheLifetimeSeconds = SHORT_LIFETIME,
        refreshFn = { interestLimitsProvider.getLimitsForAllAssets() }
    )

    private val availabilityCache = TimedCacheRequest(
        cacheLifetimeSeconds = LONG_LIFETIME,
        refreshFn = { interestAvailabilityProvider.getEnabledStatusForAllAssets() }
    )

    private val eligibilityCache = TimedCacheRequest(
        cacheLifetimeSeconds = LONG_LIFETIME,
        refreshFn = { interestEligibilityProvider.getEligibilityForAllAssets() }
    )

    fun getLimitForAsset(ccy: CryptoCurrency): Maybe<InterestLimits> =
        limitsCache.getCachedSingle().flatMapMaybe { limitsList ->
            val limitsForAsset = limitsList.list.find { it.cryptoCurrency == ccy }
            limitsForAsset?.let {
                Maybe.just(it)
            } ?: Maybe.empty()
        }.onErrorResumeNext(Maybe.empty())

    fun getAvailabilityForAsset(ccy: CryptoCurrency): Single<Boolean> =
        availabilityCache.getCachedSingle().flatMap { enabledList ->
            Single.just(enabledList.contains(ccy))
        }.onErrorResumeNext(Single.just(false))

    fun getAvailableAssets(): Single<List<CryptoCurrency>> =
        availabilityCache.getCachedSingle()

    fun getEligibilityForAsset(ccy: CryptoCurrency): Single<Eligibility> =
        eligibilityCache.getCachedSingle().map { eligibilityList ->
            eligibilityList.find { it.cryptoCurrency == ccy }?.eligibility
        }

    companion object {
        private const val SHORT_LIFETIME = 240L
        private const val LONG_LIFETIME = 3600L
    }
}