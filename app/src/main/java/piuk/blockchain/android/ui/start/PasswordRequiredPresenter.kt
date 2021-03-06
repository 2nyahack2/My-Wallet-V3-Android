package piuk.blockchain.android.ui.start

import com.blockchain.logging.CrashLogger
import piuk.blockchain.android.R
import piuk.blockchain.android.ui.launcher.LauncherActivity
import piuk.blockchain.android.util.AppUtil
import piuk.blockchain.androidcore.data.auth.AuthDataManager
import piuk.blockchain.androidcore.data.payload.PayloadDataManager
import piuk.blockchain.androidcore.utils.PersistentPrefs
import piuk.blockchain.androidcoreui.ui.customviews.ToastCustom

interface PasswordRequiredView : PasswordAuthView {
    fun restartPage()
    fun showForgetWalletWarning(onForgetConfirmed: () -> Unit)
    fun showWalletGuid(guid: String)
}

class PasswordRequiredPresenter(
    override val appUtil: AppUtil,
    override val prefs: PersistentPrefs,
    override val authDataManager: AuthDataManager,
    override val payloadDataManager: PayloadDataManager,
    override val crashLogger: CrashLogger
) : PasswordAuthPresenter<PasswordRequiredView>() {

    fun onContinueClicked(password: String) {
        if (password.length > 1) {
            val guid = prefs.getValue(PersistentPrefs.KEY_WALLET_GUID, "")
            verifyPassword(password, guid)
        } else {
            view?.apply {
                showToast(R.string.invalid_password, ToastCustom.TYPE_ERROR)
                restartPage()
            }
        }
    }

    fun onForgetWalletClicked() {
        view?.showForgetWalletWarning {
            appUtil.clearCredentialsAndRestart(LauncherActivity::class.java)
        }
    }

    fun loadWalletGuid() {
        view?.showWalletGuid(
            prefs.getValue(PersistentPrefs.KEY_WALLET_GUID, "")
        )
    }

    override fun onAuthFailed() {
        super.onAuthFailed()
        showErrorToastAndRestartApp(R.string.auth_failed)
    }
}
