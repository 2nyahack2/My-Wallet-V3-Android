package piuk.blockchain.android.ui.customviews.inputview

import android.text.InputFilter
import android.text.Spanned
import java.text.DecimalFormatSymbols
import java.util.Locale
import java.util.regex.Pattern

class DecimalDigitsInputFilter(
    digitsBeforeZero: Int = 100,
    digitsAfterZero: Int = 100,
    private val prefixOrSuffix: String = ""
) : InputFilter {

    private val decimalSeparator = DecimalFormatSymbols(Locale.getDefault()).decimalSeparator.toString()

    private val pattern =
        Pattern.compile("-?[0-9]{0," + digitsBeforeZero + "}+((\\$decimalSeparator[0-9]{0," +
                digitsAfterZero + "})?)||(\\$decimalSeparator)?")

    override fun filter(
        source: CharSequence,
        start: Int,
        end: Int,
        dest: Spanned,
        dstart: Int,
        dend: Int
    ): CharSequence? {
        if (source.toString() == prefixOrSuffix) return null
        val replacement = source.subSequence(start, end).toString()

        val newVal = (dest.subSequence(0, dstart).toString() + replacement +
                dest.subSequence(dend, dest.length).toString())
        val matcher = pattern.matcher(newVal.removePrefix(prefixOrSuffix).removeSuffix(prefixOrSuffix))

        if (matcher.matches()) return null
        return if (source.isEmpty()) dest.subSequence(dstart, dend) else ""
    }
}