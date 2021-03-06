package piuk.blockchain.android.cards

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.picker_item.view.*
import piuk.blockchain.android.R
import piuk.blockchain.android.util.visible
import piuk.blockchain.android.util.gone

class PickerItemsAdapter(private val block: (PickerItem) -> Unit) :
    RecyclerView.Adapter<PickerItemsAdapter.ViewHolder>() {

    var items: List<PickerItem> = emptyList()
        set(items) {
            field = items
            notifyDataSetChanged()
        }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.picker_item, parent, false)
        return ViewHolder(v)
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int
    ) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int {
        return items.size
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind(pickerItem: PickerItem) {
            itemView.item_title.text = pickerItem.label
            pickerItem.icon?.let {
                itemView.item_icon.text = it
                itemView.item_icon.visible()
            } ?: itemView.item_icon.gone()

            itemView.root_view.setOnClickListener {
                block(pickerItem)
            }
        }
    }
}