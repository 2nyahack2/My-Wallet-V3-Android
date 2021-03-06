package piuk.blockchain.android.ui.activity.detail.adapter

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.extensions.LayoutContainer
import kotlinx.android.synthetic.main.item_activity_detail_action.view.*
import piuk.blockchain.android.R
import piuk.blockchain.android.ui.activity.detail.Action
import piuk.blockchain.android.ui.activity.detail.ActivityDetailsType
import piuk.blockchain.android.ui.adapters.AdapterDelegate
import piuk.blockchain.android.util.inflate

class ActivityDetailActionItemDelegate<in T>(
    private val onActionItemClicked: () -> Unit
) : AdapterDelegate<T> {

    override fun isForViewType(items: List<T>, position: Int): Boolean {
        val item = items[position] as ActivityDetailsType
        return item is Action
    }

    override fun onCreateViewHolder(parent: ViewGroup): RecyclerView.ViewHolder =
        ActionItemViewHolder(parent.inflate(R.layout.item_activity_detail_action))

    override fun onBindViewHolder(
        items: List<T>,
        position: Int,
        holder: RecyclerView.ViewHolder
    ) = (holder as ActionItemViewHolder).bind(
        onActionItemClicked
    )
}

private class ActionItemViewHolder(var parent: View) : RecyclerView.ViewHolder(parent),
    LayoutContainer {
    override val containerView: View?
        get() = itemView

    fun bind(actionItemClicked: () -> Unit) {
        itemView.activity_details_action.setOnClickListener { actionItemClicked() }
    }
}