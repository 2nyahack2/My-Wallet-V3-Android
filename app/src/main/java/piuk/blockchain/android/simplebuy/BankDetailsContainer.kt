package piuk.blockchain.android.simplebuy

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import piuk.blockchain.android.ui.customviews.CopyableTextFormItem

class BankDetailsContainer @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    fun initWithBankDetailsAndAmount(
        bankDetails: List<BankDetailField>,
        copyFieldListener: CopyFieldListener? = null
    ) {
        if (childCount == 0) {
            bankDetails.forEach {
                addView(
                    CopyableTextFormItem(
                        context = context,
                        title = it.title,
                        value = it.value,
                        isCopyable = it.isCopyable,
                        onCopy = { field -> copyFieldListener?.onFieldCopied(field) }
                    ),
                    LayoutParams(
                        LayoutParams.MATCH_PARENT,
                        LayoutParams.WRAP_CONTENT
                    )
                )
            }
        }
    }
}

data class BankDetailField(val title: String, val value: String, val isCopyable: Boolean)

interface CopyFieldListener {
    fun onFieldCopied(field: String)
}