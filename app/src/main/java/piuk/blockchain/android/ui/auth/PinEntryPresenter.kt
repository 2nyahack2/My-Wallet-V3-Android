package piuk.blockchain.android.ui.auth

import android.annotation.SuppressLint
import android.view.View
import androidx.annotation.StringRes
import androidx.annotation.UiThread
import androidx.annotation.VisibleForTesting
import com.blockchain.logging.CrashLogger
import com.blockchain.notifications.analytics.Analytics
import com.blockchain.notifications.analytics.AnalyticsEvents
import com.blockchain.remoteconfig.RemoteConfig
import info.blockchain.wallet.api.Environment
import info.blockchain.wallet.api.data.UpdateType
import info.blockchain.wallet.exceptions.AccountLockedException
import info.blockchain.wallet.exceptions.DecryptionException
import info.blockchain.wallet.exceptions.HDWalletException
import info.blockchain.wallet.exceptions.InvalidCredentialsException
import info.blockchain.wallet.exceptions.ServerConnectionException
import info.blockchain.wallet.exceptions.UnsupportedVersionException
import io.reactivex.Single
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.rxkotlin.subscribeBy
import org.spongycastle.crypto.InvalidCipherTextException
import piuk.blockchain.android.R
import piuk.blockchain.android.data.biometrics.BiometricsController
import piuk.blockchain.android.ui.home.CredentialsWiper
import piuk.blockchain.android.ui.launcher.LauncherActivity
import piuk.blockchain.android.util.AppUtil
import piuk.blockchain.android.util.StringUtils
import piuk.blockchain.androidcore.data.access.AccessState
import piuk.blockchain.androidcore.data.api.EnvironmentConfig
import piuk.blockchain.androidcore.data.auth.AuthDataManager
import piuk.blockchain.androidcore.data.payload.PayloadDataManager
import piuk.blockchain.androidcore.data.walletoptions.WalletOptionsDataManager
import piuk.blockchain.androidcore.utils.PersistentPrefs
import piuk.blockchain.androidcore.utils.PrngFixer
import piuk.blockchain.androidcore.utils.annotations.Thunk
import piuk.blockchain.androidcoreui.ui.base.BasePresenter
import piuk.blockchain.androidcoreui.ui.customviews.ToastCustom
import piuk.blockchain.androidcoreui.utils.logging.Logging
import timber.log.Timber
import java.net.SocketTimeoutException

class PinEntryPresenter(
    private val analytics: Analytics,
    private val authDataManager: AuthDataManager,
    private val appUtil: AppUtil,
    private val prefs: PersistentPrefs,
    private val payloadDataManager: PayloadDataManager,
    private val stringUtils: StringUtils,
    private val accessState: AccessState,
    private val walletOptionsDataManager: WalletOptionsDataManager,
    private val environmentSettings: EnvironmentConfig,
    private val prngFixer: PrngFixer,
    private val mobileNoticeRemoteConfig: MobileNoticeRemoteConfig,
    private val crashLogger: CrashLogger,
    private val config: RemoteConfig,
    private val credentialsWiper: CredentialsWiper,
    private val biometricsController: BiometricsController
) : BasePresenter<PinEntryView>() {

    @VisibleForTesting
    var canShowFingerprintDialog = true

    @VisibleForTesting
    var isForValidatingPinForResult = false

    @VisibleForTesting
    var userEnteredPin = ""

    @VisibleForTesting
    var userEnteredConfirmationPin: String? = null

    @VisibleForTesting
    internal var bAllowExit = true

    internal val ifShouldShowFingerprintLogin: Boolean
        get() = (!(isForValidatingPinForResult || isCreatingNewPin) &&
            biometricsController.isFingerprintUnlockEnabled)

    val isCreatingNewPin: Boolean
        get() = prefs.pinId.isEmpty()

    private val isChangingPin: Boolean
        get() = isCreatingNewPin && accessState.pin.isNotEmpty()

    override fun onViewReady() {
        prngFixer.applyPRNGFixes()

        if (view.pageIntent != null) {
            val extras = view.pageIntent?.extras
            if (extras != null) {
                if (extras.containsKey(KEY_VALIDATING_PIN_FOR_RESULT)) {
                    isForValidatingPinForResult = extras.getBoolean(KEY_VALIDATING_PIN_FOR_RESULT)
                }
            }
        }

        checkPinFails()
        checkFingerprintStatus()
        doTestnetCheck()
        setupCommitHash()
    }

    private fun setupCommitHash() {
        view.setupCommitHashView()
    }

    private fun doTestnetCheck() {
        if (environmentSettings.environment == Environment.TESTNET) {
            view.showTestnetWarning()
        }
    }

    fun checkFingerprintStatus() {
        if (ifShouldShowFingerprintLogin) {
            view.showFingerprintDialog()
        } else {
            view.showKeyboard()
        }
    }

    fun canShowFingerprintDialog(): Boolean {
        return canShowFingerprintDialog
    }

    fun loginWithDecryptedPin(pincode: String) {
        canShowFingerprintDialog = false
        for (view in view.pinBoxList) {
            view.setImageResource(R.drawable.rounded_view_dark_blue)
        }
        validatePIN(pincode)
    }

    fun onDeleteClicked() {
        if (userEnteredPin.isNotEmpty()) {
            // Remove last char from pin string
            userEnteredPin = userEnteredPin.substring(0, userEnteredPin.length - 1)

            // Clear last box
            view.pinBoxList[userEnteredPin.length].setImageResource(R.drawable.rounded_view_blue_white_border)
        }
    }

    fun onPadClicked(string: String?) {
        if (string == null || userEnteredPin.length == PIN_LENGTH) {
            return
        }

        // Append tapped #
        userEnteredPin += string

        for (i in 0 until userEnteredPin.length) {
            // Ensures that all necessary dots are filled
            view.pinBoxList[i].setImageResource(R.drawable.rounded_view_dark_blue)
        }

        // Perform appropriate action if PIN_LENGTH has been reached
        if (userEnteredPin.length == PIN_LENGTH) {

            // Throw error on '0000' to avoid server-side type issue
            if (userEnteredPin == "0000") {
                showErrorToast(R.string.zero_pin)
                clearPinViewAndReset()
                if (isCreatingNewPin) {
                    view.setTitleString(R.string.create_pin)
                }
                return
            }

            if (userEnteredConfirmationPin == null) {
                analytics.logEventOnce(AnalyticsEvents.WalletSignupPINFirst)
            } else {
                analytics.logEventOnce(AnalyticsEvents.WalletSignupPINSecond)
            }

            // Only show warning on first entry and if user is creating a new PIN
            if (isCreatingNewPin && isPinCommon(userEnteredPin) && userEnteredConfirmationPin == null) {
                view.showCommonPinWarning(object : DialogButtonCallback {
                    override fun onPositiveClicked() {
                        clearPinViewAndReset()
                    }

                    override fun onNegativeClicked() {
                        validateAndConfirmPin()
                    }
                })

                // If user is changing their PIN and it matches their old one, disallow it
            } else if (isChangingPin &&
                userEnteredConfirmationPin == null &&
                accessState.pin == userEnteredPin
            ) {
                showErrorToast(R.string.change_pin_new_matches_current)
                clearPinViewAndReset()
            } else {
                validateAndConfirmPin()
            }
        }
    }

    @Thunk
    internal fun validateAndConfirmPin() {
        // Validate
        when {
            prefs.pinId.isNotEmpty() -> {
                view.setTitleVisibility(View.INVISIBLE)
                validatePIN(userEnteredPin)
            }
            userEnteredConfirmationPin == null -> {
                // End of Create -  Change to Confirm
                userEnteredConfirmationPin = userEnteredPin
                userEnteredPin = ""
                view.setTitleString(R.string.confirm_pin)
                clearPinBoxes()
            }
            userEnteredConfirmationPin == userEnteredPin -> {
                // End of Confirm - Pin is confirmed
                createNewPin(userEnteredPin)
            }
            else -> {
                // End of Confirm - Pin Mismatch
                showErrorToast(R.string.pin_mismatch_error)
                view.setTitleString(R.string.create_pin)
                clearPinViewAndReset()
            }
        }
    }

    /**
     * Resets the view without restarting the page
     */
    @Thunk
    internal fun clearPinViewAndReset() {
        clearPinBoxes()
        userEnteredConfirmationPin = null
        checkFingerprintStatus()
    }

    fun clearPinBoxes() {
        userEnteredPin = ""
        view?.clearPinBoxes()
    }

    @VisibleForTesting
    fun updatePayload(password: String, isFromPinCreation: Boolean = false) {
        view.showProgressDialog(R.string.decrypting_wallet, null)

        compositeDisposable += payloadDataManager.initializeAndDecrypt(
            prefs.getValue(PersistentPrefs.KEY_SHARED_KEY, ""),
            prefs.getValue(PersistentPrefs.KEY_WALLET_GUID, ""),
            password
        )
            .doAfterTerminate {
                view.dismissProgressDialog()
                canShowFingerprintDialog = true
            }
            .subscribeBy(
                onComplete = { handlePayloadUpdateComplete(isFromPinCreation) },
                onError = { handlePayloadUpdateError(it) }
            )
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun handlePayloadUpdateComplete(isFromPinCreation: Boolean) {
        val wallet = payloadDataManager.wallet!!
        appUtil.sharedKey = wallet.sharedKey

        setAccountLabelIfNecessary()

        Logging.logLogin(true)

        if (!wallet.isUpgraded) {
            view.goToUpgradeWalletActivity()
        } else {
            if (isFromPinCreation && biometricsController.isFingerprintAvailable) {
                view.askToUseBiometrics()
            } else {
                view.restartAppWithVerifiedPin()
            }
        }
    }

    fun finishSignupProcess() {
        view.restartAppWithVerifiedPin()
    }

    private fun handlePayloadUpdateError(t: Throwable) {
        Logging.logLogin(false)

        when (t) {
            is InvalidCredentialsException -> view.goToPasswordRequiredActivity()
            is ServerConnectionException,
            is SocketTimeoutException -> {
                showFatalErrorToastAndRestart(R.string.server_unreachable_exit, t)
            }
            is UnsupportedVersionException -> view.showWalletVersionNotSupportedDialog(t.message)
            is DecryptionException -> view.goToPasswordRequiredActivity()
            is HDWalletException -> {
                // This shouldn't happen. HD fatal error - not safe to continue - don't clear credentials
                showFatalErrorToastAndRestart(R.string.unexpected_error, t)
            }
            is InvalidCipherTextException -> {
                // Password changed on web, needs re-pairing
                crashLogger.logEvent("password changed elsewhere. Pin is reset")
                accessState.clearPin()
                appUtil.clearCredentials()
                showFatalErrorToastAndRestart(R.string.password_changed_explanation, t)
            }
            is AccountLockedException -> view.showAccountLockedDialog()
            else -> {
                showFatalErrorToastAndRestart(R.string.unexpected_error, t)
            }
        }
    }

    fun validatePassword(password: String) {
        view.showProgressDialog(R.string.validating_password, null)

        compositeDisposable += payloadDataManager.initializeAndDecrypt(
            prefs.getValue(PersistentPrefs.KEY_SHARED_KEY, ""),
            prefs.getValue(PersistentPrefs.KEY_WALLET_GUID, ""),
            password
        )
            .doAfterTerminate { view.dismissProgressDialog() }
            .subscribeBy(
                onComplete = { handlePasswordValidated() },
                onError = { throwable -> handlePasswordValidatedError(throwable) }
            )
    }

    private fun handlePasswordValidated() {
        showMessageToast(R.string.pin_4_strikes_password_accepted)
        prefs.removeValue(PersistentPrefs.KEY_PIN_FAILS)
        prefs.pinId = ""
        crashLogger.logEvent("new password. pin reset")
        accessState.clearPin()
        view.restartPageAndClearTop()
    }

    private fun handlePasswordValidatedError(t: Throwable) {
        when (t) {
            is ServerConnectionException,
            is SocketTimeoutException ->
                showFatalErrorToastAndRestart(R.string.server_unreachable_exit, t)
            is HDWalletException -> {
                // This shouldn't happen. HD fatal error - not safe to continue - don't clear credentials
                showFatalErrorToastAndRestart(R.string.unexpected_error, t)
            }
            is AccountLockedException -> view.showAccountLockedDialog()
            else -> {
                crashLogger.logException(t)
                showErrorToast(R.string.invalid_password)
                view.showValidationDialog()
            }
        }
    }

    private fun createNewPin(pin: String) {
        val tempPassword = payloadDataManager.tempPassword
        if (tempPassword == null) {
            showErrorToast(R.string.create_pin_failed)
            prefs.clear()
            appUtil.restartApp(LauncherActivity::class.java)
            return
        }

        compositeDisposable += authDataManager.createPin(tempPassword, pin)
            .doOnSubscribe { view.showProgressDialog(R.string.creating_pin, null) }
            .subscribeBy(
                onComplete = {
                    view.dismissProgressDialog()
                    biometricsController.setFingerprintUnlockEnabled(false)
                    prefs.setValue(PersistentPrefs.KEY_PIN_FAILS, 0)
                    updatePayload(tempPassword, true)
                },
                onError = {
                    showErrorToast(R.string.create_pin_failed)
                    prefs.clear()
                    appUtil.restartApp(LauncherActivity::class.java)
                }
            )
    }

    @SuppressLint("CheckResult")
    private fun validatePIN(pin: String) {
        view.showProgressDialog(R.string.validating_pin, null)

        authDataManager.validatePin(pin)
            .subscribe({ password ->
                view.dismissProgressDialog()
                if (password != null) {
                    if (isForValidatingPinForResult) {
                        view.finishWithResultOk(pin)
                    } else {
                        updatePayload(password)
                    }
                    prefs.setValue(PersistentPrefs.KEY_PIN_FAILS, 0)
                } else {
                    handleValidateFailure()
                }
            }, { throwable ->
                Timber.e(throwable)
                if (throwable is InvalidCredentialsException) {
                    handleValidateFailure()
                } else {
                    showErrorToast(R.string.api_fail)
                    view.restartPageAndClearTop()
                }
            })
    }

    private fun handleValidateFailure() {
        if (isForValidatingPinForResult) {
            incrementFailureCount()
        } else {
            incrementFailureCountAndRestart()
        }
    }

    private fun incrementFailureCount() {
        var fails = prefs.getValue(PersistentPrefs.KEY_PIN_FAILS, 0)
        prefs.setValue(PersistentPrefs.KEY_PIN_FAILS, ++fails)
        showErrorToast(R.string.invalid_pin)
        userEnteredPin = ""
        for (textView in view.pinBoxList) {
            textView.setImageResource(R.drawable.rounded_view_blue_white_border)
        }
        view.setTitleVisibility(View.VISIBLE)
        view.setTitleString(R.string.pin_entry)
    }

    fun incrementFailureCountAndRestart() {
        var fails = prefs.getValue(PersistentPrefs.KEY_PIN_FAILS, 0)
        prefs.setValue(PersistentPrefs.KEY_PIN_FAILS, ++fails)
        showErrorToast(R.string.invalid_pin)
        view.restartPageAndClearTop()
    }

    // Check user's password if PIN fails >= max
    private fun checkPinFails() {
        val fails = prefs.getValue(PersistentPrefs.KEY_PIN_FAILS, 0)
        getPinRetriesFromRemoteConfig { maxAttempts ->
            if (fails >= maxAttempts) {
                showParameteredErrorToast(R.string.pin_max_strikes, maxAttempts)
                view.showMaxAttemptsDialog()
            }
        }
    }

    private fun setAccountLabelIfNecessary() {
        if (accessState.isNewlyCreated &&
            payloadDataManager.accounts.isNotEmpty() &&
            payloadDataManager.getAccount(0).label.isNullOrEmpty()
        ) {
            payloadDataManager.getAccount(0).label =
                stringUtils.getString(R.string.btc_default_wallet_name)
        }
    }

    private fun isPinCommon(pin: String): Boolean {
        val commonPins = listOf("1234", "1111", "1212", "7777", "1004")
        return commonPins.contains(pin)
    }

    fun resetApp() {
        credentialsWiper.wipe()
    }

    fun allowExit(): Boolean {
        return bAllowExit
    }

    @Suppress("SameParameterValue")
    @UiThread
    private fun showMessageToast(@StringRes message: Int) {
        view.showToast(message, ToastCustom.TYPE_OK)
    }

    @UiThread
    private fun showErrorToast(@StringRes message: Int) {
        view.dismissProgressDialog()
        view.showToast(message, ToastCustom.TYPE_ERROR)
    }

    @UiThread
    private fun showParameteredErrorToast(@StringRes message: Int, parameter: Int) {
        view.dismissProgressDialog()
        view.showParameteredToast(message, ToastCustom.TYPE_ERROR, parameter)
    }

    private class PinEntryLogException(cause: Throwable) : Exception(cause)

    @UiThread
    private fun showFatalErrorToastAndRestart(@StringRes message: Int, t: Throwable) {
        view.showToast(message, ToastCustom.TYPE_ERROR)
        crashLogger.logException(PinEntryLogException(t))
        appUtil.restartApp(LauncherActivity::class.java)
    }

    internal fun clearLoginState() {
        accessState.logout()
    }

    fun fetchInfoMessage() {
        compositeDisposable += mobileNoticeRemoteConfig.mobileNoticeDialog()
            .subscribeBy(
                onSuccess = { view.showMobileNotice(it) },
                onError = {
                    if (it is NoSuchElementException)
                        Timber.d("No mobile notice found")
                    else
                        Timber.e(it)
                }
            )
    }

    fun checkForceUpgradeStatus(versionName: String) {
        compositeDisposable += walletOptionsDataManager.checkForceUpgrade(versionName)
            .subscribeBy(
                onNext = { updateType ->
                    if (updateType !== UpdateType.NONE)
                        view.appNeedsUpgrade(updateType === UpdateType.FORCE)
                },
                onError = { Timber.e(it) }
            )
    }

    private fun getPinRetriesFromRemoteConfig(action: (Int) -> Unit) {
        compositeDisposable += Single.just(LOCAL_MAX_ATTEMPTS)
            .onErrorReturn { LOCAL_MAX_ATTEMPTS }
            .subscribeBy(
                onSuccess = {
                    action.invoke(it.toInt())
                }, onError = {
                    Timber.d("Error getting PIN tries from remote config: $it")
                }
            )
    }

    companion object {
        private const val PIN_LENGTH = 4
        private const val LOCAL_MAX_ATTEMPTS: Long = 4
        private const val PIN_RETRIES_KEY = "pin_retries_count"
    }
}
