package com.blockchain.nabu.datamanagers.repositories

import com.blockchain.rx.TimedCacheRequest
import com.blockchain.nabu.datamanagers.NabuDataUserProvider
import com.blockchain.nabu.models.responses.nabu.NabuUser
import io.reactivex.Single

class NabuUserRepository(nabuDataUserProvider: NabuDataUserProvider) {

    val cache = TimedCacheRequest(
        cacheLifetimeSeconds = CACHE_LIFETIME,
        refreshFn = { nabuDataUserProvider.getUser() }
    )

    fun fetchUser(): Single<NabuUser> =
        cache.getCachedSingle()

    companion object {
        private const val CACHE_LIFETIME: Long = 10
    }
}
