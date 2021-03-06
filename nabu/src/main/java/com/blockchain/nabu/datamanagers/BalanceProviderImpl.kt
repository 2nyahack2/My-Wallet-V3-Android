package com.blockchain.nabu.datamanagers

import com.blockchain.nabu.Authenticator
import com.blockchain.nabu.models.responses.simplebuy.SimpleBuyAllBalancesResponse
import com.blockchain.nabu.service.NabuService
import io.reactivex.Single

class BalanceProviderImpl(
    private val nabuService: NabuService,
    private val authenticator: Authenticator
) : BalancesProvider {
    override fun getBalanceForAllAssets(): Single<SimpleBuyAllBalancesResponse> =
        authenticator.authenticate {
            nabuService.getBalanceForAllAssets(it)
        }
}