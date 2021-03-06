package piuk.blockchain.android.ui.dashboard.announcements.rule

import androidx.annotation.VisibleForTesting
import com.blockchain.remoteconfig.FeatureFlag
import info.blockchain.balance.CryptoCurrency
import io.reactivex.Single
import piuk.blockchain.android.R
import piuk.blockchain.android.ui.dashboard.announcements.AnnouncementHost
import piuk.blockchain.android.ui.dashboard.announcements.AnnouncementRule
import piuk.blockchain.android.ui.dashboard.announcements.DismissRecorder
import piuk.blockchain.android.ui.dashboard.announcements.DismissRule
import piuk.blockchain.android.ui.dashboard.announcements.StandardAnnouncementCard

class WDGLDAvailableAnnouncement(
    dismissRecorder: DismissRecorder,
    private val dgldFeatureFlag: FeatureFlag
) : AnnouncementRule(dismissRecorder) {

    private var cta: (AnnouncementHost) -> Unit = {}
    override val dismissKey = DISMISS_KEY

    override fun shouldShow(): Single<Boolean> {
        if (dismissEntry.isDismissed) {
            return Single.just(false)
        }

        return dgldFeatureFlag.enabled
    }

    override fun show(host: AnnouncementHost) {
        host.showAnnouncementCard(
            card = StandardAnnouncementCard(
                name = name,
                dismissRule = DismissRule.CardOneTime,
                dismissEntry = dismissEntry,
                titleText = R.string.wdgld_available_card_title,
                bodyText = R.string.wdgld_available_card_body,
                ctaText = R.string.wdgld_available_card_cta,
                iconImage = R.drawable.vector_dgld_colored,
                dismissFunction = {
                    host.dismissAnnouncementCard()
                },
                ctaFunction = {
                    host.dismissAnnouncementCard()
                    host.startSimpleBuy(CryptoCurrency.DGLD)
                }
            )
        )
    }

    override val name = "wdgld_available"

    companion object {
        @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
        const val DISMISS_KEY = "WDGLDAvailableAnnouncement_DISMISSED"
    }
}
