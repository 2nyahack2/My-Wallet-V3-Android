package piuk.blockchain.android.ui.customviews

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.blockchain.ui.urllinks.URL_BLOCKCHAIN_SUPPORT_PORTAL
import kotlinx.android.synthetic.main.view_empty_state.view.*
import piuk.blockchain.android.R
import piuk.blockchain.android.util.calloutToExternalSupportLinkDlg
import piuk.blockchain.android.util.visibleIf

class EmptyStateView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    init {
        inflate(context, R.layout.view_empty_state, this)
    }

    fun setDetails(
        @StringRes title: Int = R.string.common_empty_title,
        @StringRes description: Int = R.string.common_empty_details,
        @DrawableRes icon: Int = R.drawable.ic_wallet_intro_image,
        @StringRes ctaText: Int = R.string.common_empty_cta,
        contactSupportEnabled: Boolean = false,
        action: () -> Unit
    ) {
        view_empty_title.text = context.getString(title)
        view_empty_desc.text = context.getString(description)
        view_empty_icon.setImageDrawable(context.getDrawable(icon))
        view_empty_cta.text = context.getString(ctaText)
        view_empty_cta.setOnClickListener {
            action()
        }

        view_empty_support_cta.visibleIf {
            contactSupportEnabled
        }

        view_empty_support_cta.setOnClickListener {
            calloutToExternalSupportLinkDlg(context, URL_BLOCKCHAIN_SUPPORT_PORTAL)
        }
    }
}