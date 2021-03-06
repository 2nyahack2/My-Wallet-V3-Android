package piuk.blockchain.android.ui.activity.detail.adapter

import android.text.InputFilter
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.extensions.LayoutContainer
import kotlinx.android.synthetic.main.item_activity_detail_description.view.*
import piuk.blockchain.android.R
import piuk.blockchain.android.ui.activity.detail.ActivityDetailsType
import piuk.blockchain.android.ui.activity.detail.Description
import piuk.blockchain.android.ui.adapters.AdapterDelegate
import piuk.blockchain.android.util.inflate

const val MAX_NOTE_LENGTH = 255

const val INPUT_FIELD_FLAGS: Int = (
    InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
        InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE or
        InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE
    )

class ActivityDetailDescriptionItemDelegate<in T>(
    private val onDescriptionItemUpdated: (String) -> Unit
) : AdapterDelegate<T> {

    override fun isForViewType(items: List<T>, position: Int): Boolean {
        val item = items[position] as ActivityDetailsType
        return item is Description
    }

    override fun onCreateViewHolder(parent: ViewGroup): RecyclerView.ViewHolder =
        DescriptionItemViewHolder(
            parent.inflate(R.layout.item_activity_detail_description)
        )

    override fun onBindViewHolder(
        items: List<T>,
        position: Int,
        holder: RecyclerView.ViewHolder
    ) = (holder as DescriptionItemViewHolder).bind(
        items[position] as Description,
        onDescriptionItemUpdated
    )
}

private class DescriptionItemViewHolder(var parent: View) : RecyclerView.ViewHolder(parent),
    LayoutContainer {
    override val containerView: View?
        get() = itemView

    fun bind(item: Description, onDescriptionUpdated: (String) -> Unit) {
        itemView.item_activity_detail_description.apply {
            item.description?.let {
                setText(it, TextView.BufferType.EDITABLE)
                setSelection(item.description.length)
            }
            inputType =
                INPUT_FIELD_FLAGS
            filters = arrayOf(InputFilter.LengthFilter(MAX_NOTE_LENGTH))

            setOnEditorActionListener { v, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE && v.text.isNotEmpty()) {
                    onDescriptionUpdated(v.text.toString())
                    clearFocus()
                }

                false
            }
        }
    }
}