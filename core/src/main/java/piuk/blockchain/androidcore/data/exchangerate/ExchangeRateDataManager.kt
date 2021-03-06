package piuk.blockchain.androidcore.data.exchangerate

import info.blockchain.balance.CryptoCurrency
import info.blockchain.balance.CryptoValue
import info.blockchain.balance.ExchangeRates
import info.blockchain.balance.FiatValue
import info.blockchain.balance.Money
import io.reactivex.Completable
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import piuk.blockchain.androidcore.data.exchangerate.datastore.ExchangeRateDataStore
import piuk.blockchain.androidcore.data.rxjava.RxBus
import piuk.blockchain.androidcore.data.rxjava.RxPinning
import java.lang.IllegalStateException
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * This data manager is responsible for storing and updating the latest exchange rates information
 * for all crypto currencies.
 * Historic prices for all crypto currencies can be queried from here.
 */
class ExchangeRateDataManager(
    private val exchangeRateDataStore: ExchangeRateDataStore,
    rxBus: RxBus
) : ExchangeRates {
    private val rxPinning = RxPinning(rxBus)

    fun updateTickers(): Completable =
        rxPinning.call { exchangeRateDataStore.updateExchangeRates() }
            .subscribeOn(Schedulers.io())

    override fun getLastPrice(cryptoCurrency: CryptoCurrency, currencyName: String) =
        BigDecimal(exchangeRateDataStore.getLastPrice(cryptoCurrency, currencyName))

    override fun getLastPriceOfFiat(targetFiat: String, sourceFiat: String) =
        BigDecimal(exchangeRateDataStore.getFiatLastPrice(targetFiat = targetFiat, sourceFiat = sourceFiat))

    fun getHistoricPrice(value: Money, fiat: String, timeInSeconds: Long): Single<FiatValue> =
        exchangeRateDataStore.getHistoricPrice(
            (value as? CryptoValue)?.currency ?: throw IllegalStateException("Fiat is not supported"),
            fiat, timeInSeconds)
            .map { FiatValue.fromMajor(fiat, it * value.toBigDecimal()) }
            .subscribeOn(Schedulers.io())

    fun getHistoricPrice(currency: CryptoCurrency, fiat: String, timeInSeconds: Long): Single<FiatValue> =
        exchangeRateDataStore.getHistoricPrice(currency, fiat, timeInSeconds)
            .map { FiatValue.fromMajor(fiat, it) }
            .subscribeOn(Schedulers.io())

    fun getCurrencyLabels() = exchangeRateDataStore.getCurrencyLabels()
}

fun FiatValue.toCrypto(exchangeRateDataManager: ExchangeRates, cryptoCurrency: CryptoCurrency) =
    toCryptoOrNull(exchangeRateDataManager, cryptoCurrency) ?: CryptoValue.zero(cryptoCurrency)

fun FiatValue.toCryptoOrNull(exchangeRateDataManager: ExchangeRates, cryptoCurrency: CryptoCurrency) =
    if (isZero) {
        CryptoValue.zero(cryptoCurrency)
    } else {
        val rate = exchangeRateDataManager.getLastPrice(cryptoCurrency, this.currencyCode)
        if (rate.signum() == 0) {
            null
        } else {
            CryptoValue.fromMajor(
                cryptoCurrency,
                this.toBigDecimal().divide(rate, cryptoCurrency.dp, RoundingMode.HALF_UP)
            )
        }
    }
