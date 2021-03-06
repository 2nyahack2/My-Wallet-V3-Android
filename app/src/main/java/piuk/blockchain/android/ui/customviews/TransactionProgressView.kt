package piuk.blockchain.android.ui.customviews

import android.content.Context
import android.text.SpannableStringBuilder
import android.text.method.LinkMovementMethod
import android.util.AttributeSet
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.constraintlayout.widget.ConstraintLayout
import kotlinx.android.synthetic.main.view_transaction_progress.view.*
import org.koin.core.KoinComponent
import piuk.blockchain.android.R
import piuk.blockchain.android.util.gone
import piuk.blockchain.android.util.visible

class TransactionProgressView(context: Context, attrs: AttributeSet) :
    ConstraintLayout(context, attrs), KoinComponent {

    init {
        inflate(context, R.layout.view_transaction_progress, this)
    }

    fun setAssetIcon(@DrawableRes assetIcon: Int) {
        tx_icon.setImageResource(assetIcon)
    }

    fun onCtaClick(fn: () -> Unit) {
        tx_ok_btn.setOnClickListener {
            fn()
        }
    }

    fun showTxInProgress(title: String, subtitle: String) {
        progress.visible()
        tx_state_indicator.gone()
        tx_ok_btn.gone()
        setText(title, subtitle)
    }

    fun showTxPending(title: String, subtitle: String) {
        progress.gone()
        tx_state_indicator.visible()
        tx_ok_btn.visible()
        tx_state_indicator.setImageResource(R.drawable.ic_pending_clock)
        setText(title, subtitle)
    }

    fun showTxSuccess(
        title: String,
        subtitle: String
    ) {
        tx_state_indicator.setImageResource(R.drawable.ic_check_circle)
        tx_state_indicator.visible()
        showEndStateUi()
        setText(title, subtitle)
    }

    fun showPendingTx(
        title: String,
        subtitle: SpannableStringBuilder
    ) {
        tx_state_indicator.setImageResource(R.drawable.ic_check_circle)
        tx_state_indicator.visible()
        showEndStateUi()
        tx_title.text = title
        tx_subtitle.run {
            setText(subtitle, TextView.BufferType.SPANNABLE)
            movementMethod = LinkMovementMethod.getInstance()
        }
    }

    fun showTxError(title: String, subtitle: String) {
        tx_icon.setImageResource(R.drawable.ic_alert)
        tx_state_indicator.gone()
        showEndStateUi()
        setText(title, subtitle)
    }

    private fun showEndStateUi() {
        progress.gone()
        tx_ok_btn.visible()
    }

    private fun setText(title: String, subtitle: String) {
        tx_title.text = title
        tx_subtitle.text = subtitle
    }
}