package piuk.blockchain.android.ui.dashboard.announcements.rule

import androidx.annotation.VisibleForTesting
import com.blockchain.notifications.analytics.Analytics
import com.blockchain.notifications.analytics.InterestAnalytics
import io.reactivex.Single
import org.koin.core.KoinComponent
import org.koin.core.inject
import piuk.blockchain.android.R
import piuk.blockchain.android.ui.dashboard.announcements.AnnouncementHost
import piuk.blockchain.android.ui.dashboard.announcements.AnnouncementRule
import piuk.blockchain.android.ui.dashboard.announcements.DismissRecorder
import piuk.blockchain.android.ui.dashboard.announcements.DismissRule
import piuk.blockchain.android.ui.dashboard.announcements.StandardAnnouncementCard

class InterestAvailableAnnouncement(
    dismissRecorder: DismissRecorder
) : AnnouncementRule(dismissRecorder), KoinComponent {

    private val analytics: Analytics by inject()
    override val dismissKey = DISMISS_KEY

    override fun shouldShow(): Single<Boolean> {
        if (dismissEntry.isDismissed) {
            return Single.just(false)
        }

        return Single.just(true)
    }

    override fun show(host: AnnouncementHost) {
        host.showAnnouncementCard(
            card = StandardAnnouncementCard(
                name = name,
                dismissRule = DismissRule.CardOneTime,
                dismissEntry = dismissEntry,
                titleText = R.string.interest_announcement_title,
                bodyText = R.string.interest_announcement_description,
                iconImage = R.drawable.ic_interest_blue_circle,
                ctaText = R.string.interest_announcement_action,
                ctaFunction = {
                    analytics.logEvent(InterestAnalytics.INTEREST_ANNOUNCEMENT_CTA)
                    host.dismissAnnouncementCard()
                    host.startInterestDashboard()
                },
                dismissFunction = {
                    host.dismissAnnouncementCard()
                }
            )
        )
    }

    override val name = "interest_available"

    companion object {
        @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
        const val DISMISS_KEY = "InterestAvailableAnnouncement_DISMISSED"
    }
}