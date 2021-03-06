package piuk.blockchain.android.ui.activity.adapter

import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.blockchain.preferences.CurrencyPrefs
import info.blockchain.balance.CryptoCurrency
import info.blockchain.wallet.multiaddress.TransactionSummary
import io.reactivex.disposables.CompositeDisposable
import kotlinx.android.synthetic.main.dialog_activities_tx_item.view.*
import piuk.blockchain.android.R
import piuk.blockchain.android.coincore.NonCustodialActivitySummaryItem
import piuk.blockchain.android.ui.activity.CryptoActivityType
import piuk.blockchain.android.ui.adapters.AdapterDelegate
import piuk.blockchain.android.util.toFormattedDate
import piuk.blockchain.android.util.setAssetIconColours
import piuk.blockchain.android.util.gone
import piuk.blockchain.android.util.inflate
import java.util.Date

class NonCustodialActivityItemDelegate<in T>(
    private val currencyPrefs: CurrencyPrefs,
    private val onItemClicked: (CryptoCurrency, String, CryptoActivityType) -> Unit // crypto, txID, type
) : AdapterDelegate<T> {

    override fun isForViewType(items: List<T>, position: Int): Boolean =
        items[position] is NonCustodialActivitySummaryItem

    override fun onCreateViewHolder(parent: ViewGroup): RecyclerView.ViewHolder =
        NonCustodialActivityItemViewHolder(parent.inflate(R.layout.dialog_activities_tx_item))

    override fun onBindViewHolder(
        items: List<T>,
        position: Int,
        holder: RecyclerView.ViewHolder
    ) = (holder as NonCustodialActivityItemViewHolder).bind(
        items[position] as NonCustodialActivitySummaryItem,
        currencyPrefs.selectedFiatCurrency,
        onItemClicked
    )
}

private class NonCustodialActivityItemViewHolder(
    itemView: View
) : RecyclerView.ViewHolder(itemView) {

    private val disposables: CompositeDisposable = CompositeDisposable()

    fun bind(
        tx: NonCustodialActivitySummaryItem,
        selectedFiatCurrency: String,
        onAccountClicked: (CryptoCurrency, String, CryptoActivityType) -> Unit
    ) {
        disposables.clear()
        with(itemView) {
            if (tx.isConfirmed) {
                icon.setTransactionTypeIcon(tx.transactionType, tx.isFeeTransaction)
                icon.setAssetIconColours(tx.cryptoCurrency, context)
            } else {
                icon.setIsConfirming()
            }

            status_date.text = Date(tx.timeStampMs).toFormattedDate()

            tx_type.setTxLabel(tx.cryptoCurrency, tx.transactionType, tx.isFeeTransaction)

            setTextColours(tx.isConfirmed)

            asset_balance_fiat.gone()
            asset_balance_crypto.text = tx.value.toStringWithSymbol()
            asset_balance_fiat.bindAndConvertFiatBalance(tx, disposables, selectedFiatCurrency)

            setOnClickListener { onAccountClicked(tx.cryptoCurrency, tx.txId, CryptoActivityType.NON_CUSTODIAL) }
        }
    }

    private fun setTextColours(isConfirmed: Boolean) {
        with(itemView) {
            if (isConfirmed) {
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

private fun ImageView.setTransactionTypeIcon(
    transactionType: TransactionSummary.TransactionType,
    isFeeTransaction: Boolean
) {
    setImageResource(
        if (isFeeTransaction) {
            R.drawable.ic_tx_sent
        } else {
            when (transactionType) {
                TransactionSummary.TransactionType.TRANSFERRED -> R.drawable.ic_tx_transfer
                TransactionSummary.TransactionType.RECEIVED -> R.drawable.ic_tx_receive
                TransactionSummary.TransactionType.SENT -> R.drawable.ic_tx_sent
                TransactionSummary.TransactionType.BUY -> R.drawable.ic_tx_buy
                TransactionSummary.TransactionType.SELL -> R.drawable.ic_tx_sell
                TransactionSummary.TransactionType.SWAP -> R.drawable.ic_tx_swap
                else -> R.drawable.ic_tx_buy
            }
        }
    )
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

private fun TextView.setTxLabel(
    cryptoCurrency: CryptoCurrency,
    transactionType: TransactionSummary.TransactionType,
    isFeeTransaction: Boolean
) {
    val resId = if (isFeeTransaction) {
        R.string.tx_title_fee
    } else {
        when (transactionType) {
            TransactionSummary.TransactionType.TRANSFERRED -> R.string.tx_title_transfer
            TransactionSummary.TransactionType.RECEIVED -> R.string.tx_title_receive
            TransactionSummary.TransactionType.SENT -> R.string.tx_title_send
            TransactionSummary.TransactionType.BUY -> R.string.tx_title_buy
            TransactionSummary.TransactionType.SELL -> R.string.tx_title_sell
            TransactionSummary.TransactionType.SWAP -> R.string.tx_title_swap
            else -> R.string.empty
        }
    }

    text = context.resources.getString(resId, cryptoCurrency.displayTicker)
}
