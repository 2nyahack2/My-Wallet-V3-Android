package piuk.blockchain.android.ui.transactionflow.flow.adapter

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.extensions.LayoutContainer
import kotlinx.android.synthetic.main.item_send_confirm_details.view.*
import piuk.blockchain.android.R
import piuk.blockchain.android.coincore.TxConfirmation
import piuk.blockchain.android.coincore.TxConfirmationValue
import piuk.blockchain.android.ui.adapters.AdapterDelegate
import piuk.blockchain.android.ui.transactionflow.flow.TxConfirmReadOnlyMapper
import piuk.blockchain.android.util.inflate

class ConfirmInfoItemDelegate<in T>(private val mapper: TxConfirmReadOnlyMapper) :
    AdapterDelegate<T> {
    override fun isForViewType(items: List<T>, position: Int): Boolean {
        return (items[position] as? TxConfirmationValue)?.confirmation == TxConfirmation.READ_ONLY
    }

    override fun onCreateViewHolder(parent: ViewGroup): RecyclerView.ViewHolder =
        InfoItemViewHolder(
            parent.inflate(R.layout.item_send_confirm_details),
            mapper
        )

    override fun onBindViewHolder(
        items: List<T>,
        position: Int,
        holder: RecyclerView.ViewHolder
    ) = (holder as InfoItemViewHolder).bind(
        items[position] as TxConfirmationValue
    )
}

class InfoItemViewHolder(val parent: View, private val mapper: TxConfirmReadOnlyMapper) :
    RecyclerView.ViewHolder(parent),
    LayoutContainer {

    override val containerView: View?
        get() = itemView

    fun bind(item: TxConfirmationValue) {
        mapper.map(item).let { (title, value) ->
            itemView.confirmation_item_label.text = title
            itemView.confirmation_item_value.text = value
        }
    }
}
