package com.blockchain.swap.nabu.datamanagers.repositories.interest

import com.blockchain.preferences.CurrencyPrefs
import com.blockchain.swap.nabu.Authenticator
import com.blockchain.swap.nabu.service.NabuService
import info.blockchain.balance.CryptoCurrency
import info.blockchain.balance.FiatValue
import io.reactivex.Single
import piuk.blockchain.androidcore.data.exchangerate.ExchangeRateDataManager
import piuk.blockchain.androidcore.data.exchangerate.toCrypto

class InterestLimitsProviderImpl(
    private val nabuService: NabuService,
    private val authenticator: Authenticator,
    private val currencyPrefs: CurrencyPrefs,
    private val exchangeRates: ExchangeRateDataManager
) : InterestLimitsProvider {
    override fun getLimitsForAllAssets(): Single<InterestLimitsList> =
        authenticator.authenticate {
            nabuService.getInterestLimits(it, currencyPrefs.selectedFiatCurrency)
                .map { response ->
                    val list = response.body()?.let { responseBody ->
                        responseBody.limits.assetMap.entries.map { entry ->
                            val crypto = CryptoCurrency.fromNetworkTicker(entry.key)!!
                            val fiatValue = FiatValue.fromMinor(currencyPrefs.selectedFiatCurrency,
                                entry.value.minDepositAmount.toLong())
                            val cryptoValue = fiatValue.toCrypto(exchangeRates, crypto)

                            InterestLimits(
                                entry.value.lockUpDuration,
                                cryptoValue,
                                crypto,
                                entry.value.currency
                            )
                        }
                    } ?: emptyList()

                    InterestLimitsList(list)
                }
        }
}