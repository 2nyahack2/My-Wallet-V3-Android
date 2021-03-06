package piuk.blockchain.android.ui.transactionflow.flow.adapter

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.extensions.LayoutContainer
import kotlinx.android.synthetic.main.item_send_large_tx_confirm_item.view.*
import piuk.blockchain.android.R
import piuk.blockchain.android.coincore.TxConfirmation
import piuk.blockchain.android.coincore.TxConfirmationValue
import piuk.blockchain.android.ui.adapters.AdapterDelegate
import piuk.blockchain.android.ui.transactionflow.engine.TransactionIntent
import piuk.blockchain.android.ui.transactionflow.engine.TransactionModel
import piuk.blockchain.android.util.inflate

class LargeTransactionWarningItemDelegate<in T>(
    private val model: TransactionModel
) : AdapterDelegate<T> {
    override fun isForViewType(items: List<T>, position: Int): Boolean =
        (items[position] as? TxConfirmationValue)?.confirmation == TxConfirmation.LARGE_TRANSACTION_WARNING

    override fun onCreateViewHolder(parent: ViewGroup): RecyclerView.ViewHolder =
        LargeTransactionViewHolder(
            parent.inflate(R.layout.item_send_large_tx_confirm_item)
        )

    @Suppress("UNCHECKED_CAST")
    override fun onBindViewHolder(
        items: List<T>,
        position: Int,
        holder: RecyclerView.ViewHolder
    ) = (holder as LargeTransactionViewHolder).bind(
        items[position] as TxConfirmationValue.TxBooleanConfirmation<Unit>,
        model
    )
}

private class LargeTransactionViewHolder(val parent: View) :
    RecyclerView.ViewHolder(parent),
    LayoutContainer {

    override val containerView: View?
        get() = itemView

    fun bind(
        item: TxConfirmationValue.TxBooleanConfirmation<Unit>,
        model: TransactionModel
    ) {
        with(itemView.confirm_checkbox) {
            isChecked = item.value
            setOnCheckedChangeListener { view, isChecked ->
                model.process(TransactionIntent.ModifyTxOption(item.copy(value = isChecked)))
                view.isEnabled = false
            }
        }
    }
}