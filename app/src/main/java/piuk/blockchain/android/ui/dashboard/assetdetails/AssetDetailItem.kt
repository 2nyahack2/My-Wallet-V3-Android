package piuk.blockchain.android.ui.dashboard.assetdetails

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import info.blockchain.balance.CryptoCurrency
import info.blockchain.balance.Money
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import kotlinx.android.synthetic.main.dialog_dashboard_asset_label_item.view.*
import kotlinx.android.synthetic.main.view_account_crypto_overview.view.*
import piuk.blockchain.android.R
import piuk.blockchain.android.coincore.AccountGroup
import piuk.blockchain.android.coincore.AssetAction
import piuk.blockchain.android.coincore.AssetFilter
import piuk.blockchain.android.coincore.BlockchainAccount
import piuk.blockchain.android.coincore.CryptoAccount
import piuk.blockchain.android.coincore.CryptoAsset
import piuk.blockchain.android.ui.customviews.account.CellDecorator
import piuk.blockchain.android.ui.customviews.account.addViewToBottomWithConstraints
import piuk.blockchain.android.util.assetName
import piuk.blockchain.android.util.setCoinIcon
import piuk.blockchain.android.util.context
import piuk.blockchain.android.util.gone
import piuk.blockchain.android.util.inflate
import piuk.blockchain.android.util.visible
import kotlin.properties.Delegates

data class AssetDetailItem(
    val assetFilter: AssetFilter,
    val account: BlockchainAccount,
    val balance: Money,
    val fiatBalance: Money,
    val actions: Set<AssetAction>,
    val interestRate: Double
)

class AssetDetailViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    fun bind(
        item: AssetDetailItem,
        onAccountSelected: (BlockchainAccount, AssetFilter) -> Unit,
        disposable: CompositeDisposable,
        block: AssetDetailsDecorator
    ) {
        with(itemView) {
            val asset = getAsset(item.account, item.balance.currencyCode)

            icon.setCoinIcon(asset)
            wallet_name.text = resources.getString(asset.assetName())

            asset_subtitle.text = when (item.assetFilter) {
                AssetFilter.All -> resources.getString(R.string.dashboard_asset_balance_total)
                AssetFilter.NonCustodial -> resources.getString(
                    R.string.dashboard_asset_balance_wallet
                )
                AssetFilter.Custodial -> resources.getString(
                    R.string.dashboard_asset_balance_custodial
                )
                AssetFilter.Interest -> resources.getString(
                    R.string.dashboard_asset_balance_interest, item.interestRate
                )
            }

            rootView.setOnClickListener {
                onAccountSelected(item.account, item.assetFilter)
            }

            when (item.assetFilter) {
                AssetFilter.NonCustodial -> asset_account_icon.gone()
                AssetFilter.Interest -> {
                    asset_account_icon.visible()
                    asset_account_icon.setImageResource(
                        R.drawable.ic_account_badge_interest
                    )
                }
                AssetFilter.Custodial -> {
                    asset_account_icon.visible()
                    asset_account_icon.setImageResource(
                        R.drawable.ic_account_badge_custodial
                    )
                }
                AssetFilter.All -> asset_account_icon.gone()
            }

            wallet_balance_fiat.text = item.balance.toStringWithSymbol()
            wallet_balance_crypto.text = item.fiatBalance.toStringWithSymbol()
            disposable += block(item).view(rootView.context)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    container.addViewToBottomWithConstraints(
                        view = it,
                        bottomOfView = asset_subtitle,
                        startOfView = asset_subtitle,
                        endOfView = wallet_balance_crypto
                    )
                }
        }
    }

    private fun getAsset(account: BlockchainAccount, currency: String): CryptoCurrency =
        when (account) {
            is CryptoAccount -> account.asset
            is AccountGroup -> account.accounts.filterIsInstance<CryptoAccount>()
                .firstOrNull()?.asset ?: throw IllegalStateException(
                "No crypto accounts found in ${this::class.java} with currency $currency "
            )
            else -> null
        } ?: throw IllegalStateException("Unsupported account type ${this::class.java}")
}

class LabelViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    fun bind(token: CryptoAsset) {
        itemView.asset_label_description.text = when (token.asset) {
            CryptoCurrency.ALGO -> context.getString(R.string.algorand_asset_label)
            else -> ""
        }
    }
}

internal class AssetDetailAdapter(
    private val onAccountSelected: (BlockchainAccount, AssetFilter) -> Unit,
    private val showBanner: Boolean,
    private val token: CryptoAsset,
    private val assetDetailsDecorator: AssetDetailsDecorator
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val compositeDisposable = CompositeDisposable()

    var itemList: List<AssetDetailItem> by Delegates.observable(emptyList()) { _, oldValue, newValue ->
        if (oldValue != newValue) {
            notifyDataSetChanged()
        }
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        compositeDisposable.clear()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
        if (viewType == TYPE_CRYPTO) {
            AssetDetailViewHolder(parent.inflate(R.layout.view_account_crypto_overview))
        } else {
            LabelViewHolder(parent.inflate(R.layout.dialog_dashboard_asset_label_item))
        }

    override fun getItemCount(): Int = if (showBanner) itemList.size + 1 else itemList.size

    override fun getItemViewType(position: Int): Int =
        if (showBanner) {
            if (position >= itemList.size) {
                TYPE_LABEL
            } else {
                TYPE_CRYPTO
            }
        } else {
            TYPE_CRYPTO
        }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is AssetDetailViewHolder) {
            holder.bind(itemList[position], onAccountSelected, compositeDisposable, assetDetailsDecorator)
        } else {
            (holder as LabelViewHolder).bind(token)
        }
    }

    companion object {
        private const val TYPE_CRYPTO = 0
        private const val TYPE_LABEL = 1
    }
}

typealias AssetDetailsDecorator = (AssetDetailItem) -> CellDecorator