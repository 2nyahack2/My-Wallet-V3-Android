package info.blockchain.balance

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Currency

sealed class ExchangeRate(val rate: BigDecimal) {

    abstract fun convert(value: Money, round: Boolean = true): Money
    abstract fun price(): Money
    abstract fun inverse(roundingMode: RoundingMode = RoundingMode.HALF_UP, scale: Int = -1): ExchangeRate

    class CryptoToCrypto(
        val from: CryptoCurrency,
        val to: CryptoCurrency,
        rate: BigDecimal
    ) : ExchangeRate(rate) {
        fun applyRate(cryptoValue: CryptoValue): CryptoValue {
            validateCurrency(from, cryptoValue.currency)
            return CryptoValue.fromMajor(
                to,
                rate.multiply(cryptoValue.toBigDecimal())
            )
        }

        override fun convert(value: Money, round: Boolean): Money =
            applyRate(value as CryptoValue)

        override fun price(): Money =
            CryptoValue.fromMajor(to, rate)

        override fun inverse(roundingMode: RoundingMode, scale: Int) =
            CryptoToCrypto(to,
                from,
                BigDecimal.ONE.divide(rate, if (scale == -1) from.dp else scale, roundingMode).stripTrailingZeros())
    }

    data class CryptoToFiat(
        val from: CryptoCurrency,
        val to: String,
        private val _rate: BigDecimal
    ) : ExchangeRate(_rate) {
        fun applyRate(cryptoValue: CryptoValue, round: Boolean = false): FiatValue {
            validateCurrency(from, cryptoValue.currency)
            return FiatValue.fromMajor(
                currencyCode = to,
                major = rate.multiply(cryptoValue.toBigDecimal()),
                round = round
            )
        }

        override fun convert(value: Money, round: Boolean): Money =
            applyRate(value as CryptoValue, round)

        override fun price(): Money =
            FiatValue.fromMajor(to, rate)

        override fun inverse(roundingMode: RoundingMode, scale: Int) =
            FiatToCrypto(to,
                from,
                BigDecimal.ONE.divide(rate, if (scale == -1) from.dp else scale, roundingMode).stripTrailingZeros())
    }

    class FiatToCrypto(
        val from: String,
        val to: CryptoCurrency,
        rate: BigDecimal
    ) : ExchangeRate(rate) {
        fun applyRate(fiatValue: FiatValue): CryptoValue {
            validateCurrency(from, fiatValue.currencyCode)
            return CryptoValue.fromMajor(
                to,
                rate.multiply(fiatValue.toBigDecimal())
            )
        }

        override fun convert(value: Money, round: Boolean): Money =
            applyRate(value as FiatValue)

        override fun price(): Money =
            CryptoValue.fromMajor(to, rate)

        override fun inverse(roundingMode: RoundingMode, scale: Int) =
            CryptoToFiat(to,
                from,
                BigDecimal.ONE.divide(
                    rate,
                    if (scale == -1) Currency.getInstance(from).defaultFractionDigits else scale,
                    roundingMode
                ).stripTrailingZeros()
            )
    }

    class FiatToFiat(
        val from: String,
        val to: String,
        rate: BigDecimal
    ) : ExchangeRate(rate) {
        fun applyRate(fiatValue: FiatValue): FiatValue {
            validateCurrency(from, fiatValue.currencyCode)
            return FiatValue.fromMajor(
                to,
                rate.multiply(fiatValue.toBigDecimal())
            )
        }

        override fun convert(value: Money, round: Boolean): Money =
            applyRate(value as FiatValue)

        override fun price(): Money =
            FiatValue.fromMajor(to, rate)

        override fun inverse(roundingMode: RoundingMode, scale: Int) =
            FiatToFiat(to,
                from,
                BigDecimal.ONE.divide(rate,
                    if (scale == -1) Currency.getInstance(from).defaultFractionDigits else scale,
                    roundingMode).stripTrailingZeros()
            )
    }

    companion object {
        private fun validateCurrency(expected: CryptoCurrency, got: CryptoCurrency) {
            if (expected != got)
                throw ValueTypeMismatchException("exchange", expected.networkTicker, got.networkTicker)
        }

        private fun validateCurrency(expected: String, got: String) {
            if (expected != got)
                throw ValueTypeMismatchException("exchange", expected, got)
        }
    }
}

operator fun CryptoValue?.times(rate: ExchangeRate.CryptoToCrypto?) =
    this?.let { rate?.applyRate(it) }

operator fun CryptoValue?.div(rate: ExchangeRate.CryptoToCrypto?) =
    this?.let { rate?.inverse()?.applyRate(it) }

operator fun FiatValue?.times(rate: ExchangeRate.FiatToCrypto?) =
    this?.let { rate?.applyRate(it) }

operator fun CryptoValue?.times(exchangeRate: ExchangeRate.CryptoToFiat?) =
    this?.let { exchangeRate?.applyRate(it) }

operator fun CryptoValue?.div(exchangeRate: ExchangeRate.FiatToCrypto?) =
    this?.let { exchangeRate?.inverse()?.applyRate(it) }

operator fun FiatValue?.div(exchangeRate: ExchangeRate.CryptoToFiat?) =
    this?.let { exchangeRate?.inverse()?.applyRate(it) }

fun ExchangeRate?.percentageDelta(previous: ExchangeRate?): Double =
    try {
        if (this != null && previous != null && previous.rate.signum() != 0) {
            val current = rate
            val prev = previous.rate

            (current - prev)
                .divide(prev, 4, RoundingMode.HALF_EVEN)
                .movePointRight(2)
                .toDouble()
        } else {
            Double.NaN
        }
    } catch (t: ArithmeticException) {
        Double.NaN
    }