package com.blockchain.nabu.datamanagers.repositories

import com.blockchain.rx.TimedCacheRequest
import com.blockchain.nabu.datamanagers.BalancesProvider
import info.blockchain.balance.CryptoCurrency
import info.blockchain.balance.CryptoValue
import info.blockchain.balance.FiatValue
import io.reactivex.Maybe
import timber.log.Timber

class AssetBalancesRepository(balancesProvider: BalancesProvider) {

    private val cache = TimedCacheRequest(
        cacheLifetimeSeconds = CACHE_LIFETIME,
        refreshFn = {
            balancesProvider.getBalanceForAllAssets()
                .doOnSuccess { Timber.d("Balance response: $it") }
        }
    )

    fun getTotalBalanceForAsset(ccy: CryptoCurrency): Maybe<CryptoValue> =
        cache.getCachedSingle().flatMapMaybe {
            it[ccy]?.let { response ->
                Maybe.just(CryptoValue.fromMinor(ccy, response.total.toBigInteger()))
            } ?: Maybe.empty()
        }.onErrorResumeNext(Maybe.empty())

    fun getActionableBalanceForAsset(ccy: CryptoCurrency): Maybe<CryptoValue> =
        cache.getCachedSingle().flatMapMaybe {
            it[ccy]?.let { response ->
                Maybe.just(CryptoValue.fromMinor(ccy, response.actionable.toBigInteger()))
            } ?: Maybe.empty()
        }.onErrorResumeNext(Maybe.empty())

    fun getTotalBalanceForAsset(fiat: String): Maybe<FiatValue> =
        cache.getCachedSingle().flatMapMaybe {
            it[fiat]?.let { response ->
                Maybe.just(FiatValue.fromMinor(fiat, response.total.toLong()))
            } ?: Maybe.empty()
        }.onErrorResumeNext(Maybe.empty())

    fun getActionableBalanceForAsset(fiat: String): Maybe<FiatValue> =
        cache.getCachedSingle().flatMapMaybe {
            it[fiat]?.let { response ->
                Maybe.just(FiatValue.fromMinor(fiat, response.actionable.toLong()))
            } ?: Maybe.empty()
        }.onErrorResumeNext(Maybe.empty())

    fun getPendingBalanceForAsset(ccy: CryptoCurrency): Maybe<CryptoValue> =
        cache.getCachedSingle().flatMapMaybe {
            it[ccy]?.let { response ->
                Maybe.just(CryptoValue.fromMinor(ccy, response.pending.toBigInteger()))
            } ?: Maybe.empty()
        }.onErrorResumeNext(Maybe.empty())

    fun getPendingBalanceForAsset(fiat: String): Maybe<FiatValue> =
        cache.getCachedSingle().flatMapMaybe {
            it[fiat]?.let { response ->
                Maybe.just(FiatValue.fromMinor(fiat, response.pending.toLong()))
            } ?: Maybe.empty()
        }.onErrorResumeNext(Maybe.empty())

    companion object {
        private const val CACHE_LIFETIME = 10L
    }
}
