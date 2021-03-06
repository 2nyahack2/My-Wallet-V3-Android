package piuk.blockchain.android

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.ProcessLifecycleOwner
import com.blockchain.koin.KoinStarter
import com.blockchain.koin.apiRetrofit
import com.blockchain.koin.explorerRetrofit
import com.blockchain.logging.CrashLogger
import com.facebook.stetho.Stetho
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.security.ProviderInstaller
import com.google.android.play.core.missingsplits.MissingSplitsManagerFactory
import info.blockchain.wallet.BlockchainFramework
import info.blockchain.wallet.FrameworkInterface
import info.blockchain.wallet.api.Environment
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.plugins.RxJavaPlugins
import io.reactivex.rxkotlin.subscribeBy
import org.bitcoinj.core.NetworkParameters
import org.koin.android.ext.android.get
import org.koin.android.ext.android.inject
import piuk.blockchain.android.data.coinswebsocket.service.CoinsWebSocketService
import piuk.blockchain.android.data.connectivity.ConnectivityManager
import piuk.blockchain.android.identity.SiftDigitalTrust
import piuk.blockchain.android.ui.auth.LogoutActivity
import piuk.blockchain.android.ui.home.models.MetadataEvent
import piuk.blockchain.android.ui.ssl.SSLVerifyActivity
import piuk.blockchain.android.util.AppUtil
import piuk.blockchain.android.util.CurrentContextAccess
import piuk.blockchain.android.util.lifecycle.AppLifecycleListener
import piuk.blockchain.android.util.lifecycle.LifecycleInterestedComponent
import piuk.blockchain.androidcore.data.access.AccessState
import piuk.blockchain.androidcore.data.api.EnvironmentConfig
import piuk.blockchain.androidcore.data.connectivity.ConnectionEvent
import piuk.blockchain.androidcore.data.rxjava.RxBus
import piuk.blockchain.androidcore.utils.PrngFixer
import piuk.blockchain.androidcoreui.ApplicationLifeCycle
import piuk.blockchain.androidcoreui.utils.logging.Logging
import piuk.blockchain.androidcoreui.utils.logging.appLaunchEvent
import retrofit2.Retrofit
import timber.log.Timber

open class BlockchainApplication : Application(), FrameworkInterface {

    private val retrofitApi: Retrofit by inject(apiRetrofit)
    private val retrofitExplorer: Retrofit by inject(explorerRetrofit)
    private val environmentSettings: EnvironmentConfig by inject()
    private val loginState: AccessState by inject()
    private val lifeCycleInterestedComponent: LifecycleInterestedComponent by inject()
    private val rxBus: RxBus by inject()
    private val currentContextAccess: CurrentContextAccess by inject()
    private val appUtils: AppUtil by inject()
    private val crashLogger: CrashLogger by inject()
    private val coinsWebSocketService: CoinsWebSocketService by inject()
    private val trust: SiftDigitalTrust by inject()

    private val lifecycleListener: AppLifecycleListener by lazy {
        AppLifecycleListener(lifeCycleInterestedComponent)
    }

    override fun onCreate() {

        if (MissingSplitsManagerFactory.create(this).disableAppIfMissingRequiredSplits()) {
            // Skip rest of the initialization to prevent the app from crashing.
            return
        }

        super.onCreate()

        // Init Timber
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        // Build the DI graphs:
        KoinStarter.start(this)
        initLifecycleListener()
        crashLogger.init(this)
        crashLogger.userLanguageLocale(resources.configuration.locale.language)

        if (environmentSettings.isRunningInDebugMode()) {
            Stetho.initializeWithDefaults(this)
        }

        // Pass objects to JAR - TODO: Remove this and use DI/Koin
        BlockchainFramework.init(this)

        UncaughtExceptionHandler.install(appUtils)

        RxJavaPlugins.setErrorHandler { throwable -> Timber.tag(RX_ERROR_TAG).e(throwable) }

        loginState.setLogoutActivity(LogoutActivity::class.java)

        // Apply PRNG fixes on app start if needed
        val prngUpdater: PrngFixer = get()
        prngUpdater.applyPRNGFixes()

        ApplicationLifeCycle.getInstance()
            .addListener(object : ApplicationLifeCycle.LifeCycleListener {
                override fun onBecameForeground() {
                    // Ensure that PRNG fixes are always current for the session
                    prngUpdater.applyPRNGFixes()
                }

                override fun onBecameBackground() {
                }
            })

        ConnectivityManager.getInstance().registerNetworkListener(this, rxBus)

        checkSecurityProviderAndPatchIfNeeded()

        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)

        registerActivityLifecycleCallbacks(activityCallback)

        // Report Google Play Services availability
        Logging.init(this)
        Logging.logEvent(appLaunchEvent(isGooglePlayServicesAvailable(this)))

        // Register the notification channel if necessary
        initNotifications()

        initRxBus()
    }

    @SuppressLint("CheckResult")
    private fun initRxBus() {
        rxBus.register(ConnectionEvent::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeBy(onNext = ::onBusConnectionEvent)

        rxBus.register(MetadataEvent::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeBy(onNext = ::onBusMetadataEvent)
    }

    private fun onBusConnectionEvent(event: ConnectionEvent) {
        SSLVerifyActivity.start(applicationContext, event)
    }

    private fun onBusMetadataEvent(event: MetadataEvent) {
        startCoinsWebService()
    }

    private fun startCoinsWebService() {
        coinsWebSocketService.start()
    }

    private fun initLifecycleListener() {
        ProcessLifecycleOwner.get().lifecycle
            .addObserver(lifecycleListener)
    }

    private fun initNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            // Create the NotificationChannel
            val channel2FA = NotificationChannel(
                "notifications_2fa",
                getString(R.string.notification_2fa_summary),
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = getString(R.string.notification_2fa_description) }

            // We create two channels, since the user may want to opt out of
            // payments notifications in the settings, and we don't need the
            // high importance flag on those.
            val channelPayments = NotificationChannel(
                "notifications_payments",
                getString(R.string.notification_payments_summary),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = getString(R.string.notification_payments_description) }
            // TODO do we want some custom vibration pattern?
            notificationManager.createNotificationChannel(channel2FA)
            notificationManager.createNotificationChannel(channelPayments)
        }
    }

    // FrameworkInterface
    // Pass instances to JAR Framework, evaluate after object graph instantiated fully
    override fun getRetrofitApiInstance(): Retrofit {
        return retrofitApi
    }

    override fun getRetrofitExplorerInstance(): Retrofit {
        return retrofitExplorer
    }

    override fun getEnvironment(): Environment {
        return environmentSettings.environment
    }

    override fun getBitcoinParams(): NetworkParameters {
        return environmentSettings.bitcoinNetworkParameters
    }

    override fun getBitcoinCashParams(): NetworkParameters {
        return environmentSettings.bitcoinCashNetworkParameters
    }

    override fun getDevice(): String {
        return "android"
    }

    override fun getAppVersion(): String {
        return BuildConfig.VERSION_NAME
    }

    override val apiCode: String
        get() = "25a6ad13-1633-4dfb-b6ee-9b91cdf0b5c3"

    /**
     * This patches a device's Security Provider asynchronously to help defend against various
     * vulnerabilities. This provider is normally updated in Google Play Services anyway, but this
     * will catch any immediate issues that haven't been fixed in a slow rollout.
     *
     * In the future, we may want to show some kind of warning to users or even stop the app, but
     * this will harm users with versions of Android without GMS approval.
     *
     * @see [Updating
     * Your Security Provider](https://developer.android.com/training/articles/security-gms-provider.html)
     */
    protected open fun checkSecurityProviderAndPatchIfNeeded() {
        ProviderInstaller.installIfNeededAsync(
            this,
            object : ProviderInstaller.ProviderInstallListener {
                override fun onProviderInstalled() {
                    Timber.i("Security Provider installed")
                }

                override fun onProviderInstallFailed(errorCode: Int, intent: Intent?) {
                    if (GoogleApiAvailability.getInstance().isUserResolvableError(errorCode)) {
                        showError(errorCode)
                    } else {
                        // Google Play services is not available.
                        onProviderInstallerNotAvailable()
                    }
                }
            })
    }

    /**
     * Show a dialog prompting the user to install/update/enable Google Play services.
     *
     * @param errorCode Recoverable error code
     */
    internal fun showError(errorCode: Int) {
        // TODO: 05/08/2016 Decide if we should alert users here or not
        Timber.e(
            "Security Provider install failed with recoverable error: %s",
            GoogleApiAvailability.getInstance().getErrorString(errorCode)
        )
    }

    /**
     * This is reached if the provider cannot be updated for some reason. App should consider all
     * HTTP communication to be vulnerable, and take appropriate action.
     */
    internal fun onProviderInstallerNotAvailable() {
        // TODO: 05/08/2016 Decide if we should take action here or not
        Timber.wtf("Security Provider Installer not available")
    }

    /**
     * Returns true if Google Play Services are found and ready to use.
     *
     * @param context The current Application Context
     */
    private fun isGooglePlayServicesAvailable(context: Context): Boolean {
        val availability = GoogleApiAvailability.getInstance()
        return availability.isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
    }

    private val activityCallback = object : ActivityLifecycleCallbacks {

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            trust.onActivityCreate(activity)
        }

        override fun onActivityResumed(activity: Activity) {
            currentContextAccess.contextOpen(activity)
            trust.onActivityResume(activity)
        }

        override fun onActivityPaused(activity: Activity) {
            currentContextAccess.contextClose(activity)
            trust.onActivityPause()
        }

        override fun onActivityStarted(activity: Activity) {}

        override fun onActivityStopped(activity: Activity) {
            trust.onActivityClose()
        }

        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

        override fun onActivityDestroyed(activity: Activity) {}
    }

    companion object {
        const val RX_ERROR_TAG = "RxJava Error"
    }
}
