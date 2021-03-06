package piuk.blockchain.android.ui.customviews

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.ContextCompat
import piuk.blockchain.android.R

class FiatCurrencySymbolView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    fun setIcon(fiat: String) =
        setImageDrawable(
            ContextCompat.getDrawable(context,
                when (fiat) {
                    "EUR" -> R.drawable.ic_funds_euro
                    "GBP" -> R.drawable.ic_funds_gbp
                    "USD" -> R.drawable.ic_funds_usd
                    else -> R.drawable.ic_funds_usd // show dollar if currency isn't selected
                }
            ))
}