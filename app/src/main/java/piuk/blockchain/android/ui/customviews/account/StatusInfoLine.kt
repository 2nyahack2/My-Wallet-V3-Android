package piuk.blockchain.android.ui.customviews.account

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import kotlinx.android.synthetic.main.view_status_line_info.view.*
import piuk.blockchain.android.R
import piuk.blockchain.android.util.visibleIf

class StatusInfoLine @JvmOverloads constructor(
    ctx: Context,
    attr: AttributeSet? = null,
    defStyle: Int = 0
) : LinearLayout(ctx, attr, defStyle) {

    init {
        LayoutInflater.from(context)
            .inflate(R.layout.view_status_line_info, this, true)
    }

    var status: String = ""
        set(value) {
            field = value
            refreshText()
        }

    var textColour: Int = -1
        set(value) {
            field = value
            refreshTextColour()
        }

    var background: Int = -1
        set(value) {
            field = value
            refreshBkgdColour()
        }

    private fun refreshTextColour() {
        message.setTextColor(ContextCompat.getColor(context, textColour))
    }

    private fun refreshBkgdColour() {
        item_account_parent.background =
            ContextCompat.getDrawable(context, background)
    }

    var isIconVisible: Boolean = false
        set(value) {
            field = value
            refreshIconVisibility()
        }

    private fun refreshIconVisibility() {
        icon.visibleIf { isIconVisible }
    }

    private fun refreshText() {
        message.text = status
    }
}
