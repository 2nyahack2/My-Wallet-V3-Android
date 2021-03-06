package piuk.blockchain.android.ui.activity.detail

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.blockchain.koin.scopedInject
import com.blockchain.nabu.datamanagers.TransactionState
import com.blockchain.nabu.datamanagers.TransactionType
import kotlinx.android.synthetic.main.dialog_sheet_activity_details.view.*
import piuk.blockchain.android.R
import piuk.blockchain.android.coincore.FiatActivitySummaryItem
import piuk.blockchain.android.repositories.AssetActivityRepository
import piuk.blockchain.android.ui.activity.detail.adapter.FiatDetailsSheetAdapter
import piuk.blockchain.android.ui.base.SlidingModalBottomDialog
import piuk.blockchain.android.ui.customviews.BlockchainListDividerDecor
import piuk.blockchain.android.util.toFormattedString
import piuk.blockchain.androidcore.utils.helperfunctions.unsafeLazy
import piuk.blockchain.android.util.gone
import java.util.Date

class FiatActivityDetailsBottomSheet :
    SlidingModalBottomDialog() {
    private val assetActivityRepository: AssetActivityRepository by scopedInject()
    private val fiatDetailsSheetAdapter = FiatDetailsSheetAdapter()
    private val currency: String by unsafeLazy {
        arguments?.getString(CURRENCY_KEY) ?: throw IllegalStateException("No currency  provided")
    }

    private val txHash: String by unsafeLazy {
        arguments?.getString(TX_HASH_KEY) ?: throw IllegalStateException("No tx  provided")
    }

    override val layoutResource: Int
        get() = R.layout.dialog_sheet_activity_details

    override fun initControls(view: View) {
        with(view) {
            confirmation_progress.gone()
            confirmation_label.gone()
            custodial_tx_button.gone()

            assetActivityRepository.findCachedItem(currency, txHash)?.let {
                title.text =
                    if (it.type == TransactionType.DEPOSIT) getString(R.string.common_deposit) else
                        getString(R.string.fiat_funds_detail_withdraw_title)
                amount.text =
                    if (it.type == TransactionType.DEPOSIT) it.value.toStringWithSymbol() else
                        "- ${it.value.toStringWithSymbol()}"
                status.apply {
                    configureForState(it.state)
                }
                with(details_list) {
                    addItemDecoration(BlockchainListDividerDecor(requireContext()))

                    layoutManager = LinearLayoutManager(
                        requireContext(),
                        LinearLayoutManager.VERTICAL,
                        false
                    )
                    adapter = fiatDetailsSheetAdapter
                }
                fiatDetailsSheetAdapter.items = getItemsForSummaryItem(it)
            }
        }
    }

    private fun TextView.configureForState(state: TransactionState) {
        when (state) {
            TransactionState.COMPLETED -> {
                text = getString(R.string.activity_details_completed)
                setBackgroundResource(R.drawable.bkgd_green_100_rounded)
                setTextColor(ContextCompat.getColor(context, R.color.green_600))
            }
            else -> {
                gone()
            }
        }
    }

    private fun getItemsForSummaryItem(item: FiatActivitySummaryItem): List<FiatDetailItem> =
        listOf(
            FiatDetailItem(getString(R.string.activity_details_buy_tx_id), item.txId),
            FiatDetailItem(getString(R.string.date), Date(item.timeStampMs).toFormattedString()),
            FiatDetailItem(
                if (item.type == TransactionType.DEPOSIT) {
                    getString(R.string.common_to)
                } else {
                    getString(R.string.common_from)
                }, item.account.label),
            FiatDetailItem(getString(R.string.amount), item.value.toStringWithSymbol()))

    companion object {
        private const val CURRENCY_KEY = "CURRENCY_KEY"
        private const val TX_HASH_KEY = "TX_HASH_KEY"

        fun newInstance(
            fiatCurrency: String,
            txHash: String
        ): FiatActivityDetailsBottomSheet {
            return FiatActivityDetailsBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(CURRENCY_KEY, fiatCurrency)
                    putString(TX_HASH_KEY, txHash)
                }
            }
        }
    }
}

data class FiatDetailItem(val key: String, val value: String)