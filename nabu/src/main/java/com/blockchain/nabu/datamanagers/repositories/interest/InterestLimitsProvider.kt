package com.blockchain.nabu.datamanagers.repositories.interest

import com.blockchain.nabu.Authenticator
import com.blockchain.nabu.service.NabuService
import com.blockchain.preferences.CurrencyPrefs
import info.blockchain.balance.CryptoCurrency
import info.blockchain.balance.CryptoValue
import info.blockchain.balance.FiatValue
import io.reactivex.Single
import piuk.blockchain.androidcore.data.exchangerate.ExchangeRateDataManager
import piuk.blockchain.androidcore.data.exchangerate.toCrypto
import java.util.Calendar
import java.util.Date

interface InterestLimitsProvider {
    fun getLimitsForAllAssets(): Single<InterestLimitsList>
}

class InterestLimitsProviderImpl(
    private val nabuService: NabuService,
    private val authenticator: Authenticator,
    private val currencyPrefs: CurrencyPrefs,
    private val exchangeRates: ExchangeRateDataManager
) : InterestLimitsProvider {
    override fun getLimitsForAllAssets(): Single<InterestLimitsList> =
        authenticator.authenticate {
            nabuService.getInterestLimits(it, currencyPrefs.selectedFiatCurrency)
                .map { responseBody ->
                    InterestLimitsList(responseBody.limits.assetMap.entries.map { entry ->
                        val crypto = CryptoCurrency.fromNetworkTicker(entry.key)!!
                        val fiatValue = FiatValue.fromMinor(currencyPrefs.selectedFiatCurrency,
                            entry.value.minDepositAmount.toLong())
                        val cryptoValue = fiatValue.toCrypto(exchangeRates, crypto)

                        val calendar = Calendar.getInstance()
                        calendar.set(Calendar.DAY_OF_MONTH, 1)
                        calendar.add(Calendar.MONTH, 1)

                        InterestLimits(
                            entry.value.lockUpDuration,
                            cryptoValue,
                            crypto,
                            entry.value.currency,
                            calendar.time
                        )
                    })
                }
        }
}

data class InterestLimits(
    val interestLockUpDuration: Int,
    val minDepositAmount: CryptoValue,
    val cryptoCurrency: CryptoCurrency,
    val currency: String,
    val nextInterestPayment: Date
)

data class InterestLimitsList(
    val list: List<InterestLimits>
)