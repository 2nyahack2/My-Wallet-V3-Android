package piuk.blockchain.android.ui.customviews

import android.content.ClipboardManager
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.constraintlayout.widget.ConstraintLayout
import kotlinx.android.synthetic.main.copyable_text_form_item.view.*
import piuk.blockchain.android.R
import piuk.blockchain.android.util.visibleIf

class CopyableTextFormItem @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    private val title: String = "",
    private val value: String = "",
    private val isCopyable: Boolean = false,
    private val onCopy: (String) -> Unit = {}
) : ConstraintLayout(context, attrs, defStyleAttr) {

    init {
        initView()
    }

    private fun initView() {
        val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        inflater.inflate(R.layout.copyable_text_form_item, this, true).also {
            it.title.text = title
            it.value.text = value
            it.ic_copy.visibleIf { isCopyable }
            if (isCopyable) {
                it.copy_tap_target.setOnClickListener {
                    val clipboard =
                        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = android.content.ClipData.newPlainText("Copied Text", value)
                    clipboard.setPrimaryClip(clip)
                    onCopy(title)
                }
            }
        }
    }
}