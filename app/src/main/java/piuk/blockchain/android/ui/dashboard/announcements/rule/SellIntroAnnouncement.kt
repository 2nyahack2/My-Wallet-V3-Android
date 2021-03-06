package piuk.blockchain.android.ui.dashboard.announcements.rule

import androidx.annotation.VisibleForTesting
import com.blockchain.notifications.analytics.Analytics
import com.blockchain.nabu.datamanagers.EligibilityProvider
import io.reactivex.Single
import io.reactivex.rxkotlin.Singles
import piuk.blockchain.android.R
import piuk.blockchain.android.coincore.Coincore
import piuk.blockchain.android.coincore.FiatAccount
import piuk.blockchain.android.coincore.InterestAccount
import piuk.blockchain.android.ui.dashboard.announcements.AnnouncementHost
import piuk.blockchain.android.ui.dashboard.announcements.AnnouncementRule
import piuk.blockchain.android.ui.dashboard.announcements.DismissRecorder
import piuk.blockchain.android.ui.dashboard.announcements.DismissRule
import piuk.blockchain.android.ui.dashboard.announcements.StandardAnnouncementCard
import piuk.blockchain.android.ui.sell.SellAnalytics

class SellIntroAnnouncement(
    dismissRecorder: DismissRecorder,
    private val eligibilityProvider: EligibilityProvider,
    private val coincore: Coincore,
    private val analytics: Analytics
) : AnnouncementRule(dismissRecorder) {

    override val dismissKey = DISMISS_KEY

    override fun shouldShow(): Single<Boolean> {
        if (dismissEntry.isDismissed) {
            return Single.just(false)
        }

        return Singles.zip(
            eligibilityProvider.isEligibleForSimpleBuy(),
            coincore.allWallets().map { acg ->
                acg.accounts.filterNot { it is InterestAccount || it is FiatAccount }
            }.map { list ->
                list.any {
                    it.isFunded
                }
            }
        ) { eligible, fundedAccount ->
            eligible && fundedAccount
        }
    }

    override fun show(host: AnnouncementHost) {
        host.showAnnouncementCard(
            card = StandardAnnouncementCard(
                name = name,
                dismissRule = DismissRule.CardOneTime,
                dismissEntry = dismissEntry,
                titleText = R.string.sell_announcement_title,
                bodyText = R.string.sell_announcement_description,
                ctaText = R.string.sell_announcement_action,
                iconImage = R.drawable.ic_sell_minus,
                ctaFunction = {
                    analytics.logEvent(SellAnalytics.SellIntroCta)
                    host.dismissAnnouncementCard()
                    host.startSell()
                },
                dismissFunction = {
                    host.dismissAnnouncementCard()
                }
            )
        )
    }

    override val name = "sell_intro"

    companion object {
        @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
        const val DISMISS_KEY = "SellIntroAnnouncement_DISMISSED"
    }
}