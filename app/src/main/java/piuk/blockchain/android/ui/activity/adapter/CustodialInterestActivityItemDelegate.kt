package piuk.blockchain.android.ui.activity.adapter

import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.blockchain.preferences.CurrencyPrefs
import com.blockchain.nabu.datamanagers.InterestState
import info.blockchain.balance.CryptoCurrency
import info.blockchain.wallet.multiaddress.TransactionSummary
import io.reactivex.disposables.CompositeDisposable
import kotlinx.android.synthetic.main.dialog_activities_tx_item.view.*
import piuk.blockchain.android.R
import piuk.blockchain.android.coincore.CustodialInterestActivitySummaryItem
import piuk.blockchain.android.ui.activity.CryptoActivityType
import piuk.blockchain.android.ui.adapters.AdapterDelegate
import piuk.blockchain.android.util.toFormattedDate
import piuk.blockchain.android.util.setAssetIconColours
import piuk.blockchain.android.util.gone
import piuk.blockchain.android.util.inflate
import java.util.Date

class CustodialInterestActivityItemDelegate<in T>(
    private val currencyPrefs: CurrencyPrefs,
    private val onItemClicked: (CryptoCurrency, String, CryptoActivityType) -> Unit // crypto, txID, type
) : AdapterDelegate<T> {

    override fun isForViewType(items: List<T>, position: Int): Boolean =
        items[position] is CustodialInterestActivitySummaryItem

    override fun onCreateViewHolder(parent: ViewGroup): RecyclerView.ViewHolder =
        CustodialInterestActivityItemViewHolder(parent.inflate(R.layout.dialog_activities_tx_item))

    override fun onBindViewHolder(
        items: List<T>,
        position: Int,
        holder: RecyclerView.ViewHolder
    ) = (holder as CustodialInterestActivityItemViewHolder).bind(
        items[position] as CustodialInterestActivitySummaryItem,
        currencyPrefs.selectedFiatCurrency,
        onItemClicked
    )
}

private class CustodialInterestActivityItemViewHolder(
    itemView: View
) : RecyclerView.ViewHolder(itemView) {

    private val disposables: CompositeDisposable = CompositeDisposable()

    fun bind(
        tx: CustodialInterestActivitySummaryItem,
        selectedFiatCurrency: String,
        onAccountClicked: (CryptoCurrency, String, CryptoActivityType) -> Unit
    ) {
        disposables.clear()
        with(itemView) {
            icon.setIcon(tx.isPending(), tx.type)
            if (tx.status.isPending().not()) {
                icon.setAssetIconColours(tx.cryptoCurrency, context)
            } else {
                icon.background = null
                icon.setColorFilter(Color.TRANSPARENT)
            }

            asset_balance_fiat.gone()
            asset_balance_crypto.text = tx.value.toStringWithSymbol()
            asset_balance_fiat.bindAndConvertFiatBalance(tx, disposables, selectedFiatCurrency)

            tx_type.setTxLabel(tx.cryptoCurrency, tx.type)
            status_date.setTxStatus(tx)
            setTextColours(tx.status)

            setOnClickListener {
                onAccountClicked(tx.cryptoCurrency, tx.txId, CryptoActivityType.CUSTODIAL_INTEREST)
            }
        }
    }

    private fun setTextColours(txStatus: InterestState) {
        with(itemView) {
            if (txStatus == InterestState.COMPLETE) {
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

private fun InterestState.isPending(): Boolean =
    this == InterestState.PENDING ||
        this == InterestState.PROCESSING ||
        this == InterestState.MANUAL_REVIEW

private fun ImageView.setIcon(txPending: Boolean, type: TransactionSummary.TransactionType) =
    setImageResource(
        if (txPending) {
            R.drawable.ic_tx_confirming
        } else {
            when (type) {
                TransactionSummary.TransactionType.DEPOSIT -> R.drawable.ic_tx_buy
                TransactionSummary.TransactionType.INTEREST_EARNED -> R.drawable.ic_tx_interest
                TransactionSummary.TransactionType.WITHDRAW -> R.drawable.ic_tx_sell
                else -> R.drawable.ic_tx_buy
            }
        }
    )

private fun TextView.setTxLabel(
    cryptoCurrency: CryptoCurrency,
    type: TransactionSummary.TransactionType
) {
    text = when (type) {
        TransactionSummary.TransactionType.DEPOSIT -> context.resources.getString(
            R.string.tx_title_transfer,
            cryptoCurrency.displayTicker)
        TransactionSummary.TransactionType.INTEREST_EARNED -> context.resources.getString(
            R.string.tx_title_interest,
            cryptoCurrency.displayTicker)
        TransactionSummary.TransactionType.WITHDRAW -> context.resources.getString(
            R.string.tx_title_withdraw,
            cryptoCurrency.displayTicker)
        else -> context.resources.getString(R.string.tx_title_transfer,
            cryptoCurrency.displayTicker)
    }
}

private fun TextView.setTxStatus(tx: CustodialInterestActivitySummaryItem) {
    text = when (tx.status) {
        InterestState.COMPLETE -> Date(tx.timeStampMs).toFormattedDate()
        InterestState.FAILED -> context.getString(R.string.activity_state_failed)
        InterestState.CLEARED -> context.getString(R.string.activity_state_cleared)
        InterestState.REFUNDED -> context.getString(R.string.activity_state_refunded)
        InterestState.PENDING -> context.getString(R.string.activity_state_pending)
        InterestState.PROCESSING -> context.getString(R.string.activity_state_pending)
        InterestState.MANUAL_REVIEW -> context.getString(R.string.activity_state_pending)
        InterestState.REJECTED -> context.getString(R.string.activity_state_rejected)
        InterestState.UNKNOWN -> context.getString(R.string.activity_state_unknown)
    }
}
