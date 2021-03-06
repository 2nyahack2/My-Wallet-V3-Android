package piuk.blockchain.android.simplebuy

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.recyclerview.widget.RecyclerView
import com.blockchain.nabu.datamanagers.PaymentMethod
import kotlinx.android.synthetic.main.funds_payment_method_layout.view.*
import piuk.blockchain.android.R
import piuk.blockchain.android.ui.adapters.AdapterDelegate

class FundsPaymentDelegate : AdapterDelegate<PaymentMethodItem> {

    override fun isForViewType(items: List<PaymentMethodItem>, position: Int): Boolean =
        items[position].paymentMethod is PaymentMethod.Funds

    override fun onCreateViewHolder(parent: ViewGroup): RecyclerView.ViewHolder {
        val itemView =
            LayoutInflater.from(parent.context).inflate(
                R.layout.funds_payment_method_layout,
                parent,
                false
            )
        return ViewHolder(itemView)
    }

    override fun onBindViewHolder(items: List<PaymentMethodItem>, position: Int, holder: RecyclerView.ViewHolder) {
        (holder as ViewHolder).bind(items[position])
    }

    private class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: AppCompatTextView = itemView.payment_method_title
        val ticker: AppCompatTextView = itemView.ticker
        val balance: AppCompatTextView = itemView.balance
        val icon: ImageView = itemView.payment_method_icon
        val root: ViewGroup = itemView.payment_method_root

        fun bind(paymentMethodItem: PaymentMethodItem) {
            (paymentMethodItem.paymentMethod as? PaymentMethod.Funds)?.let {
                icon.setImageResource(it.icon())
                ticker.text = paymentMethodItem.paymentMethod.fiatCurrency
                title.text = title.context.getString(it.label())
                balance.text = it.balance.toStringWithSymbol()
            }
            root.setOnClickListener { paymentMethodItem.clickAction() }
        }
    }
}