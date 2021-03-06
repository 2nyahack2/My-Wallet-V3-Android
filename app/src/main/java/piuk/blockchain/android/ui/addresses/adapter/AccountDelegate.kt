package piuk.blockchain.android.ui.addresses.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import io.reactivex.Maybe
import io.reactivex.Single
import kotlinx.android.synthetic.main.item_accounts_row.view.*
import piuk.blockchain.android.R
import piuk.blockchain.android.coincore.CryptoAccount
import piuk.blockchain.android.ui.adapters.AdapterDelegate
import piuk.blockchain.android.ui.customviews.account.CellDecorator
import piuk.blockchain.android.util.gone
import piuk.blockchain.android.util.inflate
import piuk.blockchain.android.util.visible

class AccountDelegate(
    val listener: AccountAdapter.Listener
) : AdapterDelegate<AccountListItem> {

    override fun onCreateViewHolder(parent: ViewGroup): RecyclerView.ViewHolder =
        AccountViewHolder(parent.inflate(R.layout.item_accounts_row))

    override fun onBindViewHolder(
        items: List<AccountListItem>,
        position: Int,
        holder: RecyclerView.ViewHolder
    ) {
        val accountViewHolder = holder as AccountViewHolder
        accountViewHolder.bind(items[position] as AccountListItem.Account, listener)
    }

    override fun isForViewType(items: List<AccountListItem>, position: Int) =
        (items[position] is AccountListItem.Account)

    private class AccountViewHolder internal constructor(
        itemView: View
    ) : RecyclerView.ViewHolder(itemView) {

        internal fun bind(
            item: AccountListItem.Account,
            listener: AccountAdapter.Listener
        ) {
            if (item.account.isArchived) {
                // Show the archived item
                itemView.account_details.gone()
                itemView.account_details_archived.visible()
                itemView.account_details_archived.updateAccount(
                    item.account
                ) { listener.onAccountClicked(it) }
                itemView.account_details.contentDescription = ""
                itemView.account_details_archived.contentDescription = item.account.label
            } else {
                // Show the normal item
                itemView.account_details_archived.gone()
                itemView.account_details.visible()
                itemView.account_details.updateAccount(
                    item.account,
                    { listener.onAccountClicked(it) },
                    DefaultAccountCellDecorator(item.account)
                )
                itemView.account_details_archived.contentDescription = ""
                itemView.account_details.contentDescription = item.account.label
            }
        }
    }
}

class DefaultAccountCellDecorator(private val account: CryptoAccount) : CellDecorator {
    override fun view(context: Context): Maybe<View> =
        if (account.isDefault) {
            defaultLabel(context)
        } else {
            Maybe.empty()
        }

    private fun defaultLabel(context: Context): Maybe<View> =
        Maybe.just(
            LayoutInflater.from(context)
                .inflate(R.layout.decorator_account_default, null, false)
            )

    override fun isEnabled(): Single<Boolean> = Single.just(true)
}