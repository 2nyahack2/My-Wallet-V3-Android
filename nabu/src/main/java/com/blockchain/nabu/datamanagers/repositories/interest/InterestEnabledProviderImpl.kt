package com.blockchain.swap.nabu.datamanagers.repositories.interest

import com.blockchain.swap.nabu.Authenticator
import com.blockchain.swap.nabu.service.NabuService
import info.blockchain.balance.CryptoCurrency
import io.reactivex.Single

class InterestEnabledProviderImpl(
    private val nabuService: NabuService,
    private val authenticator: Authenticator
) : InterestEnabledProvider {
    override fun getEnabledStatusForAllAssets(): Single<List<CryptoCurrency>> =
        authenticator.authenticate { token ->
            nabuService.getInterestEnabled(token).map { response ->
                response.body()?.let { instrumentsResponse ->
                    instrumentsResponse.instruments.map {
                        CryptoCurrency.fromNetworkTicker(it)!!
                    }
                } ?: emptyList()
            }
        }
}