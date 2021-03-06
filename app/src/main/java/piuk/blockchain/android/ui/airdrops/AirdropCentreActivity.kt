package piuk.blockchain.android.ui.airdrops

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.blockchain.koin.scopedInject
import kotlinx.android.synthetic.main.activity_airdrops.*
import kotlinx.android.synthetic.main.item_airdrop_header.view.*
import kotlinx.android.synthetic.main.item_airdrop_status.view.*
import kotlinx.android.synthetic.main.toolbar_general.*
import piuk.blockchain.android.R
import piuk.blockchain.android.ui.base.MvpActivity
import piuk.blockchain.android.ui.base.SlidingModalBottomDialog
import piuk.blockchain.android.util.setCoinIcon
import piuk.blockchain.android.util.inflate
import piuk.blockchain.android.util.setOnClickListenerDebounced
import java.text.DateFormat
import kotlin.math.max
import piuk.blockchain.android.ui.airdrops.AirdropStatusSheet as AirdropStatusSheet

class AirdropCentreActivity : MvpActivity<AirdropCentreView, AirdropCentrePresenter>(),
    AirdropCentreView,
    SlidingModalBottomDialog.Host {

    override val presenter: AirdropCentrePresenter by scopedInject()
    override val view: AirdropCentreView = this

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_airdrops)
        setupToolbar(toolbar_general, R.string.airdrop_activity_title)

        toolbar_general.setNavigationOnClickListener { finish() }

        airdrop_list.layoutManager = LinearLayoutManager(this)
    }

    override fun renderList(statusList: List<Airdrop>) {
        val itemList: MutableList<ListItem> = statusList.sortedBy { !it.isActive }
            .map { ListItem.AirdropItem(it) }
            .toMutableList()

        val i = max(statusList.indexOfFirst { !it.isActive }, 0)
        itemList.add(i, ListItem.HeaderItem("Ended"))
        itemList.add(0, ListItem.HeaderItem("Active"))

        airdrop_list.adapter = Adapter(itemList) { airdropName -> onItemClicked(airdropName) }
    }

    private fun onItemClicked(airdropName: String) {
        showBottomSheet(AirdropStatusSheet.newInstance(airdropName))
    }

    override fun renderListUnavailable() {
        finish()
    }

    companion object {
        fun start(ctx: Context) {
            Intent(ctx, AirdropCentreActivity::class.java).run { ctx.startActivity(this) }
        }
    }

    override fun onSheetClosed() {
        /* no-op */
    }
}

sealed class ListItem {
    data class AirdropItem(val airdrop: Airdrop) : ListItem()
    data class HeaderItem(val heading: String) : ListItem()
}

abstract class AirdropViewHolder<out T : ListItem>(itemView: View) : RecyclerView.ViewHolder(itemView)

class HeadingViewHolder(itemView: View) : AirdropViewHolder<ListItem.HeaderItem>(itemView) {

    fun bind(item: ListItem.HeaderItem) {
        itemView.heading.text = item.heading
    }
}

class StatusViewHolder(itemView: View) : AirdropViewHolder<ListItem.AirdropItem>(itemView) {

    fun bind(item: ListItem.AirdropItem, onClick: (String) -> Unit) {
        with(itemView) {
            icon.setCoinIcon(item.airdrop.currency)
            currency.text = item.airdrop.currency.displayTicker
            val formatted = DateFormat.getDateInstance(DateFormat.SHORT).format(item.airdrop.date)
            setOnClickListenerDebounced { onClick(item.airdrop.name) }
            date.text = resources.getString(
                if (item.airdrop.isActive) {
                    R.string.airdrop_status_date_active
                } else {
                    R.string.airdrop_status_date_inactive
                },
                formatted
            )
        }
    }
}

private class Adapter(
    private val itemList: List<ListItem>,
    private val onClick: (String) -> Unit
) : RecyclerView.Adapter<AirdropViewHolder<ListItem>>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AirdropViewHolder<ListItem> =
        when (viewType) {
            1 -> HeadingViewHolder(parent.inflate(R.layout.item_airdrop_header))
            2 -> StatusViewHolder(parent.inflate(R.layout.item_airdrop_status))
            else -> throw IllegalArgumentException("View type out of range")
        }

    override fun getItemCount(): Int = itemList.size

    override fun getItemViewType(position: Int): Int =
        when (itemList[position]) {
            is ListItem.HeaderItem -> 1
            is ListItem.AirdropItem -> 2
        }

    override fun onBindViewHolder(holder: AirdropViewHolder<ListItem>, position: Int) {
        when (val o = itemList[position]) {
            is ListItem.HeaderItem -> (holder as HeadingViewHolder).bind(o)
            is ListItem.AirdropItem -> (holder as StatusViewHolder).bind(o, onClick)
        }
    }
}
