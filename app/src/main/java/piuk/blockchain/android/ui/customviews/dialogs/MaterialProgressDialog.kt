package piuk.blockchain.android.ui.customviews.dialogs

import android.content.Context
import android.content.DialogInterface
import android.content.res.TypedArray
import android.graphics.PorterDuff
import android.os.Build
import android.view.View
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import kotlinx.android.synthetic.main.progress_dialog_compat.view.*
import piuk.blockchain.android.R

/**
 * Creates an [AlertDialog] with a custom view for emulating a Material Design progress
 * dialog on pre-Lollipop devices.
 *
 * @param context The Activity Context
 */
class MaterialProgressDialog(context: Context) {

    private val dialog: AlertDialog
    private val layout: View = View.inflate(context, R.layout.progress_dialog_compat, null)

    val isShowing: Boolean
        get() = dialog.isShowing

    init {
        dialog = AlertDialog.Builder(context, R.style.AlertDialogStyle)
            .setTitle(context.getString(R.string.app_name))
            .setView(layout)
            .create()

        val a: TypedArray = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            context.theme.obtainStyledAttributes(intArrayOf(android.R.attr.colorAccent))
        } else {
            context.theme.obtainStyledAttributes(intArrayOf(R.attr.colorAccent))
        }

        layout.progress_bar.indeterminateDrawable.setColorFilter(
            a.getColor(0, 0),
            PorterDuff.Mode.SRC_IN
        )
    }

    fun setMessage(message: String) {
        layout.txt_message.text = message
    }

    fun setMessage(@StringRes message: Int) = layout.txt_message.setText(message)

    fun setTitle(title: String) = dialog.setTitle(title)

    fun setTitle(@StringRes title: Int) = dialog.setTitle(title)

    fun setCancelable(cancelable: Boolean) = dialog.setCancelable(cancelable)

    fun show() = dialog.show()

    fun dismiss() = dialog.dismiss()

    fun setOnCancelListener(listener: DialogInterface.OnCancelListener) =
        dialog.setOnCancelListener(listener)

    fun setOnCancelListener(listener: () -> Unit) =
        dialog.setOnCancelListener { listener.invoke() }
}
