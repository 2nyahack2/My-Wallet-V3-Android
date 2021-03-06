package piuk.blockchain.android.ui.kyc.hyperlinks

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.View
import android.widget.TextView
import androidx.annotation.StringRes
import com.blockchain.ui.urllinks.URL_PRIVACY_POLICY
import com.blockchain.ui.urllinks.URL_TOS_POLICY
import piuk.blockchain.android.R

fun TextView.renderTermsLinks(
    @StringRes startText: Int,
    tos: String = URL_TOS_POLICY,
    privacyPolicyUrl: String = URL_PRIVACY_POLICY
) {
    val disclaimerStart = context.getString(startText) + "\n"
    val terms = context.getString(R.string.kyc_splash_terms_and_conditions_terms)
    val ampersand = "&"
    val privacy = context.getString(R.string.kyc_splash_terms_and_conditions_privacy)
    val termsClickSpan = context.goToUrlClickableSpan(tos)
    val privacyClickSpan = context.goToUrlClickableSpan(privacyPolicyUrl)

    formatLinks(
        disclaimerStart to defaultClickSpan,
        terms to termsClickSpan,
        ampersand to defaultClickSpan,
        privacy to privacyClickSpan
    )
}

fun TextView.renderSingleLink(@StringRes startText: Int, @StringRes link: Int, @StringRes url: Int) {
    val prefixText = context.getString(startText)
    val linkText = context.getString(link)
    val linkUrl = context.goToUrlClickableSpan(context.getString(url))

    formatLinks(
        prefixText to defaultClickSpan,
        linkText to linkUrl
    )
}

private val defaultClickSpan =
    object : ClickableSpan() {
        override fun onClick(view: View) = Unit
    }

private fun TextView.formatLinks(vararg linkPairs: Pair<String, ClickableSpan>) {
    val finalString = linkPairs.joinToString(separator = " ") { it.first }
    val spannableString = SpannableString(finalString)

    linkPairs.forEach { (link, span) ->
        val startIndexOfLink = finalString.indexOf(link)
        spannableString.setSpan(
            span,
            startIndexOfLink,
            startIndexOfLink + link.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
    }

    highlightColor = Color.TRANSPARENT
    movementMethod = LinkMovementMethod.getInstance()
    setText(spannableString, TextView.BufferType.SPANNABLE)
}

private fun Context.goToUrlClickableSpan(url: String) =
    object : ClickableSpan() {
        override fun onClick(widget: View) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }
