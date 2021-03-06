package piuk.blockchain.android.util

import android.content.Context
import android.content.Intent
import com.blockchain.ui.ActivityIndicator
import info.blockchain.wallet.payload.PayloadManagerWiper
import piuk.blockchain.androidcore.data.access.AccessState
import piuk.blockchain.androidcore.utils.PersistentPrefs
import piuk.blockchain.androidcore.utils.extensions.isValidGuid

class AppUtil(
    private val context: Context,
    private var payloadManager: PayloadManagerWiper,
    private var accessState: AccessState,
    private val prefs: PersistentPrefs
) {
    val isSane: Boolean
        get() {
            val guid = prefs.getValue(PersistentPrefs.KEY_WALLET_GUID, "")
            val encryptedPassword = prefs.getValue(PersistentPrefs.KEY_ENCRYPTED_PASSWORD, "")
            val pinID = prefs.pinId

            return guid.isValidGuid() && encryptedPassword.isNotEmpty() && pinID.isNotEmpty()
        }

    @Deprecated("Use prefs directly")
    var sharedKey: String
        get() = prefs.getValue(PersistentPrefs.KEY_SHARED_KEY, "")
        set(sharedKey) = prefs.setValue(PersistentPrefs.KEY_SHARED_KEY, sharedKey)

    var activityIndicator: ActivityIndicator? = null

    fun clearCredentials() {
        payloadManager.wipe()
        prefs.clear()
        accessState.forgetWallet()
    }

    fun clearCredentialsAndRestart(launcherActivity: Class<*>) {
        clearCredentials()
        restartApp(launcherActivity)
    }

    fun restartApp(launcherActivity: Class<*>) {
        context.startActivity(
            Intent(context, launcherActivity).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }

    fun restartAppWithVerifiedPin(launcherActivity: Class<*>, isAfterWalletCreation: Boolean = false) {
        context.startActivity(
            Intent(context, launcherActivity).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(INTENT_EXTRA_VERIFIED, true)
                putExtra(INTENT_EXTRA_IS_AFTER_WALLET_CREATION, isAfterWalletCreation)
            }
        )
        accessState.logIn()
    }

    companion object {
        const val INTENT_EXTRA_VERIFIED = "verified"
        const val INTENT_EXTRA_IS_AFTER_WALLET_CREATION = "is_after_wallet_creation"
    }
}
