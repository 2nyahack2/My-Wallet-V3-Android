package piuk.blockchain.android.ui.addresses

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.text.InputFilter
import android.text.InputType
import android.view.View
import android.widget.ImageView
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatEditText
import piuk.blockchain.android.R
import piuk.blockchain.android.util.ViewUtils
import piuk.blockchain.android.util.getTextString
import piuk.blockchain.androidcoreui.ui.customviews.ToastCustom
import piuk.blockchain.androidcoreui.ui.customviews.toast

private const val ADDRESS_LABEL_MAX_LENGTH = 17

internal fun promptForAccountLabel(
    ctx: Context,
    @StringRes title: Int,
    @StringRes msg: Int,
    initialText: String = "",
    @StringRes okBtnText: Int = R.string.save_name,
    @StringRes cancelText: Int = android.R.string.cancel,
    okAction: (String) -> Unit
) {
    val editCtrl = AppCompatEditText(ctx).apply {
        inputType = InputType.TYPE_TEXT_FLAG_CAP_WORDS
        filters = arrayOf<InputFilter>(InputFilter.LengthFilter(ADDRESS_LABEL_MAX_LENGTH))
        setHint(R.string.name)
        contentDescription = resources.getString(R.string.content_desc_edit_account_label)

        if (initialText.length <= ADDRESS_LABEL_MAX_LENGTH) {
            setText(initialText)
            setSelection(initialText.length)
        }
    }

    AlertDialog.Builder(ctx, R.style.AlertDialogStyle)
        .setTitle(title)
        .setMessage(msg)
        .setView(ViewUtils.getAlertDialogPaddedView(ctx, editCtrl))
        .setCancelable(false)
        .setPositiveButton(okBtnText) { _, _ ->
            val label = editCtrl.getTextString().trim { it <= ' ' }
            if (label.isNotEmpty()) {
                okAction(label)
            } else {
                ctx.toast(R.string.label_cant_be_empty, ToastCustom.TYPE_ERROR)
            }
        }.setNegativeButton(cancelText, null)
        .show()
}

internal fun promptArchive(
    ctx: Context,
    @StringRes title: Int,
    @StringRes msg: Int,
    action: () -> Unit
) {
    AlertDialog.Builder(ctx, R.style.AlertDialogStyle)
        .setTitle(title)
        .setMessage(msg)
        .setCancelable(false)
        .setPositiveButton(R.string.common_yes) { _, _ -> action() }
        .setNegativeButton(R.string.common_no, null)
        .show()
}

fun promptXpubShareWarning(
    ctx: Context,
    action: () -> Unit
) {
    AlertDialog.Builder(ctx, R.style.AlertDialogStyle)
        .setTitle(R.string.warning)
        .setMessage(R.string.xpub_sharing_warning)
        .setCancelable(false)
        .setPositiveButton(R.string.dialog_continue) { _, _ -> action() }
        .setNegativeButton(android.R.string.cancel, null)
        .show()
}

fun showAddressQrCode(
    ctx: Context,
    @StringRes heading: Int,
    @StringRes note: Int,
    @StringRes copyBtn: Int,
    bitmap: Bitmap,
    qrString: String
) = showAddressQrCode(
        ctx,
        heading,
        ctx.getString(note),
        copyBtn,
        bitmap,
        qrString
)

fun showAddressQrCode(
    ctx: Context,
    @StringRes heading: Int,
    note: String,
    @StringRes copyBtn: Int,
    bitmap: Bitmap,
    qrString: String
) {
    val view = View.inflate(ctx, R.layout.dialog_view_qr, null)
    val imageView = view.findViewById<View>(R.id.imageview_qr) as ImageView
    imageView.setImageBitmap(bitmap)

    AlertDialog.Builder(ctx, R.style.AlertDialogStyle)
        .setTitle(heading)
        .setMessage(note)
        .setView(view)
        .setNegativeButton(android.R.string.cancel, null)
        .setPositiveButton(copyBtn) { _, _ ->
            val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip: ClipData = ClipData.newPlainText("Send address", qrString)
            ctx.toast(R.string.copied_to_clipboard)
            clipboard.setPrimaryClip(clip)
        }
        .create()
        .show()
}

fun promptImportKeyPassword(
    ctx: Context,
    okAction: (String) -> Unit
) {
    val editCtrl = AppCompatEditText(ctx).apply {
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
    }

    AlertDialog.Builder(ctx, R.style.AlertDialogStyle)
        .setTitle(R.string.app_name)
        .setMessage(R.string.bip38_password_entry)
        .setView(ViewUtils.getAlertDialogPaddedView(ctx, editCtrl))
        .setCancelable(false)
        .setPositiveButton(android.R.string.ok) { _, _ ->
            val password = editCtrl.getTextString().trim { it <= ' ' }
            okAction(password)
        }
        .setNegativeButton(android.R.string.cancel, null)
        .show()
}

fun promptTransferFunds(
    ctx: Context,
    okAction: () -> Unit
) {
    AlertDialog.Builder(ctx, R.style.AlertDialogStyle)
        .setTitle(R.string.transfer_funds_title)
        .setMessage(R.string.transfer_funds_description)
        .setPositiveButton(R.string.transfer_all) { _, _ -> okAction() }
        .setNegativeButton(R.string.not_now, null)
        .show()
}
