package com.blockchain.nabu.datamanagers.repositories

import io.reactivex.Maybe

private const val CACHE_LIFETIME = 60 * 1000
abstract class ExpiringRepository<T> {
    var lastUpdatedTimestamp = -1L

    fun isCacheExpired() =
        System.currentTimeMillis() - lastUpdatedTimestamp >= CACHE_LIFETIME

    abstract fun getFromNetwork(): Maybe<T>
    abstract fun getFromCache(): Maybe<T>
}