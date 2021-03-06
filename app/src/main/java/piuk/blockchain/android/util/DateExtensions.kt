package piuk.blockchain.android.util

import java.math.BigInteger
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil

/**
 * Takes a [Date] object and converts it to our standard date format, ie March 09, 2018 @11:47.
 *
 * @param locale The current [Locale].
 * @return A formatted [String] object.
 */
fun Date.toFormattedString(locale: Locale = Locale.getDefault()): String {
    val dateFormat = SimpleDateFormat.getDateInstance(DateFormat.MEDIUM)
    val timeFormat = SimpleDateFormat("hh:mm a", locale)
    val dateText = dateFormat.format(this)
    val timeText = timeFormat.format(this)

    return "$timeText on $dateText"
}

/**
 * Takes a [Date] object and converts it to our standard date format, ie March 09, 2018 @11:47.
 *
 * @param locale The current [Locale].
 * @return A formatted [String] object.
 */
fun Date.toFormattedDate(): String {
    val dateFormat = SimpleDateFormat.getDateInstance(DateFormat.MEDIUM)
    return dateFormat.format(this)
}

fun BigInteger.secondsToDays(): Long =
    ceil(this.toDouble() / SECONDS_OF_DAY).toLong()

fun Int.secondsToDays(): Int =
    ceil(this.toDouble() / SECONDS_OF_DAY).toInt()

private const val SECONDS_OF_DAY: Long = 86400