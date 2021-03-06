package piuk.blockchain.android.ui.createwallet

import android.app.LauncherActivity
import com.blockchain.notifications.analytics.Analytics
import com.blockchain.notifications.analytics.AnalyticsEvents
import com.blockchain.preferences.WalletStatus
import info.blockchain.wallet.util.FormatsUtil
import info.blockchain.wallet.util.PasswordUtil
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.rxkotlin.subscribeBy
import piuk.blockchain.android.R
import piuk.blockchain.android.util.AppUtil
import piuk.blockchain.androidcore.data.access.AccessState
import piuk.blockchain.androidcore.data.api.EnvironmentConfig
import piuk.blockchain.androidcore.data.payload.PayloadDataManager
import piuk.blockchain.androidcore.utils.PersistentPrefs
import piuk.blockchain.androidcore.utils.PrngFixer
import piuk.blockchain.androidcoreui.ui.base.BasePresenter
import piuk.blockchain.androidcoreui.utils.logging.Logging
import timber.log.Timber
import kotlin.math.roundToInt

class CreateWalletPresenter(
    private val payloadDataManager: PayloadDataManager,
    private val prefs: PersistentPrefs,
    private val appUtil: AppUtil,
    private val accessState: AccessState,
    private val prngFixer: PrngFixer,
    private val analytics: Analytics,
    private val walletPrefs: WalletStatus,
    private val environmentConfig: EnvironmentConfig
) : BasePresenter<CreateWalletView>() {

    var passwordStrength = 0

    override fun onViewReady() {
        // No-op
    }

    fun calculateEntropy(password: String) {
        passwordStrength = PasswordUtil.getStrength(password).roundToInt()
        view.setEntropyStrength(passwordStrength)

        when (passwordStrength) {
            in 0..25 -> view.setEntropyLevel(0)
            in 26..50 -> view.setEntropyLevel(1)
            in 51..75 -> view.setEntropyLevel(2)
            in 76..100 -> view.setEntropyLevel(3)
        }
    }

    fun validateCredentials(email: String, password1: String, password2: String): Boolean =
        when {
            !FormatsUtil.isValidEmailAddress(email) -> {
                view.showError(R.string.invalid_email); false
            }
            password1.length < 4 -> {
                view.showError(R.string.invalid_password_too_short); false
            }
            password1.length > 255 -> {
                view.showError(R.string.invalid_password); false
            }
            password1 != password2 -> {
                view.showError(R.string.password_mismatch_error); false
            }
            !passwordStrength.isStrongEnough() -> {
                view.warnWeakPassword(email, password1); false
            }
            else -> true
        }

    fun createOrRestoreWallet(email: String, password: String, recoveryPhrase: String) =
        when {
            recoveryPhrase.isNotEmpty() -> recoverWallet(email, password, recoveryPhrase)
            else -> createWallet(email, password)
        }

    private fun createWallet(email: String, password: String) {
        analytics.logEventOnce(AnalyticsEvents.WalletSignupCreated)
        prngFixer.applyPRNGFixes()

        compositeDisposable += payloadDataManager.createHdWallet(password, view.getDefaultAccountName(), email)
            .doOnNext {
                accessState.isNewlyCreated = true
                prefs.setValue(PersistentPrefs.KEY_WALLET_GUID, payloadDataManager.wallet!!.guid)
                appUtil.sharedKey = payloadDataManager.wallet!!.sharedKey
            }
            .doOnSubscribe { view.showProgressDialog(R.string.creating_wallet) }
            .doOnTerminate { view.dismissProgressDialog() }
            .subscribe(
                {
                    walletPrefs.setNewUser()
                    prefs.setValue(PersistentPrefs.KEY_EMAIL, email)
                    view.startPinEntryActivity()
                    Logging.logSignUp(true)
                    analytics.logEvent(AnalyticsEvents.WalletCreation)
                },
                {
                    Timber.e(it)
                    view.showError(R.string.hd_error)
                    appUtil.clearCredentialsAndRestart(LauncherActivity::class.java)
                    Logging.logSignUp(false)
                }
            )
    }

    private fun recoverWallet(email: String, password: String, recoveryPhrase: String) {
        compositeDisposable += payloadDataManager.restoreHdWallet(
            recoveryPhrase,
            view.getDefaultAccountName(),
            email,
            password
        ).doOnNext {
            accessState.isNewlyCreated = true
            accessState.isRestored = true
            prefs.setValue(PersistentPrefs.KEY_WALLET_GUID, payloadDataManager.wallet!!.guid)
            appUtil.sharedKey = payloadDataManager.wallet!!.sharedKey
        }.doOnSubscribe {
            view.showProgressDialog(R.string.restoring_wallet)
        }.doOnTerminate {
            view.dismissProgressDialog()
        }.subscribeBy(
            onComplete = {
                prefs.setValue(PersistentPrefs.KEY_EMAIL, email)
                prefs.setValue(PersistentPrefs.KEY_ONBOARDING_COMPLETE, true)
                view.startPinEntryActivity()
                analytics.logEvent(WalletCreationEvent.RecoverWalletEvent(true))
            },
            onError = {
                Timber.e(it)
                view.showError(R.string.restore_failed)
                analytics.logEvent(WalletCreationEvent.RecoverWalletEvent(false))
            }
        )
    }

    fun logEventEmailClicked() = analytics.logEventOnce(AnalyticsEvents.WalletSignupClickEmail)
    fun logEventPasswordOneClicked() = analytics.logEventOnce(AnalyticsEvents.WalletSignupClickPasswordFirst)
    fun logEventPasswordTwoClicked() = analytics.logEventOnce(AnalyticsEvents.WalletSignupClickPasswordSecond)

    private fun Int.isStrongEnough(): Boolean {
        val limit = if (environmentConfig.isRunningInDebugMode()) 1 else 50
        return this >= limit
    }
}