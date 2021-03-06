package info.blockchain.balance

import info.blockchain.utils.tryParseBigDecimal
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

private data class Key(val locale: Locale, val currencyCode: String, val includeSymbol: Boolean)

private object FiatFormat {

    private val cache: MutableMap<Key, NumberFormat> = ConcurrentHashMap()

    operator fun get(key: Key) = cache.getOrPut(key) {
        val currencyInstance = Currency.getInstance(key.currencyCode)
        val fmt = NumberFormat.getCurrencyInstance(key.locale) as DecimalFormat
        fmt.apply {
            decimalFormatSymbols =
                decimalFormatSymbols.apply {
                    currency = currencyInstance
                    if (!key.includeSymbol) {
                        currencySymbol = ""
                    }
                }
            minimumFractionDigits = currencyInstance.defaultFractionDigits
            maximumFractionDigits = currencyInstance.defaultFractionDigits
            roundingMode = RoundingMode.DOWN
        }
    }
}

// TODO: AND-1363 Remove suppress, possibly by implementing equals manually as copy is not needed
@Suppress("DataClassPrivateConstructor")
data class FiatValue private constructor(
    override val currencyCode: String,
    private val amount: BigDecimal
) : Money() {

    // ALWAYS for display, so use default Locale
    override val symbol: String =
        try {
            Currency.getInstance(currencyCode).getSymbol(Locale.getDefault())
        } catch (t: IllegalArgumentException) {
            throw IllegalArgumentException("${t.message} (currency=$currencyCode)")
        }

    override val maxDecimalPlaces: Int get() = maxDecimalPlaces(currencyCode)

    override val isZero: Boolean get() = amount.signum() == 0

    override val isPositive: Boolean get() = amount.signum() == 1

    override fun toBigDecimal(): BigDecimal = amount

    override fun toBigInteger(): BigInteger =
        amount.movePointRight(maxDecimalPlaces).toBigInteger()

    override fun toFloat(): Float =
        toBigDecimal().toFloat()

    @Deprecated(message = "Tech Debt", replaceWith = ReplaceWith("toBigInteger"))
    val valueMinor: Long = amount.movePointRight(maxDecimalPlaces).toLong()

    override fun toStringWithSymbol(): String =
        FiatFormat[Key(Locale.getDefault(), currencyCode, includeSymbol = true)].format(amount)

    override fun toStringWithoutSymbol(): String =
        FiatFormat[Key(Locale.getDefault(), currencyCode, includeSymbol = false)]
            .format(amount)
            .trim()

    override fun toNetworkString(): String =
        FiatFormat[Key(Locale.US, currencyCode, includeSymbol = false)]
            .format(amount)
            .trim()
            .removeComma()

    override fun toFiat(
        exchangeRates: ExchangeRates,
        fiatCurrency: String
    ) = fromMajor(fiatCurrency,
        exchangeRates.getLastPriceOfFiat(
            sourceFiat = this.currencyCode,
            targetFiat = fiatCurrency
        ) * this.toBigDecimal()
    )

    override fun add(other: Money): FiatValue {
        require(other is FiatValue)
        return FiatValue(currencyCode, amount + other.amount)
    }

    override fun subtract(other: Money): FiatValue {
        require(other is FiatValue)
        return FiatValue(currencyCode, amount - other.amount)
    }

    override fun division(other: Money): Money {
        require(other is FiatValue)
        return FiatValue(currencyCode, amount / other.amount)
    }

    override fun compare(other: Money): Int {
        require(other is FiatValue)
        return amount.compareTo(other.amount)
    }

    override fun ensureComparable(operation: String, other: Money) {
        if (other is FiatValue) {
            if (currencyCode != other.currencyCode) {
                throw ValueTypeMismatchException(operation, currencyCode, other.currencyCode)
            }
        } else {
            throw ValueTypeMismatchException(operation, currencyCode, other.currencyCode)
        }
    }

    override fun toZero(): FiatValue = fromMajor(currencyCode, BigDecimal.ZERO)

    override fun equals(other: Any?): Boolean =
        (other is FiatValue) && (other.currencyCode == currencyCode) && (other.amount.compareTo(amount) == 0)

    override fun hashCode(): Int {
        var result = currencyCode.hashCode()
        result = 31 * result + amount.hashCode()
        return result
    }

    companion object {

        fun fromMinor(currencyCode: String, minor: Long) =
            fromMajor(
                currencyCode,
                BigDecimal.valueOf(minor).movePointLeft(maxDecimalPlaces(currencyCode))
            )

        @JvmStatic
        fun fromMajor(currencyCode: String, major: BigDecimal, round: Boolean = true) =
            FiatValue(
                currencyCode,
                if (round) major.setScale(
                    maxDecimalPlaces(currencyCode),
                    RoundingMode.DOWN
                ) else major
            )

        fun fromMajorOrZero(currencyCode: String, major: String, locale: Locale = Locale.getDefault()) =
            fromMajor(
                currencyCode,
                major.tryParseBigDecimal(locale) ?: BigDecimal.ZERO
            )

        fun zero(currencyCode: String) = FiatValue(currencyCode, BigDecimal.ZERO)

        private fun maxDecimalPlaces(currencyCode: String) =
            Currency.getInstance(currencyCode).defaultFractionDigits
    }
}
