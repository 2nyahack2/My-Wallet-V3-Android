package piuk.blockchain.android.ui.settings.preferences

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import androidx.preference.PreferenceCategory
import piuk.blockchain.android.R
import piuk.blockchain.android.util.loadInterMedium

@Suppress("unused")
class CustomPreferenceCategory @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = R.attr.preferenceCategoryStyle,
    defStyleRes: Int = 0
) : PreferenceCategory(context, attrs, defStyleAttr, defStyleRes) {

    private val typeface: Typeface = context.loadInterMedium()

    init {
        // Forces setting fonts when Summary or Title are set via XMl
        this.title = title
        this.summary = summary
    }

    override fun setTitle(titleResId: Int) {
        title = context.getString(titleResId)
    }

    override fun setTitle(title: CharSequence?) {
        title?.let { super.setTitle(title.applyFont(typeface)) } ?: super.setTitle(title)
    }

    override fun setSummary(summaryResId: Int) {
        summary = context.getString(summaryResId)
    }

    override fun setSummary(summary: CharSequence?) {
        summary?.let { super.setSummary(summary.applyFont(typeface)) } ?: super.setSummary(summary)
    }
}
