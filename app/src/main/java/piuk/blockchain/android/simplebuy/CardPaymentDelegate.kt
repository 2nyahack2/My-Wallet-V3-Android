package piuk.blockchain.android.simplebuy

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.recyclerview.widget.RecyclerView
import com.blockchain.nabu.datamanagers.PaymentMethod
import kotlinx.android.synthetic.main.card_payment_method_layout.view.*
import piuk.blockchain.android.R
import piuk.blockchain.android.cards.icon
import piuk.blockchain.android.ui.adapters.AdapterDelegate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CardPaymentDelegate : AdapterDelegate<PaymentMethodItem> {

    override fun isForViewType(items: List<PaymentMethodItem>, position: Int): Boolean =
        items[position].paymentMethod is PaymentMethod.Card

    override fun onCreateViewHolder(parent: ViewGroup): RecyclerView.ViewHolder {
        val itemView =
            LayoutInflater.from(parent.context).inflate(
                R.layout.card_payment_method_layout,
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
        val limit: AppCompatTextView = itemView.payment_method_limit
        val icon: ImageView = itemView.payment_method_icon
        val root: ViewGroup = itemView.payment_method_root
        val expiryDate: AppCompatTextView = itemView.exp_date
        val cardNumber: AppCompatTextView = itemView.card_number

        fun bind(paymentMethodItem: PaymentMethodItem) {
            (paymentMethodItem.paymentMethod as? PaymentMethod.Card)?.let {
                icon.setImageResource(it.cardType.icon())
                limit.text =
                    limit.context.getString(R.string.payment_method_limit,
                        paymentMethodItem.paymentMethod.limits.max.toStringWithSymbol())
                title.text = it.uiLabel()
                cardNumber.text = it.dottedEndDigits()
                expiryDate.text = expiryDate.context.getString(R.string.card_expiry_date, it.expireDate.formatted())
            }
            root.setOnClickListener { paymentMethodItem.clickAction() }
        }

        private fun Date.formatted(): String =
            SimpleDateFormat("MM/yyyy", Locale.getDefault()).format(this)
    }
}