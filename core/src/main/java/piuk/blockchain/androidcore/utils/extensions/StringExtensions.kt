package piuk.blockchain.androidcore.utils.extensions

import java.text.NumberFormat
import java.text.ParseException
import java.util.Locale
import kotlin.math.roundToLong

@Deprecated("We should have a from string method in Crypto- and FiatValue")
fun String.toSafeLong(locale: Locale = Locale.getDefault()): Long = try {
    var amount = this
    if (amount.isEmpty()) amount = "0"
    (NumberFormat.getInstance(locale).parse(amount).toDouble() * 1e8).roundToLong()
} catch (e: ParseException) {
    0L
}

private const val REGEX_UUID = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
fun String.isValidGuid() = this.matches(REGEX_UUID.toRegex())

const val PIN_LENGTH = 4
fun String.isValidPin(): Boolean = (this != "0000" && this.length == PIN_LENGTH)