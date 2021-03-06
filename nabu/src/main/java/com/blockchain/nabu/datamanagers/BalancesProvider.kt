package com.blockchain.nabu.datamanagers

import com.blockchain.nabu.models.responses.simplebuy.SimpleBuyAllBalancesResponse
import io.reactivex.Single

interface BalancesProvider {
    fun getBalanceForAllAssets(): Single<SimpleBuyAllBalancesResponse>
}