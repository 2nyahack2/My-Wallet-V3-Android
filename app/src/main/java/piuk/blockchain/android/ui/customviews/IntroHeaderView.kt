package piuk.blockchain.android.ui.customviews

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import kotlinx.android.synthetic.main.view_intro_header.view.*
import piuk.blockchain.android.R
import piuk.blockchain.android.util.visibleIf

class IntroHeaderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    init {
        inflate(context, R.layout.view_intro_header, this)

        setupView(context, attrs)
    }

    private fun setupView(context: Context, attrs: AttributeSet?) {
        val attributes = context.obtainStyledAttributes(attrs, R.styleable.IntroHeaderView, 0, 0)

        attributes?.let {
            val title = it.getString(R.styleable.IntroHeaderView_intro_header_title)
            val label = it.getString(R.styleable.IntroHeaderView_intro_header_label)
            val icon = it.getDrawable(R.styleable.IntroHeaderView_intro_header_icon)
            val showSeparator = it.getBoolean(R.styleable.IntroHeaderView_intro_header_separator, true)

            intro_header_title.text = title
            intro_header_label.text = label
            intro_header_icon.setImageDrawable(icon)
            intro_header_separator.visibleIf { showSeparator }

            attributes.recycle()
        }
    }

    fun setDetails(
        @StringRes title: Int,
        @StringRes label: Int,
        @DrawableRes icon: Int,
        showSeparator: Boolean = true
    ) {
        intro_header_title.text = context.getString(title)
        intro_header_label.text = context.getString(label)
        intro_header_icon.setImageDrawable(context.getDrawable(icon))
        intro_header_separator.visibleIf { showSeparator }
    }

    fun toggleBottomSeparator(visible: Boolean) {
        intro_header_separator.visibleIf { visible }
    }
}