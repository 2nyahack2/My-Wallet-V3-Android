package info.blockchain.balance

import java.math.BigDecimal

interface ExchangeRates {
    fun getLastPrice(cryptoCurrency: CryptoCurrency, currencyName: String): BigDecimal
    fun getLastPriceOfFiat(targetFiat: String, sourceFiat: String): BigDecimal
}