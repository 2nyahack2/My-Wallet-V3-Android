package piuk.blockchain.android.ui.transactionflow.flow.adapter

import android.view.View
import android.view.ViewGroup
import androidx.annotation.ColorRes
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.extensions.LayoutContainer
import kotlinx.android.synthetic.main.item_send_confirm_countdown.view.*
import piuk.blockchain.android.R
import piuk.blockchain.android.coincore.TxConfirmation
import piuk.blockchain.android.coincore.TxConfirmationValue
import piuk.blockchain.android.ui.adapters.AdapterDelegate
import piuk.blockchain.android.util.context
import piuk.blockchain.android.util.inflate
import java.util.concurrent.TimeUnit
import piuk.blockchain.androidcoreui.utils.extensions.getResolvedColor

class InvoiceCountdownTimerDelegate<in T> : AdapterDelegate<T> {
    override fun isForViewType(items: List<T>, position: Int): Boolean {
        return (items[position] as? TxConfirmationValue)?.confirmation == TxConfirmation.INVOICE_COUNTDOWN
    }

    override fun onCreateViewHolder(parent: ViewGroup): RecyclerView.ViewHolder =
        ViewHolder(parent.inflate(R.layout.item_send_confirm_countdown))

    override fun onBindViewHolder(
        items: List<T>,
        position: Int,
        holder: RecyclerView.ViewHolder
    ) = (holder as ViewHolder).bind(items[position] as TxConfirmationValue.BitPayCountdown)

    class ViewHolder(
        val view: View
    ) : RecyclerView.ViewHolder(view), LayoutContainer {

        override val containerView: View?
            get() = itemView

        fun bind(item: TxConfirmationValue.BitPayCountdown) {
            itemView.confirmation_item_label.setText(R.string.bitpay_remaining_time)
            updateCountdownElements(item.timeRemainingSecs)
        }

        private fun updateCountdownElements(remaining: Long) {
            val readableTime = if (remaining >= 0) {
                String.format(
                    "%2d:%02d",
                    TimeUnit.SECONDS.toMinutes(remaining),
                    TimeUnit.SECONDS.toSeconds(remaining) -
                        TimeUnit.MINUTES.toSeconds(TimeUnit.SECONDS.toMinutes(remaining))
                )
            } else {
                "--:--"
            }
            itemView.confirmation_item_value.text = readableTime

            when {
                remaining > FIVE_MINUTES -> setColors(R.color.primary_grey_light)
                remaining > ONE_MINUTE -> setColors(R.color.secondary_yellow_medium)
                else -> setColors(R.color.secondary_red_light)
            }
        }

        private fun setColors(@ColorRes colourResId: Int) {
            val resolved = context.getResolvedColor(colourResId)
            itemView.confirmation_item_value.setTextColor(resolved)
            itemView.confirmation_item_label.setTextColor(resolved)
        }

        companion object {
            private const val FIVE_MINUTES = 5 * 60
            private const val ONE_MINUTE = 60
        }
    }
}
