package piuk.blockchain.android.ui.swap

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.blockchain.nabu.datamanagers.CustodialOrder
import info.blockchain.balance.CryptoValue
import info.blockchain.balance.Money
import kotlinx.android.synthetic.main.swap_pending_item_layout.view.*
import piuk.blockchain.android.R
import piuk.blockchain.android.util.toFormattedDate
import piuk.blockchain.android.util.setAssetIconColours
import piuk.blockchain.android.util.inflate

class PendingSwapsAdapter(
    private val orders: List<CustodialOrder>,
    private val toFiat: (Money) -> Money
) :
    RecyclerView.Adapter<PendingSwapsAdapter.PendingSwapViewHolder>() {

    override fun getItemCount(): Int = orders.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PendingSwapViewHolder =
        PendingSwapViewHolder(
            parent.inflate(R.layout.swap_pending_item_layout)
        )

    override fun onBindViewHolder(holder: PendingSwapViewHolder, position: Int) {
        holder.bind(orders[position], toFiat)
    }

    class PendingSwapViewHolder(private val parent: View) : RecyclerView.ViewHolder(parent) {
        fun bind(custodialOrder: CustodialOrder, toFiat: (Money) -> Money) {
            with(parent) {
                title.text = resources.getString(
                    R.string.swap_direction, (custodialOrder.inputMoney as CryptoValue).currency.displayTicker,
                    (custodialOrder.outputMoney as CryptoValue).currency.displayTicker)
                subtitle.text = custodialOrder.createdAt.toFormattedDate()
                fiatvalue.text = toFiat(custodialOrder.inputMoney).toStringWithSymbol()
                cryptovalue.text = custodialOrder.inputMoney.toStringWithSymbol()
                icon.setAssetIconColours((custodialOrder.inputMoney as CryptoValue).currency, context)
            }
        }
    }
}