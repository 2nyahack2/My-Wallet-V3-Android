package com.blockchain.nabu.datamanagers

import com.blockchain.nabu.models.responses.nabu.NabuUser
import io.reactivex.Maybe
import io.reactivex.Observable

private const val CACHE_LIFETIME = 2 * 60 * 1000
class NabuUserRepository(private val nabuDataUserProvider: NabuDataUserProvider) {

    private lateinit var userCache: NabuUser
    private var lastUpdatedTimestamp: Long = -1L

    fun fetchUser(): Observable<NabuUser> =
        Maybe.concat(
            getFromCache(),
            getFromNetwork()
        ).toObservable()

    private fun getFromNetwork(): Maybe<NabuUser> =
        if (isCacheExpired()) {
            nabuDataUserProvider.getUser().toMaybe().doOnSuccess {
                lastUpdatedTimestamp = System.currentTimeMillis()
                userCache = it
            }
        } else {
            Maybe.empty()
        }

    private fun getFromCache(): Maybe<NabuUser> =
        if (!::userCache.isInitialized) {
            Maybe.empty()
        } else {
            Maybe.just(userCache)
        }

    private fun isCacheExpired() =
        System.currentTimeMillis() - lastUpdatedTimestamp >= CACHE_LIFETIME
}