package piuk.blockchain.android.ui.customviews

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.view.View
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import piuk.blockchain.android.R

class BlockchainListDividerDecor(context: Context) : RecyclerView.ItemDecoration() {

    private val dividerDrawable: Drawable = ContextCompat.getDrawable(context, R.drawable.divider_item)!!

    override fun getItemOffsets(rect: Rect, v: View, parent: RecyclerView, s: RecyclerView.State) {
        parent.adapter?.let { adapter ->
            val childAdapterPosition = parent.getChildAdapterPosition(v)
                .let { if (it == RecyclerView.NO_POSITION) return else it }
            rect.bottom = // Add space/"padding" on bottom side
                if (childAdapterPosition == adapter.itemCount - 1) {
                    0 // No "padding"
                } else {
                    dividerDrawable.intrinsicHeight // Drawable height "padding"
                }
        }
    }

    override fun onDraw(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        parent.adapter?.let { adapter ->
            val children = parent.childCount
            for (i in 0..children) {
                val view = parent.getChildAt(i)
                val childAdapterPosition = parent.getChildAdapterPosition(view)
                    .let { if (it == RecyclerView.NO_POSITION) return else it }
                if (childAdapterPosition != adapter.itemCount - 1) {
                    val left = view.left
                    val top = view.bottom
                    val right = view.right
                    val bottom = view.bottom + dividerDrawable.intrinsicHeight
                    dividerDrawable.bounds = Rect(left, top, right, bottom)
                    dividerDrawable.draw(canvas)
                }
            }
        }
    }
}