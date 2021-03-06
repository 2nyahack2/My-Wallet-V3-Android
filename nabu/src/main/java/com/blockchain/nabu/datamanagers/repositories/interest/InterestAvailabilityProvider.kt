package com.blockchain.nabu.datamanagers.repositories.interest

import com.blockchain.nabu.Authenticator
import com.blockchain.nabu.service.NabuService
import info.blockchain.balance.CryptoCurrency
import io.reactivex.Single

interface InterestAvailabilityProvider {
    fun getEnabledStatusForAllAssets(): Single<List<CryptoCurrency>>
}

class InterestAvailabilityProviderImpl(
    private val nabuService: NabuService,
    private val authenticator: Authenticator
) : InterestAvailabilityProvider {
    override fun getEnabledStatusForAllAssets(): Single<List<CryptoCurrency>> =
        authenticator.authenticate { token ->
            nabuService.getInterestEnabled(token).map { instrumentsResponse ->
                instrumentsResponse.instruments.map {
                    CryptoCurrency.fromNetworkTicker(it)
                }.mapNotNull { it }
            }
        }
}