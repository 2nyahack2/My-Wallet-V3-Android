package piuk.blockchain.android.ui.settings.preferences

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.widget.TextView
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import piuk.blockchain.android.R
import piuk.blockchain.android.util.loadInterMedium

abstract class BaseStatusPreference<T : Any> constructor(
    context: Context,
    attrs: AttributeSet?,
    defStyleAttr: Int,
    defStyleRes: Int
) : Preference(context, attrs, defStyleAttr, defStyleRes) {

    private lateinit var textView: TextView
    private val typeface: Typeface = context.loadInterMedium()
    private lateinit var theValue: T

    init {
        widgetLayoutResource = R.layout.preference_status_label
        this.title = title // Forces setting fonts when Title is set via XML
    }

    override fun setTitle(titleResId: Int) {
        title = context.getString(titleResId)
    }

    override fun setTitle(title: CharSequence?) {
        title?.let { super.setTitle(title.applyFont(typeface)) } ?: super.setTitle(title)
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        textView = holder.itemView.findViewById(R.id.text_view_preference_status)
        if (!::theValue.isInitialized) { theValue = defaultValue }
        doUpdateValue(theValue, textView)
    }

    fun setValue(value: T) {
        this.theValue = value
        doUpdateValue(theValue, textView)
    }

    protected abstract val defaultValue: T

    protected abstract fun doUpdateValue(value: T, view: TextView)
}
