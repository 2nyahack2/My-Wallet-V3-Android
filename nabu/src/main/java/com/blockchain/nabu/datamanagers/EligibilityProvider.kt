package com.blockchain.nabu.datamanagers

import com.blockchain.preferences.CurrencyPrefs
import com.blockchain.rx.ParameteredTimedCacheRequest
import com.blockchain.nabu.Authenticator
import com.blockchain.nabu.service.NabuService
import io.reactivex.Single

interface EligibilityProvider {
    val defCurrency: String
    fun isEligibleForSimpleBuy(currency: String = defCurrency, forceRefresh: Boolean = false): Single<Boolean>
}

class NabuCachedEligibilityProvider(
    private val nabuService: NabuService,
    private val authenticator: Authenticator,
    private val currencyPrefs: CurrencyPrefs
) : EligibilityProvider {
    override val defCurrency: String
        get() = currencyPrefs.selectedFiatCurrency

    private val refresh: (String) -> Single<Boolean> = { currency ->
        authenticator.authenticate {
            nabuService.isEligibleForSimpleBuy(it, currency)
        }.map {
            it.simpleBuyTradingEligible
        }.onErrorReturn {
            false
        }
    }

    private val cache = ParameteredTimedCacheRequest(
        cacheLifetimeSeconds = 20L,
        refreshFn = refresh
    )

    override fun isEligibleForSimpleBuy(currency: String, forceRefresh: Boolean): Single<Boolean> {
        return if (!forceRefresh) cache.getCachedSingle(currency) else refresh(currency)
    }
}

class MockedEligibilityProvider(private val isEligible: Boolean) : EligibilityProvider {
    override val defCurrency: String
        get() = ""

    override fun isEligibleForSimpleBuy(currency: String, forceRefresh: Boolean): Single<Boolean> =
        Single.just(isEligible)
}