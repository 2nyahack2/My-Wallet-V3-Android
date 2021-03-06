package com.blockchain.nabu.datamanagers.repositories.swap

import com.blockchain.nabu.Authenticator
import com.blockchain.nabu.datamanagers.CurrencyPair
import com.blockchain.nabu.service.NabuService
import info.blockchain.balance.CryptoCurrency
import io.reactivex.Single

interface TradingPairsProvider {
    fun getAvailablePairs(): Single<List<CurrencyPair>>
}

class TradingPairsProviderImpl(
    private val authenticator: Authenticator,
    private val nabuService: NabuService
) : TradingPairsProvider {
    override fun getAvailablePairs(): Single<List<CurrencyPair>> = authenticator.authenticate { sessionToken ->
        nabuService.getSwapAvailablePairs(sessionToken)
    }.map { response ->
        response.mapNotNull { pair ->
            val parts = pair.split("-")
            if (parts.size != 2) return@mapNotNull null
            val source = CryptoCurrency.fromNetworkTicker(parts[0]) ?: return@mapNotNull null
            val destination = CryptoCurrency.fromNetworkTicker(parts[1]) ?: return@mapNotNull null
            CurrencyPair.CryptoCurrencyPair(source, destination)
        }
    }
}