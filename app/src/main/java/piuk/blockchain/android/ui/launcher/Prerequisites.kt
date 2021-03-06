package piuk.blockchain.android.ui.launcher

import com.blockchain.logging.CrashLogger
import info.blockchain.wallet.api.data.Settings
import io.reactivex.Completable
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import piuk.blockchain.android.coincore.Coincore
import piuk.blockchain.android.simplebuy.SimpleBuySyncFactory
import piuk.blockchain.android.ui.home.models.MetadataEvent
import piuk.blockchain.androidcore.data.auth.metadata.WalletCredentialsMetadataUpdater
import piuk.blockchain.androidcore.data.metadata.MetadataManager
import piuk.blockchain.androidcore.data.rxjava.RxBus
import piuk.blockchain.androidcore.data.settings.SettingsDataManager
import piuk.blockchain.androidcore.utils.extensions.then

class Prerequisites(
    private val metadataManager: MetadataManager,
    private val settingsDataManager: SettingsDataManager,
    private val coincore: Coincore,
    private val crashLogger: CrashLogger,
    private val simpleBuySync: SimpleBuySyncFactory,
    private val walletCredentialsUpdater: WalletCredentialsMetadataUpdater,
    private val rxBus: RxBus
) {

    fun initMetadataAndRelatedPrerequisites(): Completable =
        metadataManager.attemptMetadataSetup().logOnError(METADATA_ERROR_MESSAGE)
            .then { simpleBuySync.performSync().logAndCompleteOnError(SIMPLE_BUY_SYNC) }
            .then { coincore.init() } // Coincore signals the crash logger internally
            .then { walletCredentialsUpdater.checkAndUpdate().logAndCompleteOnError(WALLET_CREDENTIALS) }
            .doOnComplete {
                rxBus.emitEvent(MetadataEvent::class.java, MetadataEvent.SETUP_COMPLETE)
            }.subscribeOn(Schedulers.io())

    private fun Completable.logOnError(tag: String): Completable =
        this.doOnError {
            crashLogger.logException(
                CustomLogMessagedException(tag, it)
            )
        }

    private fun Completable.logAndCompleteOnError(tag: String): Completable =
        this.logOnError(tag).onErrorComplete()

    fun initSettings(guid: String, sharedKey: String): Single<Settings> =
        settingsDataManager.initSettings(
            guid,
            sharedKey
        ).singleOrError()

    fun decryptAndSetupMetadata(secondPassword: String) = metadataManager.decryptAndSetupMetadata(
        secondPassword
    )

    companion object {
        private const val METADATA_ERROR_MESSAGE = "metadata_init"
        private const val SIMPLE_BUY_SYNC = "simple_buy_sync"
        private const val WALLET_CREDENTIALS = "wallet_credentials"
    }
}