package piuk.blockchain.android.ui.customviews.dialogs

import android.view.MotionEvent
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import piuk.blockchain.android.R
import piuk.blockchain.androidcore.utils.PersistentPrefs

@Deprecated("This is now built into the secure activity base class")
class OverlayDetection constructor(private val prefs: PersistentPrefs) {

    private var alertDialog: AlertDialog? = null

    fun detectObscuredWindow(activity: AppCompatActivity, event: MotionEvent): Boolean {
        // Detect if touch events are being obscured by hidden overlays - These could be used for tapjacking
        if (!prefs.getValue(PersistentPrefs.KEY_OVERLAY_TRUSTED, false) &&
            event.flags and MotionEvent.FLAG_WINDOW_IS_OBSCURED != 0
        ) {
            if (!(activity.isFinishing || activity.isDestroyed)) {
                // Prevent Not Attached To Window crash
                alertDialog?.dismiss() // Prevent multiple popups

                alertDialog = AlertDialog.Builder(activity, R.style.AlertDialogStyle)
                    .setTitle(R.string.screen_overlay_warning)
                    .setMessage(R.string.screen_overlay_note)
                    .setCancelable(false)
                    .setPositiveButton(R.string.dialog_continue) { _, _ ->
                        prefs.setValue(PersistentPrefs.KEY_OVERLAY_TRUSTED, true)
                    }
                    .setNegativeButton(R.string.exit) { _, _ -> activity.finish() }
                    .show()
            }
            return true
        }
        return false
    }
}