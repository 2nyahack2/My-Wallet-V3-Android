package piuk.blockchain.android.ui.activity.adapter

import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.blockchain.nabu.datamanagers.TransactionType
import kotlinx.android.synthetic.main.layout_fiat_activity_item.view.*
import piuk.blockchain.android.R
import piuk.blockchain.android.coincore.FiatActivitySummaryItem
import piuk.blockchain.android.ui.adapters.AdapterDelegate
import piuk.blockchain.android.util.toFormattedDate
import piuk.blockchain.android.util.inflate
import java.util.Date

class CustodialFiatActivityItemDelegate<in T>(
    private val onItemClicked: (String, String) -> Unit
) : AdapterDelegate<T> {

    override fun isForViewType(items: List<T>, position: Int): Boolean =
        items[position] is FiatActivitySummaryItem

    override fun onCreateViewHolder(parent: ViewGroup): RecyclerView.ViewHolder =
        FiatActivityItemViewHolder(parent.inflate(R.layout.layout_fiat_activity_item))

    override fun onBindViewHolder(items: List<T>, position: Int, holder: RecyclerView.ViewHolder) {
        (holder as FiatActivityItemViewHolder).bind(
            items[position] as FiatActivitySummaryItem,
            onItemClicked
        )
    }
}

private class FiatActivityItemViewHolder(
    itemView: View
) : RecyclerView.ViewHolder(itemView) {

    internal fun bind(
        tx: FiatActivitySummaryItem,
        onAccountClicked: (String, String) -> Unit
    ) {
        with(itemView) {
            icon.apply {
                setImageResource(
                    if (tx.type == TransactionType.DEPOSIT)
                        R.drawable.ic_tx_buy else
                        R.drawable.ic_tx_sell
                )
                setBackgroundResource(R.drawable.bkgd_tx_circle)
                background.setTint(ContextCompat.getColor(context, R.color.green_500_fade_15))
                setColorFilter(ContextCompat.getColor(context, R.color.green_500))
            }

            tx_type.setTxLabel(tx.currency, tx.type)

            status_date.text = Date(tx.timeStampMs).toFormattedDate()

            asset_balance_fiat.text = tx.value.toStringWithSymbol()

            setOnClickListener { onAccountClicked(tx.currency, tx.txId) }
        }
    }
}

private fun AppCompatTextView.setTxLabel(currency: String, type: TransactionType) {
    text = when (type) {
        TransactionType.DEPOSIT -> context.getString(R.string.tx_title_deposit, currency)
        else -> context.getString(R.string.tx_title_withdraw, currency)
    }
}
