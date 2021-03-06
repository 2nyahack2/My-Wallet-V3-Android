package piuk.blockchain.android.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.View
import android.widget.Toast
import piuk.blockchain.android.BuildConfig

fun View.copyHashOnLongClick(context: Context) {
    if (BuildConfig.COMMIT_HASH.isNotEmpty()) {
        setOnLongClickListener {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("commit_hash", BuildConfig.COMMIT_HASH))
            Toast.makeText(context, "Commit hash copied", Toast.LENGTH_SHORT).show()
            true
        }
    }
}