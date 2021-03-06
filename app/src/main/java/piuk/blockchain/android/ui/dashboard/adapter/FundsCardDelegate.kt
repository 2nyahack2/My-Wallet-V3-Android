package piuk.blockchain.android.ui.dashboard.adapter

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.item_dashboard_funds.view.*
import kotlinx.android.synthetic.main.item_dashboard_funds_bordered.view.*
import kotlinx.android.synthetic.main.item_dashboard_funds_parent.view.*
import piuk.blockchain.android.R
import piuk.blockchain.android.coincore.FiatAccount
import piuk.blockchain.android.ui.adapters.AdapterDelegate
import piuk.blockchain.android.ui.dashboard.FiatAssetState
import piuk.blockchain.android.ui.dashboard.FiatBalanceInfo
import piuk.blockchain.android.util.gone
import piuk.blockchain.android.util.inflate
import piuk.blockchain.android.util.visibleIf

class FundsCardDelegate<in T>(
    private val selectedFiat: String,
    private val onFundsItemClicked: (FiatAccount) -> Unit
) : AdapterDelegate<T> {

    override fun isForViewType(items: List<T>, position: Int): Boolean =
        items[position] is FiatAssetState

    override fun onCreateViewHolder(parent: ViewGroup): RecyclerView.ViewHolder =
        FundsCardViewHolder(
            parent.inflate(R.layout.item_dashboard_funds_parent),
            onFundsItemClicked,
            selectedFiat
        )

    override fun onBindViewHolder(
        items: List<T>,
        position: Int,
        holder: RecyclerView.ViewHolder
    ) = (holder as FundsCardViewHolder).bind(items[position] as FiatAssetState)
}

private class FundsCardViewHolder(
    itemView: View,
    private val onFundsItemClicked: (FiatAccount) -> Unit,
    private val selectedFiat: String
) : RecyclerView.ViewHolder(itemView) {
    private val multipleFundsAdapter: MultipleFundsAdapter by lazy {
        MultipleFundsAdapter(onFundsItemClicked, selectedFiat)
    }

    fun bind(funds: FiatAssetState) {
        if (funds.fiatAccounts.size == 1) {
            showSingleAsset(funds.fiatAccounts[0])
        } else {
            itemView.funds_single_item.gone()
            itemView.funds_list.apply {
                layoutManager =
                    LinearLayoutManager(itemView.context, LinearLayoutManager.HORIZONTAL, false)
                adapter = multipleFundsAdapter
            }
            multipleFundsAdapter.items = funds.fiatAccounts
        }
    }

    private fun showSingleAsset(assetInfo: FiatBalanceInfo) {
        val ticker = assetInfo.account.fiatCurrency
        itemView.apply {
            funds_user_fiat_balance.visibleIf { selectedFiat != ticker }
            funds_user_fiat_balance.text = assetInfo.balance.toStringWithSymbol()
            funds_list.gone()
            funds_single_item.setOnClickListener {
                onFundsItemClicked(assetInfo.account)
            }
            funds_title.setStringFromTicker(context, ticker)
            funds_fiat_ticker.text = ticker
            funds_balance.text = if (selectedFiat == ticker) {
                assetInfo.balance.toStringWithSymbol()
            } else {
                assetInfo.userFiat.toStringWithSymbol()
            }
            funds_icon.setIcon(ticker)
        }
    }
}

private class MultipleFundsAdapter(
    private val onFundsItemClicked: (FiatAccount) -> Unit,
    private val selectedFiat: String
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    var items = listOf<FiatBalanceInfo>()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
        SingleFundsViewHolder(parent.inflate(R.layout.item_dashboard_funds_bordered),
            onFundsItemClicked, selectedFiat)

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int
    ) = (holder as SingleFundsViewHolder).bind(items[position])

    private class SingleFundsViewHolder(
        itemView: View,
        private val onFundsItemClicked: (FiatAccount) -> Unit,
        private val selectedFiat: String
    ) : RecyclerView.ViewHolder(itemView) {

        fun bind(assetInfo: FiatBalanceInfo) {
            val ticker = assetInfo.account.fiatCurrency
            itemView.apply {
                bordered_funds_balance_other_fiat.visibleIf { selectedFiat != ticker }
                bordered_funds_balance_other_fiat.text = assetInfo.balance.toStringWithSymbol()

                bordered_funds_parent.setOnClickListener {
                    onFundsItemClicked(assetInfo.account)
                }
                bordered_funds_title.setStringFromTicker(context, ticker)
                bordered_funds_fiat_ticker.text = ticker
                bordered_funds_balance.text = if (selectedFiat == ticker) {
                    assetInfo.balance.toStringWithSymbol()
                } else {
                    assetInfo.userFiat.toStringWithSymbol()
                }
                bordered_funds_icon.setIcon(ticker)
            }
        }
    }
}

private fun TextView.setStringFromTicker(context: Context, ticker: String) {
    text = context.getString(
        when (ticker) {
            "EUR" -> R.string.euros
            "GBP" -> R.string.pounds
            "USD" -> R.string.us_dollars
            else -> R.string.empty
        }
    )
}
