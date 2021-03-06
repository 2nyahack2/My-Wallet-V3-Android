package piuk.blockchain.android.simplebuy

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import androidx.recyclerview.widget.RecyclerView
import com.blockchain.nabu.datamanagers.PaymentMethod
import kotlinx.android.synthetic.main.add_new_card_layout.view.*
import piuk.blockchain.android.R
import piuk.blockchain.android.ui.adapters.AdapterDelegate

class LinkBankDelegate : AdapterDelegate<PaymentMethodItem> {

    override fun isForViewType(items: List<PaymentMethodItem>, position: Int): Boolean =
        items[position].paymentMethod is PaymentMethod.UndefinedBankTransfer

    override fun onCreateViewHolder(parent: ViewGroup): RecyclerView.ViewHolder {
        val itemView =
            LayoutInflater.from(parent.context).inflate(
                R.layout.link_bank_layout,
                parent,
                false
            )
        return ViewHolder(itemView)
    }

    override fun onBindViewHolder(items: List<PaymentMethodItem>, position: Int, holder: RecyclerView.ViewHolder) {
        val headerViewHolder = holder as ViewHolder
        headerViewHolder.bind(items[position])
    }

    private class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val limit: AppCompatTextView = itemView.payment_method_limit
        val root: ViewGroup = itemView.payment_method_root

        fun bind(paymentMethodItem: PaymentMethodItem) {
            (paymentMethodItem.paymentMethod as? PaymentMethod.UndefinedBankTransfer)?.let {
                limit.text =
                    limit.context.getString(R.string.payment_method_limit,
                        paymentMethodItem.paymentMethod.limits.max.toStringWithSymbol())
            }
            root.setOnClickListener { paymentMethodItem.clickAction() }
        }
    }
}