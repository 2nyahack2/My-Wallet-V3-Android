package piuk.blockchain.android.ui.transactionflow.flow.adapter

import android.app.Activity
import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.StyleSpan
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.recyclerview.widget.RecyclerView
import com.blockchain.ui.urllinks.URL_TX_FEES
import kotlinx.android.extensions.LayoutContainer
import kotlinx.android.synthetic.main.item_send_confirm_network_fee.*
import piuk.blockchain.android.R
import piuk.blockchain.android.coincore.TxConfirmationValue
import piuk.blockchain.android.ui.adapters.AdapterDelegate
import piuk.blockchain.android.util.StringUtils
import piuk.blockchain.android.util.assetName
import piuk.blockchain.android.util.gone
import piuk.blockchain.android.util.inflate
import piuk.blockchain.android.util.visible

class ConfirmNetworkFeeItemDelegate<in T>(
    private val activityContext: Activity,
    private val stringUtils: StringUtils
) : AdapterDelegate<T> {
    override fun isForViewType(items: List<T>, position: Int): Boolean {
        return items[position] is TxConfirmationValue.NetworkFee
    }

    override fun onCreateViewHolder(parent: ViewGroup): RecyclerView.ViewHolder =
        NetworkFeeItemViewHolder(parent.inflate(R.layout.item_send_confirm_network_fee), activityContext, stringUtils)

    override fun onBindViewHolder(
        items: List<T>,
        position: Int,
        holder: RecyclerView.ViewHolder
    ) = (holder as NetworkFeeItemViewHolder).bind(
        items[position] as TxConfirmationValue.NetworkFee
    )
}

private class NetworkFeeItemViewHolder(
    val parent: View,
    val activityContext: Activity,
    val stringUtils: StringUtils
) : RecyclerView.ViewHolder(parent), LayoutContainer {

    override val containerView: View?
        get() = itemView

    fun bind(
        item: TxConfirmationValue.NetworkFee
    ) {
        val linksMap = mapOf<String, Uri>("send_tx_fees" to Uri.parse(URL_TX_FEES))

        with(itemView) {
            confirmation_fee_label.text =
                context.getString(R.string.tx_confirmation_network_fee, item.txFee.asset.displayTicker)

            if (item.txFee.fee.isZero) {
                confirmation_fee_value.gone()
                confirmation_free_label.visible()

                confirmation_learn_more.setText(getFreeFeesText(context, linksMap), TextView.BufferType.SPANNABLE)
            } else {
                confirmation_fee_value.text = item.txFee.fee.toStringWithSymbol()
                confirmation_fee_value.visible()
                confirmation_free_label.gone()

                confirmation_learn_more.setText(getFeesText(context, linksMap, item.txFee.asset.assetName()),
                    TextView.BufferType.SPANNABLE)
            }

            confirmation_learn_more.movementMethod = LinkMovementMethod.getInstance()
        }
    }

    private fun getFreeFeesText(context: Context, linksMap: Map<String, Uri>): SpannableStringBuilder {
        val introText = context.getString(R.string.tx_confirmation_free_fee_learn_more_1)
        val boldText = context.getString(R.string.tx_confirmation_free_fee_learn_more_2)
        val linkedText = stringUtils.getStringWithMappedAnnotations(
            R.string.tx_confirmation_free_fee_learn_more_3,
            linksMap,
            activityContext
        )
        val sb = SpannableStringBuilder()
            .append(introText)
            .append(boldText)
            .append(linkedText)

        sb.setSpan(StyleSpan(Typeface.BOLD), introText.length,
            introText.length + boldText.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        return sb
    }

    private fun getFeesText(
        context: Context,
        linksMap: Map<String, Uri>,
        @StringRes assetName: Int
    ): SpannableStringBuilder {
        val boldText = context.getString(R.string.tx_confirmation_fee_learn_more_1)
        val networkText = context.getString(R.string.tx_confirmation_fee_learn_more_2, context.getString(assetName))
        val linkedText = stringUtils.getStringWithMappedAnnotations(
            R.string.tx_confirmation_fee_learn_more_3,
            linksMap,
            activityContext
        )

        val sb = SpannableStringBuilder()
            .append(boldText)
            .append(networkText)
            .append(linkedText)

        sb.setSpan(StyleSpan(Typeface.BOLD), 0, boldText.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        return sb
    }
}
