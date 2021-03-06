package piuk.blockchain.androidcore.data.walletoptions

import com.blockchain.sunriver.XlmHorizonUrlFetcher
import com.blockchain.sunriver.XlmTransactionTimeoutFetcher
import info.blockchain.wallet.api.data.UpdateType
import info.blockchain.wallet.api.data.WalletOptions
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import piuk.blockchain.androidcore.data.appversion.SemanticVersion
import piuk.blockchain.androidcore.data.auth.AuthService
import piuk.blockchain.androidcore.data.settings.SettingsDataManager
import piuk.blockchain.androidcore.utils.helperfunctions.unsafeLazy
import java.util.Locale

class WalletOptionsDataManager(
    authService: AuthService,
    private val walletOptionsState: WalletOptionsState,
    private val settingsDataManager: SettingsDataManager,
    private val explorerUrl: String
) : XlmTransactionTimeoutFetcher, XlmHorizonUrlFetcher {

    override fun xlmHorizonUrl(def: String): Single<String> =
        walletOptionsState.walletOptionsSource
            .map { it.stellarHorizonUrl }.first(def)

    private val walletOptionsService by unsafeLazy {
        authService.getWalletOptions()
            .subscribeOn(Schedulers.io())
            .cache()
    }

    /**
     * ReplaySubjects will re-emit items it observed.
     * It is safe to assumed that walletOptions and
     * the user's country code won't change during an active session.
     */
    private fun initWalletOptionsReplaySubjects() {
        walletOptionsService
            .subscribeOn(Schedulers.io())
            .subscribeWith(walletOptionsState.walletOptionsSource)
    }

    @Suppress("unused")
    private fun initSettingsReplaySubjects(guid: String, sharedKey: String) {
        settingsDataManager.initSettings(guid, sharedKey)

        settingsDataManager.getSettings()
            .subscribeOn(Schedulers.io())
            .subscribeWith(walletOptionsState.walletSettingsSource)
    }

    @Suppress("unused") // May be useful in future
    fun isInUsa(): Observable<Boolean> =
        walletOptionsState.walletSettingsSource.map { it.countryCode == "US" }

    fun getCoinifyPartnerId(): Observable<Int> =
        walletOptionsState.walletOptionsSource.map { it.partners.coinify.partnerId }

    fun getBuyWebviewWalletLink(): String {
        initWalletOptionsReplaySubjects()
        return (walletOptionsState.walletOptionsSource.value!!.buyWebviewWalletLink
            ?: "${explorerUrl}wallet") + "/#/intermediate"
    }

    fun getComRootLink(): String {
        return walletOptionsState.walletOptionsSource.value!!.comRootLink
    }

    private fun xlmExchangeAddresses(): List<String> {
        return walletOptionsState.walletOptionsSource.value?.xmlExchangeAddresses ?: emptyList()
    }

    fun getWalletLink(): String {
        return walletOptionsState.walletOptionsSource.value!!.walletLink
    }

    /**
     * Mobile info retrieved from wallet-options.json based on wallet setting
     */
    fun fetchInfoMessage(locale: Locale): Observable<String> {
        initWalletOptionsReplaySubjects()

        return walletOptionsState.walletOptionsSource.map { options ->
            var result: String

            options.mobileInfo.apply {
                result = getLocalisedMessage(locale, this)
            }
            return@map result
        }
    }

    /**
     * Checks to see if the client app needs to be force updated according to the wallet.options
     * JSON file. If the client is on an unsupported Android SDK, the check is bypassed to prevent
     * locking users out forever. Otherwise, an app version code ([piuk.blockchain.android.BuildConfig.VERSION_CODE])
     * less than the supplied minVersionCode will return true, and the client should be forcibly
     * upgraded.
     *
     * @param versionCode The version code of the current app
     * @param sdk The device's Android SDK version
     * @return A [Boolean] value contained within an [Observable]
     */
    fun checkForceUpgrade(versionName: String): Observable<UpdateType> {
        initWalletOptionsReplaySubjects()

        return walletOptionsState.walletOptionsSource.map {
            val latestApiVersion = SemanticVersion(it.androidUpdate.latestStoreVersion)
            val currentVersion = SemanticVersion(versionName)
            if (latestApiVersion > currentVersion) {
                return@map it.androidUpdate.updateType.toUpdateType()
            } else return@map UpdateType.NONE
        }
    }

    fun getLocalisedMessage(locale: Locale, map: Map<String, String>): String {
        var result = ""

        if (map.isNotEmpty()) {
            val lcid = locale.language + "-" + locale.country
            val language = locale.language

            result = when {
                map.containsKey(language) -> map[language] ?: ""
                // Regional
                map.containsKey(lcid) -> map[lcid] ?: ""
                // Default
                else -> map["en"] ?: ""
            }
        }

        return result
    }

    fun getLastEthTransactionFuse(): Observable<Long> {
        return walletOptionsState.walletOptionsSource
            .map { return@map it.ethereum.lastTxFuse }
    }

    override fun transactionTimeout(): Single<Long> =
        walletOptionsState.walletOptionsSource
            .map { it.xlmTransactionTimeout }
            .first(WalletOptions.XLM_DEFAULT_TIMEOUT_SECS)

    fun isXlmAddressExchange(it: String): Boolean = xlmExchangeAddresses().contains(it.toUpperCase())
}

private fun String.toUpdateType(): UpdateType =
    when {
        equals("RECOMMENDED", true) -> UpdateType.RECOMMENDED
        equals("FORCE", true) -> UpdateType.FORCE
        else -> UpdateType.RECOMMENDED
    }
