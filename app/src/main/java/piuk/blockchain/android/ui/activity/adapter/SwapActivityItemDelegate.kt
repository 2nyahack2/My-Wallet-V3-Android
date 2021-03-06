package piuk.blockchain.android.ui.activity.adapter

import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.blockchain.nabu.datamanagers.CurrencyPair
import info.blockchain.balance.CryptoCurrency
import kotlinx.android.synthetic.main.dialog_activities_tx_item.view.*
import piuk.blockchain.android.R
import piuk.blockchain.android.coincore.TradeActivitySummaryItem
import piuk.blockchain.android.ui.activity.CryptoActivityType
import piuk.blockchain.android.ui.adapters.AdapterDelegate
import piuk.blockchain.android.util.toFormattedDate
import piuk.blockchain.android.util.setAssetIconColours
import piuk.blockchain.android.util.inflate
import piuk.blockchain.android.util.visible
import java.util.Date

class SwapActivityItemDelegate<in T>(
    private val onItemClicked: (CryptoCurrency, String, CryptoActivityType) -> Unit // crypto, txID, type
) : AdapterDelegate<T> {

    override fun isForViewType(items: List<T>, position: Int): Boolean =
        (items[position] as? TradeActivitySummaryItem)?.let {
            it.currencyPair is CurrencyPair.CryptoCurrencyPair
        } ?: false

    override fun onCreateViewHolder(parent: ViewGroup): RecyclerView.ViewHolder =
        SwapActivityItemViewHolder(parent.inflate(R.layout.dialog_activities_tx_item))

    override fun onBindViewHolder(
        items: List<T>,
        position: Int,
        holder: RecyclerView.ViewHolder
    ) = (holder as SwapActivityItemViewHolder).bind(
        items[position] as TradeActivitySummaryItem,
        onItemClicked
    )
}

private class SwapActivityItemViewHolder(
    itemView: View
) : RecyclerView.ViewHolder(itemView) {

    fun bind(
        tx: TradeActivitySummaryItem,
        onAccountClicked: (CryptoCurrency, String, CryptoActivityType) -> Unit
    ) {
        with(itemView) {

            status_date.text = Date(tx.timeStampMs).toFormattedDate()
            (tx.currencyPair as? CurrencyPair.CryptoCurrencyPair)?.let {
                tx_type.text = context.resources.getString(
                    R.string.tx_title_swap,
                    it.source.displayTicker,
                    it.destination.displayTicker
                )
                if (tx.state.isPending) {
                    icon.setIsConfirming()
                } else {
                    icon.setImageResource(R.drawable.ic_tx_swap)
                    icon.setAssetIconColours(it.source, context)
                }
                setOnClickListener { onAccountClicked(tx.currencyPair.source, tx.txId, CryptoActivityType.SWAP) }
            }

            setTextColours(tx.state.isPending)

            asset_balance_crypto.text = tx.value.toStringWithSymbol()
            asset_balance_fiat.text = tx.fiatValue.toStringWithSymbol()
            asset_balance_fiat.visible()
        }
    }

    private fun setTextColours(isPending: Boolean) {
        with(itemView) {
            if (!isPending) {
                tx_type.setTextColor(ContextCompat.getColor(context, R.color.black))
                status_date.setTextColor(ContextCompat.getColor(context, R.color.grey_600))
                asset_balance_fiat.setTextColor(ContextCompat.getColor(context, R.color.grey_600))
                asset_balance_crypto.setTextColor(ContextCompat.getColor(context, R.color.black))
            } else {
                tx_type.setTextColor(ContextCompat.getColor(context, R.color.grey_400))
                status_date.setTextColor(ContextCompat.getColor(context, R.color.grey_400))
                asset_balance_fiat.setTextColor(ContextCompat.getColor(context, R.color.grey_400))
                asset_balance_crypto.setTextColor(ContextCompat.getColor(context, R.color.grey_400))
            }
        }
    }
}

private fun ImageView.setIsConfirming() =
    icon.apply {
        setImageDrawable(
            AppCompatResources.getDrawable(
                context,
                R.drawable.ic_tx_confirming
            )
        )
        background = null
        setColorFilter(Color.TRANSPARENT)
    }
