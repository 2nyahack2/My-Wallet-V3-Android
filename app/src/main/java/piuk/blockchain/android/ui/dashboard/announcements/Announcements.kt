package piuk.blockchain.android.ui.dashboard.announcements

import androidx.annotation.VisibleForTesting
import info.blockchain.balance.CryptoCurrency
import io.reactivex.Maybe
import io.reactivex.Observable
import io.reactivex.Scheduler
import io.reactivex.Single
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.rxkotlin.subscribeBy
import piuk.blockchain.android.campaign.CampaignType
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean

interface AnnouncementHost {
    val disposables: CompositeDisposable

    fun showAnnouncementCard(card: AnnouncementCard)
    fun dismissAnnouncementCard()
    fun startSwap()
    // Actions
    fun startKyc(campaignType: CampaignType)

    fun startPitLinking()
    fun startFundsBackup()
    fun startSetup2Fa()
    fun startVerifyEmail()
    fun startEnableFingerprintLogin()
    fun startIntroTourGuide()
    fun startTransferCrypto()

    fun startStxReceivedDetail()
    fun finishSimpleBuySignup()
    fun startSimpleBuy(cryptoCurrency: CryptoCurrency = CryptoCurrency.BTC)
    fun startInterestDashboard()
    fun startSell()

    fun showFiatFundsKyc()
    fun showBankLinking()
    fun openBrowserLink(url: String)
}

abstract class AnnouncementRule(private val dismissRecorder: DismissRecorder) {

    protected val dismissEntry by lazy { dismissRecorder[dismissKey] }

    abstract val dismissKey: String
    abstract val name: String

    abstract fun shouldShow(): Single<Boolean>
    abstract fun show(host: AnnouncementHost)
    fun isDismissed(): Boolean = dismissEntry.isDismissed
}

class AnnouncementList(
    private val mainScheduler: Scheduler,
    private val orderAdapter: AnnouncementConfigAdapter,
    private val availableAnnouncements: List<AnnouncementRule>,
    private val dismissRecorder: DismissRecorder
) {
    // Hack to block announcements until metadata/simple buy etc is initialised.
    // TODO: Refactor app startup so we can avoid nonsense like this
    private var isEnabled = AtomicBoolean(false)

    fun enable(): Boolean {
        if (!isEnabled.get()) {
            isEnabled.set(true)
            return true
        }
        return false
    }

    fun checkLatest(host: AnnouncementHost, disposables: CompositeDisposable) {
        host.dismissAnnouncementCard()

        if (isEnabled.get()) {
            disposables += showNextAnnouncement(host)
                .doOnSubscribe { Timber.d("SB Sync: Checking announcements...") }
                .subscribeBy(
                    onComplete = { Timber.d("SB Sync: Announcements checked") },
                    onError = Timber::e
                )
        } else {
            Timber.d("SB Sync: ... Announcements disabled")
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun buildAnnouncementList(order: List<String>): List<AnnouncementRule> {
        return order.mapNotNull { availableAnnouncements.find(it) }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun showNextAnnouncement(host: AnnouncementHost): Maybe<AnnouncementRule> =
        getNextAnnouncement()
            .observeOn(mainScheduler)
            .doOnSuccess { it.show(host) }

    private fun getNextAnnouncement(): Maybe<AnnouncementRule> =
        orderAdapter.announcementConfig
            .doOnSuccess { dismissRecorder.setPeriod(it.interval) }
            .map { buildAnnouncementList(it.order) }
            .flattenAsObservable { it }
            .concatMap { a ->
                Observable.defer {
                    a.shouldShow()
                        .filter { it }
                        .map { a }
                        .toObservable()
                }
            }
            .firstElement()

    internal fun dismissKeys(): List<String> = availableAnnouncements.map { it.dismissKey }

    private fun List<AnnouncementRule>.find(name: String): AnnouncementRule? =
        this.find { it.name == name }
}
