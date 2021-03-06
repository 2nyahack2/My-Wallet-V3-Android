package piuk.blockchain.android.ui.dashboard.announcements.rule

import androidx.annotation.VisibleForTesting
import com.blockchain.notifications.analytics.Analytics
import io.reactivex.Single
import piuk.blockchain.android.R
import piuk.blockchain.android.thepit.PitAnalyticsEvent
import piuk.blockchain.android.thepit.PitLinking
import piuk.blockchain.android.ui.dashboard.announcements.AnnouncementHost
import piuk.blockchain.android.ui.dashboard.announcements.AnnouncementRule
import piuk.blockchain.android.ui.dashboard.announcements.DismissRecorder
import piuk.blockchain.android.ui.dashboard.announcements.DismissRule
import piuk.blockchain.android.ui.dashboard.announcements.StandardAnnouncementCard

class PitAnnouncement(
    private val pitLink: PitLinking,
    dismissRecorder: DismissRecorder,
    private val analytics: Analytics
) : AnnouncementRule(dismissRecorder) {

    override val dismissKey =
        DISMISS_KEY

    override fun shouldShow(): Single<Boolean> {
        if (dismissEntry.isDismissed) {
            return Single.just(false)
        }

        return pitLink.isPitLinked().map { linked ->
            !linked
        }
    }

    override fun show(host: AnnouncementHost) {
        host.showAnnouncementCard(
            card = StandardAnnouncementCard(
                name = name,
                titleText = R.string.the_exchange_announcement_title,
                bodyText = R.string.the_exchange_announcement_body,
                ctaText = R.string.the_exchange_announcement_cta_text,
                iconImage = R.drawable.ic_the_exchange_colour,
                dismissFunction = {
                    host.dismissAnnouncementCard()
                },
                ctaFunction = {
                    analytics.logEvent(PitAnalyticsEvent.AnnouncementTappedEvent)
                    host.dismissAnnouncementCard()
                    host.startPitLinking()
                },
                dismissEntry = dismissEntry,
                dismissRule = DismissRule.CardOneTime
            )
        )
    }

    override val name = "pit_linking"

    companion object {
        @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
        const val DISMISS_KEY = "PitAnnouncement_DISMISSED"
    }
}
