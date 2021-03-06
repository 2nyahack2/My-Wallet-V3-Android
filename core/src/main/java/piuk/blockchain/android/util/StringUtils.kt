package piuk.blockchain.android.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannedString
import android.text.TextPaint
import android.text.style.ClickableSpan
import android.text.style.StyleSpan
import android.view.View
import androidx.annotation.StringRes

class StringUtils(private val context: Context) {

    @Deprecated("Don't be get getting strings in non-UI code")
    fun getString(@StringRes stringId: Int): String {
        return context.getString(stringId)
    }

    fun getStringWithMappedAnnotations(
        @StringRes stringId: Int,
        linksMap: Map<String, Uri?>,
        launchActivity: Activity,
        onClick: () -> Unit = {}
    ): CharSequence {

        val text = context.getText(stringId)
        val rawText = text as? SpannedString ?: return text
        val out = SpannableString(rawText)

        for (annotation in rawText.getSpans(0, rawText.length, android.text.Annotation::class.java)) {
            if (annotation.key == "link") {
                out.setSpan(
                    ClickableSpanWithoutUnderline {
                        linksMap[annotation.value]?.let {
                            launchActivity.startActivity(Intent(Intent.ACTION_VIEW, it))
                        }
                        onClick()
                    },
                    rawText.getSpanStart(annotation),
                    rawText.getSpanEnd(annotation),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            if (annotation.key == "font") {
                val fontName = annotation.value
                if (fontName == "bold") {
                    out.setSpan(StyleSpan(Typeface.BOLD),
                        rawText.getSpanStart(annotation),
                        rawText.getSpanEnd(annotation),
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
        }
        return out
    }
}

private class ClickableSpanWithoutUnderline(val onClick: () -> Unit) : ClickableSpan() {
    override fun onClick(widget: View) {
        onClick.invoke()
    }

    override fun updateDrawState(ds: TextPaint) {
        super.updateDrawState(ds)
        ds.isUnderlineText = false
    }
}
