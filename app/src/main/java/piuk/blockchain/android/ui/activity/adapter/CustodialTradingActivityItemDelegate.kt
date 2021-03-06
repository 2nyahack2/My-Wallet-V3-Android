package piuk.blockchain.android.ui.activity.adapter

import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.blockchain.nabu.datamanagers.OrderState
import com.blockchain.nabu.datamanagers.custodialwalletimpl.OrderType
import com.blockchain.preferences.CurrencyPrefs
import info.blockchain.balance.CryptoCurrency
import io.reactivex.disposables.CompositeDisposable
import kotlinx.android.synthetic.main.dialog_activities_tx_item.view.*
import piuk.blockchain.android.R
import piuk.blockchain.android.coincore.CustodialTradingActivitySummaryItem
import piuk.blockchain.android.ui.activity.CryptoActivityType
import piuk.blockchain.android.ui.adapters.AdapterDelegate
import piuk.blockchain.android.util.toFormattedDate
import piuk.blockchain.android.util.setAssetIconColours
import piuk.blockchain.android.util.inflate
import java.util.Date

class CustodialTradingActivityItemDelegate<in T>(
    private val currencyPrefs: CurrencyPrefs,
    private val onItemClicked: (CryptoCurrency, String, CryptoActivityType) -> Unit // crypto, txID, type
) : AdapterDelegate<T> {

    override fun isForViewType(items: List<T>, position: Int): Boolean =
        items[position] is CustodialTradingActivitySummaryItem

    override fun onCreateViewHolder(parent: ViewGroup): RecyclerView.ViewHolder =
        CustodialTradingActivityItemViewHolder(parent.inflate(R.layout.dialog_activities_tx_item))

    override fun onBindViewHolder(
        items: List<T>,
        position: Int,
        holder: RecyclerView.ViewHolder
    ) = (holder as CustodialTradingActivityItemViewHolder).bind(
        items[position] as CustodialTradingActivitySummaryItem,
        currencyPrefs.selectedFiatCurrency,
        onItemClicked
    )
}

private class CustodialTradingActivityItemViewHolder(
    itemView: View
) : RecyclerView.ViewHolder(itemView) {

    private val disposables: CompositeDisposable = CompositeDisposable()

    fun bind(
        tx: CustodialTradingActivitySummaryItem,
        selectedFiatCurrency: String,
        onAccountClicked: (CryptoCurrency, String, CryptoActivityType) -> Unit
    ) {
        disposables.clear()
        with(itemView) {
            icon.setIcon(tx.status, tx.type)
            if (tx.status.isPending().not()) {
                icon.setAssetIconColours(tx.cryptoCurrency, context)
            } else {
                icon.background = null
                icon.setColorFilter(Color.TRANSPARENT)
            }

            tx_type.setTxLabel(tx.cryptoCurrency, tx.type)

            status_date.setTxStatus(tx)
            setTextColours(tx.status)

            asset_balance_fiat.bindAndConvertFiatBalance(tx, disposables, selectedFiatCurrency)

            asset_balance_crypto.text = tx.value.toStringWithSymbol()

            setOnClickListener {
                onAccountClicked(tx.cryptoCurrency, tx.txId, CryptoActivityType.CUSTODIAL_TRADING)
            }
        }
    }

    private fun setTextColours(txStatus: OrderState) {
        with(itemView) {
            if (txStatus == OrderState.FINISHED) {
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

private fun OrderState.isPending(): Boolean =
    this == OrderState.PENDING_CONFIRMATION ||
            this == OrderState.PENDING_EXECUTION ||
            this == OrderState.AWAITING_FUNDS

private fun ImageView.setIcon(status: OrderState, type: OrderType) =
    setImageResource(
        when (status) {
            OrderState.FINISHED -> if (type == OrderType.BUY) R.drawable.ic_tx_buy else R.drawable.ic_tx_sell
            OrderState.AWAITING_FUNDS,
            OrderState.PENDING_CONFIRMATION,
            OrderState.PENDING_EXECUTION -> R.drawable.ic_tx_confirming
            OrderState.UNINITIALISED, // should not see these next ones ATM
            OrderState.INITIALISED,
            OrderState.UNKNOWN,
            OrderState.CANCELED,
            OrderState.FAILED -> if (type == OrderType.BUY) R.drawable.ic_tx_buy else R.drawable.ic_tx_sell
        }
    )

private fun TextView.setTxLabel(cryptoCurrency: CryptoCurrency, type: OrderType) {
    text = context.resources.getString(
        if (type == OrderType.BUY) R.string.tx_title_buy else R.string.tx_title_sell, cryptoCurrency.displayTicker)
}

private fun TextView.setTxStatus(tx: CustodialTradingActivitySummaryItem) {
    text = when (tx.status) {
        OrderState.FINISHED -> Date(tx.timeStampMs).toFormattedDate()
        OrderState.UNINITIALISED -> context.getString(R.string.activity_state_uninitialised)
        OrderState.INITIALISED -> context.getString(R.string.activity_state_initialised)
        OrderState.AWAITING_FUNDS -> context.getString(R.string.activity_state_awaiting_funds)
        OrderState.PENDING_EXECUTION -> context.getString(R.string.activity_state_pending)
        OrderState.PENDING_CONFIRMATION -> context.getString(R.string.activity_state_pending)
        OrderState.UNKNOWN -> context.getString(R.string.activity_state_unknown)
        OrderState.CANCELED -> context.getString(R.string.activity_state_canceled)
        OrderState.FAILED -> context.getString(R.string.activity_state_failed)
    }
}
