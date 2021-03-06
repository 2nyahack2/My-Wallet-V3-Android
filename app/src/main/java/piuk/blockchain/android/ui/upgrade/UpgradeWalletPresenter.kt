package piuk.blockchain.android.ui.upgrade

import com.blockchain.logging.CrashLogger
import info.blockchain.wallet.util.PasswordUtil
import io.reactivex.rxkotlin.plusAssign
import piuk.blockchain.android.R
import piuk.blockchain.android.ui.launcher.LauncherActivity
import piuk.blockchain.androidcore.data.access.AccessState
import piuk.blockchain.androidcore.utils.PersistentPrefs
import piuk.blockchain.androidcore.data.auth.AuthDataManager
import piuk.blockchain.androidcore.data.payload.PayloadDataManager
import piuk.blockchain.androidcoreui.ui.base.BasePresenter
import piuk.blockchain.androidcoreui.ui.customviews.ToastCustom
import piuk.blockchain.android.util.AppUtil
import piuk.blockchain.android.util.StringUtils
import piuk.blockchain.androidcoreui.utils.logging.Logging
import piuk.blockchain.androidcoreui.utils.logging.walletUpgradeEvent

internal class UpgradeWalletPresenter constructor(
    private val prefs: PersistentPrefs,
    private val appUtil: AppUtil,
    private val accessState: AccessState,
    private val authDataManager: AuthDataManager,
    private val payloadDataManager: PayloadDataManager,
    private val stringUtils: StringUtils,
    private val crashLogger: CrashLogger
) : BasePresenter<UpgradeWalletView>() {

    override fun onViewReady() {
        // Check password existence
        val tempPassword = payloadDataManager.tempPassword
        if (tempPassword == null) {
            view.showToast(R.string.upgrade_fail_info, ToastCustom.TYPE_ERROR)
            appUtil.clearCredentialsAndRestart(LauncherActivity::class.java)
            return
        }

        // Check password strength
        if (PasswordUtil.ddpw(tempPassword) || PasswordUtil.getStrength(tempPassword) < 50) {
            view.showChangePasswordDialog()
        }
    }

    fun submitPasswords(firstPassword: String, secondPassword: String) {
        if (firstPassword.length < 4 || firstPassword.length > 255 ||
            secondPassword.length < 4 || secondPassword.length > 255) {
            view.showToast(R.string.invalid_password, ToastCustom.TYPE_ERROR)
        } else {
            if (firstPassword != secondPassword) {
                view.showToast(R.string.password_mismatch_error, ToastCustom.TYPE_ERROR)
            } else {
                val currentPassword = payloadDataManager.tempPassword
                payloadDataManager.tempPassword = secondPassword

                compositeDisposable += authDataManager.createPin(currentPassword!!, accessState.pin)
                    .andThen(payloadDataManager.syncPayloadWithServer())
                    .doOnError { payloadDataManager.tempPassword = currentPassword }
                    .doOnSubscribe { view.showProgressDialog(R.string.please_wait) }
                    .doAfterTerminate { view.dismissProgressDialog() }
                    .subscribe(
                        { view.showToast(R.string.password_changed, ToastCustom.TYPE_OK) },
                        {
                            view.showToast(R.string.remote_save_failed, ToastCustom.TYPE_ERROR)
                            view.showToast(R.string.password_unchanged, ToastCustom.TYPE_ERROR)
                        })
            }
        }
    }

    internal fun onUpgradeRequested(secondPassword: String?) {
        compositeDisposable += payloadDataManager.upgradeV2toV3(
            secondPassword,
            stringUtils.getString(R.string.btc_default_wallet_name))
            .doOnSubscribe { view.onUpgradeStarted() }
            .doOnError { accessState.isNewlyCreated = false }
            .doOnComplete { accessState.isNewlyCreated = true }
            .subscribe(
                {
                    Logging.logEvent(walletUpgradeEvent((true)))
                    view.onUpgradeCompleted()
                },
                { throwable ->
                    Logging.logEvent(walletUpgradeEvent((false)))
                    crashLogger.logException(throwable)
                    view.onUpgradeFailed()
                })
    }

    internal fun onContinueClicked() {
        prefs.setValue(PersistentPrefs.KEY_EMAIL_VERIFIED, true)
        accessState.isLoggedIn = true
        appUtil.restartAppWithVerifiedPin(LauncherActivity::class.java)
    }

    internal fun onBackButtonPressed() {
        accessState.logout()
        view.onBackButtonPressed()
    }
}
