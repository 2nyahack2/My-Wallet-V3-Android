package piuk.blockchain.androidcoreui.ui.customviews

import android.content.Context
import android.view.LayoutInflater
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StringDef
import androidx.annotation.StringRes
import androidx.annotation.UiThread
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import piuk.blockchain.androidcoreui.R
import piuk.blockchain.androidcoreui.utils.extensions.getResolvedColor

object ToastCustom {
    const val TYPE_ERROR = "TYPE_ERROR"
    const val TYPE_GENERAL = "TYPE_GENERAL"
    const val TYPE_OK = "TYPE_OK"
    const val LENGTH_SHORT = 0
    const val LENGTH_LONG = 1

    @UiThread
    fun makeText(
        context: Context?,
        msg: CharSequence?,
        duration: Int,
        @ToastType type: String?
    ) {
        if (context == null) {
            return
        }

        val v = LayoutInflater.from(context).inflate(R.layout.transient_notification, null)
        v.findViewById<TextView>(R.id.message)?.apply {
            text = msg
            when (type) {
                TYPE_ERROR -> {
                    background = ContextCompat.getDrawable(context, R.drawable.rounded_view_toast_error)
                    setTextColor(context.getResolvedColor(R.color.toast_error_text))
                }
                TYPE_GENERAL -> {
                    background = ContextCompat.getDrawable(context, R.drawable.rounded_view_toast_warning)
                    setTextColor(context.getResolvedColor(R.color.toast_warning_text))
                }
                TYPE_OK -> {
                    background = ContextCompat.getDrawable(context, R.drawable.rounded_view_toast_info)
                    setTextColor(context.getResolvedColor(R.color.toast_info_text))
                }
            }
        }

        Toast.makeText(context, msg, duration).apply {
            view = v
            show()
        }
    }

    @kotlin.annotation.Retention(AnnotationRetention.SOURCE)
    @StringDef(TYPE_ERROR, TYPE_GENERAL, TYPE_OK)
    annotation class ToastType
}

/**
 * Shows a [ToastCustom] from a given [Activity]. By default, the Toast is of type
 * [ToastCustom.TYPE_GENERAL] but can be overloaded if needed.
 *
 * @param text The text to display, as a [String]
 * @param type An optional [ToastCustom.ToastType] which can be omitted for general Toasts
 */
fun AppCompatActivity.toast(text: String, @ToastCustom.ToastType type: String = ToastCustom.TYPE_GENERAL) {
    ToastCustom.makeText(this, text, ToastCustom.LENGTH_SHORT, type)
}

/**
 * Shows a [ToastCustom] from a given [Activity]. By default, the Toast is of type
 * [ToastCustom.TYPE_GENERAL] but can be overloaded if needed.
 *
 * @param text The text to display, as a String resource [Int]
 * @param type An optional [ToastCustom.ToastType] which can be omitted for general Toasts
 */
fun AppCompatActivity.toast(@StringRes text: Int, @ToastCustom.ToastType type: String = ToastCustom.TYPE_GENERAL) {
    ToastCustom.makeText(this, getString(text), ToastCustom.LENGTH_SHORT, type)
}

/**
 * Shows a [ToastCustom] from a given [Fragment]. By default, the Toast is of type
 * [ToastCustom.TYPE_GENERAL] but can be overloaded if needed.
 *
 * @param text The text to display, as a [String]
 * @param type An optional [ToastCustom.ToastType] which can be omitted for general Toasts
 */
fun Fragment.toast(text: String, @ToastCustom.ToastType type: String = ToastCustom.TYPE_GENERAL) {
    ToastCustom.makeText(activity, text, ToastCustom.LENGTH_SHORT, type)
}

/**
 * Shows a [ToastCustom] from a given [Fragment]. By default, the Toast is of type
 * [ToastCustom.TYPE_GENERAL] but can be overloaded if needed.
 *
 * @param text The text to display, as a String resource [Int]
 * @param type An optional [ToastCustom.ToastType] which can be omitted for general Toasts
 */
fun Fragment.toast(@StringRes text: Int, @ToastCustom.ToastType type: String = ToastCustom.TYPE_GENERAL) {
    ToastCustom.makeText(activity, getString(text), ToastCustom.LENGTH_SHORT, type)
}

/**
 * Shows a [ToastCustom] from a given [Context]. By default, the Toast is of type
 * [ToastCustom.TYPE_GENERAL] but can be overloaded if needed. Be careful not to abuse this an
 * call [toast] from an Application Context.
 *
 * @param text The text to display, as a [String]
 * @param type An optional [ToastCustom.ToastType] which can be omitted for general Toasts
 */

fun Context.toast(text: String, @ToastCustom.ToastType type: String = ToastCustom.TYPE_GENERAL) {
    ToastCustom.makeText(this, text, ToastCustom.LENGTH_SHORT, type)
}

/**
 * Shows a [ToastCustom] from a given [Context]. By default, the Toast is of type
 * [ToastCustom.TYPE_GENERAL] but can be overloaded if needed. Be careful not to abuse this an
 * call [toast] from an Application Context.
 *
 * @param text The text to display, as a String resource [Int]
 * @param type An optional [ToastCustom.ToastType] which can be omitted for general Toasts
 */
fun Context.toast(@StringRes text: Int, @ToastCustom.ToastType type: String = ToastCustom.TYPE_GENERAL) {
    ToastCustom.makeText(this, getString(text), ToastCustom.LENGTH_SHORT, type)
}
