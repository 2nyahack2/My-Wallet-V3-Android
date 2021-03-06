package piuk.blockchain.android.ui.shortcuts

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build
import androidx.annotation.RequiresApi
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import org.koin.core.KoinComponent
import org.koin.core.get
import piuk.blockchain.android.R
import piuk.blockchain.android.ui.shortcuts.receive.ReceiveQrActivity
import piuk.blockchain.androidcore.data.payload.PayloadDataManager
import piuk.blockchain.androidcore.utils.PersistentPrefs
import piuk.blockchain.androidcore.utils.helperfunctions.unsafeLazy
import piuk.blockchain.android.util.AndroidUtils
import piuk.blockchain.androidcoreui.utils.logging.launcherShortcutEvent
import piuk.blockchain.androidcoreui.utils.logging.Logging

class LauncherShortcutHelper(
    private val ctx: Context
) : KoinComponent {

    private val shortcutManager: ShortcutManager by unsafeLazy {
        if (AndroidUtils.is25orHigher()) {
            ctx.getSystemService(ShortcutManager::class.java)
        } else {
            throw IllegalStateException("Service not available")
        }
    }

    private var shortcutsGenerated = false

    fun generateReceiveShortcuts() {
        if (AndroidUtils.is25orHigher() && !shortcutsGenerated && areShortcutsEnabled()) {
            doGenerateReceiveShortcuts()
        }
    }

    @SuppressLint("CheckResult")
    @RequiresApi(api = Build.VERSION_CODES.N_MR1)
    private fun doGenerateReceiveShortcuts() {

        val payloadDataManager: PayloadDataManager = get()
        val receiveAccountName = payloadDataManager.defaultAccount.label

        payloadDataManager.getNextReceiveAddress(payloadDataManager.defaultAccountIndex)
            .subscribeOn(Schedulers.computation())
            .observeOn(Schedulers.newThread())
            .subscribeBy(
                onNext = { receiveAddress: String? ->
                    shortcutManager.removeAllDynamicShortcuts()
                    receiveAddress?.let {
                        shortcutManager.dynamicShortcuts = listOf(
                            makeCopyShortcut(it),
                            makeQrShortcut(it, receiveAccountName)
                        )
                    }
                    shortcutsGenerated = true
                },
                onError = {
                    it.printStackTrace()
                }
            )
    }

    @RequiresApi(api = Build.VERSION_CODES.N_MR1)
    private fun makeCopyShortcut(receiveAddress: String) =
        ShortcutInfo.Builder(
            ctx,
            SHORTCUT_ID_COPY
        )
        .setShortLabel(ctx.getString(R.string.shortcut_receive_copy_short))
        .setLongLabel(ctx.getString(R.string.shortcut_receive_copy_long))
        .setIcon(
            Icon.createWithResource(
                ctx,
                R.drawable.ic_receive_copy
            )
        )
        .setIntent(
            Intent().apply {
                action = Intent.ACTION_SEND
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, receiveAddress)
            }
        )
        .build()

    @RequiresApi(api = Build.VERSION_CODES.N_MR1)
    private fun makeQrShortcut(receiveAddress: String, receiveAccountName: String) =
        ShortcutInfo.Builder(
            ctx,
            SHORTCUT_ID_QR
        )
        .setShortLabel(ctx.getString(R.string.shortcut_receive_qr_short))
        .setLongLabel(ctx.getString(R.string.shortcut_receive_qr_long))
        .setIcon(
            Icon.createWithResource(
                ctx,
                R.drawable.ic_receive_scan
            )
        )
        .setIntent(
            Intent(ctx, ReceiveQrActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                putExtra(ReceiveQrActivity.INTENT_EXTRA_ADDRESS, receiveAddress)
                putExtra(ReceiveQrActivity.INTENT_EXTRA_LABEL, receiveAccountName)
            }
        )
        .build()

    fun logShortcutUsed(shortcutId: String) {
        if (AndroidUtils.is25orHigher()) {
            doLogShortcutUsed(shortcutId)
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.N_MR1)
    private fun doLogShortcutUsed(shortcutId: String) {
        shortcutManager.reportShortcutUsed(shortcutId)
        Logging.logEvent(launcherShortcutEvent(shortcutId))
    }

    private fun areShortcutsEnabled(): Boolean {
        val prefs: PersistentPrefs = get()
        return prefs.getValue(PersistentPrefs.KEY_RECEIVE_SHORTCUTS_ENABLED, true)
    }

    companion object {
        const val SHORTCUT_ID_COPY = "SHORTCUT_ID_COPY"
        const val SHORTCUT_ID_QR = "SHORTCUT_ID_QR"
    }
}