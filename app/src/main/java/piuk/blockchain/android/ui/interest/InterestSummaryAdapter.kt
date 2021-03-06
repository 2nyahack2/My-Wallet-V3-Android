package piuk.blockchain.android.ui.interest

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.item_interest_summary.view.*
import piuk.blockchain.android.R
import kotlin.properties.Delegates

class InterestSummaryAdapter : RecyclerView.Adapter<InterestSummaryAdapter.ViewHolder>() {

    var items: List<InterestSummarySheet.InterestSummaryInfoItem> by Delegates.observable(
        emptyList()) { _, oldValue, newValue ->
        if (oldValue != newValue) {
            notifyDataSetChanged()
        }
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val key: TextView = itemView.title
        val value: TextView = itemView.description
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layout = LayoutInflater.from(parent.context).inflate(
            R.layout.item_interest_summary,
            parent,
            false
        )
        return ViewHolder(layout)
    }

    override fun getItemCount(): Int =
        items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        with(holder) {
            val item = items[position]
            key.text = item.title
            value.text = item.label
        }
    }
}
