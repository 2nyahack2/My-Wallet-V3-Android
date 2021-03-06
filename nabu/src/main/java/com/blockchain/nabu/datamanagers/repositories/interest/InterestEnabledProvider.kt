package com.blockchain.swap.nabu.datamanagers.repositories.interest

import info.blockchain.balance.CryptoCurrency
import io.reactivex.Single

interface InterestEnabledProvider {
    fun getEnabledStatusForAllAssets(): Single<List<CryptoCurrency>>
}
