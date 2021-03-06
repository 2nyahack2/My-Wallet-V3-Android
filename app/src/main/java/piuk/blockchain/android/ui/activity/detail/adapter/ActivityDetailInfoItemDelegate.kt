package piuk.blockchain.android.ui.activity.detail.adapter

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.blockchain.nabu.datamanagers.PaymentMethod
import info.blockchain.balance.CryptoValue
import info.blockchain.wallet.multiaddress.TransactionSummary
import kotlinx.android.extensions.LayoutContainer
import kotlinx.android.synthetic.main.item_list_info_row.view.*
import piuk.blockchain.android.R
import piuk.blockchain.android.ui.activity.detail.Action
import piuk.blockchain.android.ui.activity.detail.ActivityDetailsType
import piuk.blockchain.android.ui.activity.detail.Amount
import piuk.blockchain.android.ui.activity.detail.BuyCryptoWallet
import piuk.blockchain.android.ui.activity.detail.BuyFee
import piuk.blockchain.android.ui.activity.detail.BuyPaymentMethod
import piuk.blockchain.android.ui.activity.detail.BuyPurchaseAmount
import piuk.blockchain.android.ui.activity.detail.Created
import piuk.blockchain.android.ui.activity.detail.Description
import piuk.blockchain.android.ui.activity.detail.Fee
import piuk.blockchain.android.ui.activity.detail.FeeForTransaction
import piuk.blockchain.android.ui.activity.detail.From
import piuk.blockchain.android.ui.activity.detail.HistoricValue
import piuk.blockchain.android.ui.activity.detail.NetworkFee
import piuk.blockchain.android.ui.activity.detail.SellCryptoWallet
import piuk.blockchain.android.ui.activity.detail.SellPurchaseAmount
import piuk.blockchain.android.ui.activity.detail.SwapReceiveAmount
import piuk.blockchain.android.ui.activity.detail.To
import piuk.blockchain.android.ui.activity.detail.TransactionId
import piuk.blockchain.android.ui.activity.detail.Value
import piuk.blockchain.android.ui.activity.detail.XlmMemo
import piuk.blockchain.android.ui.adapters.AdapterDelegate
import piuk.blockchain.android.util.assetName
import piuk.blockchain.android.util.inflate
import piuk.blockchain.android.util.toFormattedString

class ActivityDetailInfoItemDelegate<in T> : AdapterDelegate<T> {
    override fun isForViewType(items: List<T>, position: Int): Boolean {
        val item = items[position] as ActivityDetailsType
        return item !is Action && item !is Description
    }

    override fun onCreateViewHolder(parent: ViewGroup): RecyclerView.ViewHolder =
        InfoItemViewHolder(
            parent.inflate(R.layout.item_list_info_row)
        )

    override fun onBindViewHolder(
        items: List<T>,
        position: Int,
        holder: RecyclerView.ViewHolder
    ) = (holder as InfoItemViewHolder).bind(
        items[position] as ActivityDetailsType
    )
}

private class InfoItemViewHolder(var parent: View) : RecyclerView.ViewHolder(parent),
    LayoutContainer {
    override val containerView: View
        get() = itemView

    fun bind(item: ActivityDetailsType) {
        itemView.item_list_info_row_title.text = getHeaderForType(item)
        itemView.item_list_info_row_description.text = getValueForType(item)
    }

    private fun getHeaderForType(infoType: ActivityDetailsType): String =
        when (infoType) {
            is Created -> parent.context.getString(R.string.activity_details_created)
            is Amount -> parent.context.getString(R.string.activity_details_amount)
            is Fee -> parent.context.getString(R.string.activity_details_fee)
            is Value -> parent.context.getString(R.string.activity_details_value)
            is HistoricValue -> {
                when (infoType.transactionType) {
                    TransactionSummary.TransactionType.SENT,
                    TransactionSummary.TransactionType.SELL ->
                        parent.context.getString(R.string.activity_details_historic_sent)
                    TransactionSummary.TransactionType.RECEIVED,
                    TransactionSummary.TransactionType.BUY ->
                        parent.context.getString(R.string.activity_details_historic_received)
                    TransactionSummary.TransactionType.TRANSFERRED,
                    TransactionSummary.TransactionType.SWAP
                    -> parent.context.getString(R.string.activity_details_historic_transferred)
                    else -> parent.context.getString(R.string.empty)
                }
            }
            is To -> parent.context.getString(R.string.activity_details_to)
            is From -> parent.context.getString(R.string.activity_details_from)
            is FeeForTransaction -> parent.context.getString(R.string.activity_details_transaction_fee)
            is BuyFee -> parent.context.getString(R.string.activity_details_buy_fees)
            is BuyPurchaseAmount -> parent.context.getString(R.string.activity_details_buy_purchase_amount)
            is SellPurchaseAmount -> parent.context.getString(R.string.common_total)
            is TransactionId -> parent.context.getString(R.string.activity_details_buy_tx_id)
            is BuyCryptoWallet,
            is SellCryptoWallet -> parent.context.getString(R.string.activity_details_buy_sending_to)
            is BuyPaymentMethod -> parent.context.getString(R.string.activity_details_buy_payment_method)
            is SwapReceiveAmount -> parent.context.getString(R.string.activity_details_swap_for)
            is NetworkFee -> parent.context.getString(
                R.string.tx_confirmation_network_fee,
                (infoType.feeValue as CryptoValue).currency.displayTicker
            )
            is XlmMemo -> parent.context.getString(R.string.xlm_memo_text)
            else -> parent.context.getString(R.string.empty)
        }

    private fun getValueForType(infoType: ActivityDetailsType): String =
        when (infoType) {
            is Created -> infoType.date.toFormattedString()
            is Amount -> infoType.value.toStringWithSymbol()
            is Fee -> infoType.feeValue?.toStringWithSymbol() ?: parent.context.getString(
                R.string.activity_details_fee_load_fail
            )
            is Value -> infoType.currentFiatValue?.toStringWithSymbol() ?: parent.context.getString(
                R.string.activity_details_value_load_fail
            )
            is HistoricValue -> infoType.fiatAtExecution?.toStringWithSymbol()
                ?: parent.context.getString(
                    R.string.activity_details_historic_value_load_fail
                )
            is To -> infoType.toAddress ?: parent.context.getString(
                R.string.activity_details_to_load_fail
            )
            is From -> infoType.fromAddress ?: parent.context.getString(
                R.string.activity_details_from_load_fail
            )
            is FeeForTransaction -> {
                when (infoType.transactionType) {
                    TransactionSummary.TransactionType.SENT -> parent.context.getString(
                        R.string.activity_details_transaction_fee_send,
                        infoType.cryptoValue.toStringWithSymbol()
                    )
                    else -> parent.context.getString(
                        R.string.activity_details_transaction_fee_unknown
                    )
                }
            }
            is BuyFee -> infoType.feeValue.toStringWithSymbol()
            is BuyPurchaseAmount -> infoType.fundedFiat.toStringWithSymbol()
            is TransactionId -> infoType.txId
            is BuyCryptoWallet -> parent.context.getString(
                R.string.custodial_wallet_default_label,
                parent.context.getString(infoType.crypto.assetName())
            )
            is SellCryptoWallet -> parent.context.getString(R.string.currency_funds_wallet, infoType.currency)
            is SellPurchaseAmount -> infoType.value.toStringWithSymbol()
            is BuyPaymentMethod -> {
                when {
                    infoType.paymentDetails.endDigits != null &&
                        infoType.paymentDetails.label != null -> {

                        with(parent.context) {
                            infoType.paymentDetails.accountType?.let {
                                val accType = getString(
                                    R.string.payment_method_type_account_info,
                                    infoType.paymentDetails.accountType,
                                    infoType.paymentDetails.endDigits
                                )

                                getString(
                                    R.string.common_spaced_strings,
                                    infoType.paymentDetails.label,
                                    accType
                                )
                            } ?: getString(
                                R.string.common_hyphenated_strings,
                                infoType.paymentDetails.label,
                                infoType.paymentDetails.endDigits
                            )
                        }
                    }
                    infoType.paymentDetails.paymentMethodId == PaymentMethod.FUNDS_PAYMENT_ID -> {
                        parent.context.getString(R.string.checkout_funds_label)
                    }
                    else -> {
                        parent.context.getString(R.string.activity_details_payment_load_fail)
                    }
                }
            }
            is SwapReceiveAmount -> infoType.receivedAmount.toStringWithSymbol()
            is NetworkFee -> infoType.feeValue.toStringWithSymbol()
            is XlmMemo -> infoType.memo
            else -> ""
        }
}
