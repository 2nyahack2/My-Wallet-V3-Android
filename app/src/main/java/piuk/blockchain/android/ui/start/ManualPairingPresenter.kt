package piuk.blockchain.android.ui.start

import com.blockchain.logging.CrashLogger
import com.blockchain.notifications.analytics.Analytics
import com.blockchain.notifications.analytics.AnalyticsEvents
import piuk.blockchain.android.R
import piuk.blockchain.android.util.AppUtil
import piuk.blockchain.androidcore.data.auth.AuthDataManager
import piuk.blockchain.androidcore.data.payload.PayloadDataManager
import piuk.blockchain.androidcore.utils.PersistentPrefs

interface ManualPairingView : PasswordAuthView

class ManualPairingPresenter(
    override val appUtil: AppUtil,
    override val authDataManager: AuthDataManager,
    override val payloadDataManager: PayloadDataManager,
    override val prefs: PersistentPrefs,
    private val analytics: Analytics,
    override val crashLogger: CrashLogger
) : PasswordAuthPresenter<ManualPairingView>() {

    internal fun onContinueClicked(guid: String, password: String) {
        when {
            guid.isEmpty() -> showErrorToast(R.string.invalid_guid)
            password.isEmpty() -> showErrorToast(R.string.invalid_password)
            else -> verifyPassword(password, guid)
        }
    }

    override fun onAuthFailed() {
        super.onAuthFailed()
        showErrorToast(R.string.auth_failed)
    }

    override fun onAuthComplete() {
        super.onAuthComplete()
        analytics.logEvent(AnalyticsEvents.WalletManualLogin)
    }
}
