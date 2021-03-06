package piuk.blockchain.android.ui.launcher

import android.annotation.SuppressLint
import android.app.LauncherActivity
import android.content.Intent
import com.blockchain.logging.CrashLogger
import com.blockchain.notifications.NotificationTokenManager
import com.blockchain.notifications.analytics.Analytics
import com.blockchain.preferences.CurrencyPrefs
import info.blockchain.wallet.api.Environment
import com.blockchain.notifications.analytics.AnalyticsEvents
import info.blockchain.wallet.api.data.Settings
import info.blockchain.wallet.exceptions.HDWalletException
import info.blockchain.wallet.exceptions.InvalidCredentialsException
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.rxkotlin.zipWith
import piuk.blockchain.android.R
import piuk.blockchain.androidcore.data.access.AccessState
import piuk.blockchain.androidcore.data.api.EnvironmentConfig
import piuk.blockchain.androidcore.data.payload.PayloadDataManager
import piuk.blockchain.androidcore.data.settings.SettingsDataManager
import piuk.blockchain.androidcore.utils.PersistentPrefs
import piuk.blockchain.androidcoreui.ui.base.BasePresenter
import piuk.blockchain.androidcoreui.ui.customviews.ToastCustom
import piuk.blockchain.android.util.AppUtil
import timber.log.Timber

class LauncherPresenter(
    private val appUtil: AppUtil,
    private val payloadDataManager: PayloadDataManager,
    private val prefs: PersistentPrefs,
    private val deepLinkPersistence: DeepLinkPersistence,
    private val accessState: AccessState,
    private val settingsDataManager: SettingsDataManager,
    private val notificationTokenManager: NotificationTokenManager,
    private val envSettings: EnvironmentConfig,
    private val currencyPrefs: CurrencyPrefs,
    private val analytics: Analytics,
    private val prerequisites: Prerequisites,
    private val crashLogger: CrashLogger
) : BasePresenter<LauncherView>() {

    override fun onViewReady() {
        analytics.logEventOnce(AnalyticsEvents.WalletSignupOpen)
        val intent = view.getPageIntent()
        val action = intent.action
        val scheme = intent.scheme
        val intentData = intent.dataString
        val extras = intent.extras
        val hasLoggedOut = prefs.isLoggedOut
        var isPinValidated = false

        // Store incoming bitcoin URI if needed
        if (action != null && Intent.ACTION_VIEW == action && scheme != null && scheme == "bitcoin") {
            prefs.setValue(PersistentPrefs.KEY_SCHEME_URL, intent.data.toString())
        }

        if (Intent.ACTION_VIEW == action) {
            deepLinkPersistence.pushDeepLink(intent.data)
        }

        // Store incoming Contacts URI if needed
        if (action != null && Intent.ACTION_VIEW == action && intentData != null && intentData.contains(
                "blockchain"
            )
        ) {
            prefs.setValue(PersistentPrefs.KEY_METADATA_URI, intentData)
        }

        if (extras != null && extras.containsKey(INTENT_EXTRA_VERIFIED)) {
            isPinValidated = extras.getBoolean(INTENT_EXTRA_VERIFIED)
        }

        if (extras?.containsKey("IS_AUTOMATION_TESTING") == true) {
            if (extras.getBoolean(INTENT_AUTOMATION_TEST) && Environment.STAGING == envSettings.environment) {
                prefs.setIsUnderTest()
            }
        }

        val hasBackup = prefs.hasBackup()
        val pin = prefs.pinId

        when {
            // No GUID and no backup? Treat as new installation
            prefs.getValue(PersistentPrefs.KEY_WALLET_GUID, "").isEmpty() && !hasBackup -> view.onNoGuid()
            // No GUID but a backup. Show PIN entry page to populate other values
            prefs.getValue(PersistentPrefs.KEY_WALLET_GUID, "").isEmpty() && hasBackup -> view.onRequestPin()
            // User has logged out recently. Show password reentry page
            hasLoggedOut -> view.onReEnterPassword()
            // No PIN ID? Treat as installed app without confirmed PIN
            pin.isEmpty() -> view.onRequestPin()
            // Installed app, check sanity
            !appUtil.isSane -> view.onCorruptPayload()
            // Legacy app has not been prompted for upgrade
            isPinValidated && upgradeNeeded() -> promptUpgrade()
            // App has been PIN validated
            isPinValidated || accessState.isLoggedIn -> initSettings()
            // Something odd has happened, re-request PIN
            else -> view.onRequestPin()
        }
    }

    private fun upgradeNeeded(): Boolean =
        payloadDataManager.wallet?.isUpgraded == false

    fun clearCredentialsAndRestart() =
        appUtil.clearCredentialsAndRestart(LauncherActivity::class.java)

    private fun promptUpgrade() {
        accessState.isLoggedIn = true
        view.onRequestUpgrade()
    }

    fun clearLoginState() {
        accessState.logout()
    }

    /**
     * Init of the [SettingsDataManager] must complete here so that we can access the [Settings]
     * object from memory when the user is logged in.
     */
    @SuppressLint("CheckResult")
    private fun initSettings() {

        val settings = Single.defer {
            Single.just(payloadDataManager.wallet!!)
        }.flatMap { wallet ->
            prerequisites.initSettings(
                wallet.guid,
                wallet.sharedKey
            ).doOnSuccess {
                // If the account is new, we need to check if we should launch Simple buy flow
                // (in that case, currency will be selected by user manually)
                // or select the default from device Locale
                if (!isNewAccount())
                    setCurrencyUnits(it)
            }
        }

        val metadata = prerequisites.initMetadataAndRelatedPrerequisites()
        val updateFiatWithDefault = settingsDataManager.updateFiatUnit(currencyPrefs.defaultFiatCurrency)
            .ignoreElements()

        compositeDisposable +=
            settings.zipWith(
                metadata.toSingleDefault(true)
            ).flatMap { (_, _) ->
                if (!shouldCheckForSimpleBuyLaunching())
                    Single.just(false)
                else {
                    Single.just(walletJustCreated())
                }
            }.flatMap { simpleBuyShouldLaunched ->
                if (!simpleBuyShouldLaunched && noCurrencySet())
                    updateFiatWithDefault.toSingleDefault(simpleBuyShouldLaunched)
                else {
                    Single.just(simpleBuyShouldLaunched)
                }
            }
            .doOnSuccess { accessState.isLoggedIn = true }
            .doOnSuccess { notificationTokenManager.registerAuthEvent() }
            .observeOn(AndroidSchedulers.mainThread())
            .doOnSubscribe { view.updateProgressVisibility(true) }
            .subscribeBy(
                onSuccess = { simpleBuyShouldLaunched ->
                    view.updateProgressVisibility(false)
                    if (simpleBuyShouldLaunched) {
                        launchBuySellIntro()
                    } else {
                        startMainActivity()
                    }
                }, onError = { throwable ->
                    view.updateProgressVisibility(false)
                    if (throwable is InvalidCredentialsException || throwable is HDWalletException) {
                        if (payloadDataManager.isDoubleEncrypted) {
                            // Wallet double encrypted and needs to be decrypted to set up ether wallet, contacts etc
                            view?.showSecondPasswordDialog()
                        } else {
                        view.showToast(R.string.unexpected_error, ToastCustom.TYPE_ERROR)
                            view.onRequestPin()
                        }
                    } else {
                        view.showToast(R.string.unexpected_error, ToastCustom.TYPE_ERROR)
                        view.onRequestPin()
                    }
                    logException(throwable)
                }
            )
        }

    private fun isNewAccount(): Boolean = accessState.isNewlyCreated

    private fun walletJustCreated() =
        view?.getPageIntent()?.getBooleanExtra(AppUtil.INTENT_EXTRA_IS_AFTER_WALLET_CREATION,
            false) == true

    internal fun decryptAndSetupMetadata(secondPassword: String) {
        if (!payloadDataManager.validateSecondPassword(secondPassword)) {
            view?.showToast(R.string.invalid_password, ToastCustom.TYPE_ERROR)
            view?.showSecondPasswordDialog()
        } else {
            compositeDisposable += prerequisites.decryptAndSetupMetadata(secondPassword)
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe {
                    view.updateProgressVisibility(true)
                }.subscribeBy(
                    onError = {
                        view.updateProgressVisibility(false)
                        Timber.e(it)
                    },
                    onComplete = {
                        view.updateProgressVisibility(false)
                        appUtil.restartApp(piuk.blockchain.android.ui.launcher.LauncherActivity::class.java)
                    }
                )
        }
    }

    private fun noCurrencySet() =
        currencyPrefs.selectedFiatCurrency.isEmpty()

    private fun logException(throwable: Throwable) {
        crashLogger.logException(throwable)
        view?.showMetadataNodeFailure()
    }

    private fun shouldCheckForSimpleBuyLaunching() = accessState.isNewlyCreated && !accessState.isRestored

    private fun startMainActivity() {
        view.onStartMainActivity(deepLinkPersistence.popUriFromSharedPrefs())
    }

    private fun launchBuySellIntro() {
        view.launchBuySellIntro()
    }

    private fun setCurrencyUnits(settings: Settings) {
        prefs.selectedFiatCurrency = settings.currency
    }

    companion object {
        const val INTENT_EXTRA_VERIFIED = "verified"
        const val INTENT_AUTOMATION_TEST = "IS_AUTOMATION_TESTING"
    }
}
