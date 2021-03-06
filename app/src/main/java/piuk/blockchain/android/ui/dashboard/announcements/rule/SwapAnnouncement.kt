package piuk.blockchain.android.ui.dashboard.announcements.rule

import com.blockchain.nabu.datamanagers.EligibilityProvider
import io.reactivex.Single
import piuk.blockchain.android.R
import piuk.blockchain.android.campaign.CampaignType
import piuk.blockchain.android.ui.dashboard.announcements.AnnouncementHost
import piuk.blockchain.android.ui.dashboard.announcements.AnnouncementQueries
import piuk.blockchain.android.ui.dashboard.announcements.AnnouncementRule
import piuk.blockchain.android.ui.dashboard.announcements.DismissRecorder
import piuk.blockchain.android.ui.dashboard.announcements.DismissRule
import piuk.blockchain.android.ui.dashboard.announcements.StandardAnnouncementCard

class SwapAnnouncement(
    private val queries: AnnouncementQueries,
    private val eligibilityProvider: EligibilityProvider,
    dismissRecorder: DismissRecorder
) : AnnouncementRule(dismissRecorder) {

    private lateinit var announcementType: AnnouncementType

    override val dismissKey: String
        get() = DISMISS_KEY.plus(announcementType.toString())

    override val name: String
        get() = "swap_v2"

    override fun shouldShow(): Single<Boolean> =
        queries.isTier1Or2Verified().flatMap {
            if (!it)
                Single.just(AnnouncementType.PROMO)
            else eligibilityProvider.isEligibleForSimpleBuy().map { eligible ->
                if (eligible) AnnouncementType.ELIGIBLE
                else AnnouncementType.NOT_ELIGIBLE
            }
        }.doOnSuccess {
            announcementType = it
        }.map {
            !dismissEntry.isDismissed
        }

    override fun show(host: AnnouncementHost) {
        val title = when (announcementType) {
            AnnouncementType.PROMO -> R.string.swap_faster_cheaper
            AnnouncementType.NOT_ELIGIBLE -> R.string.swap_better
            AnnouncementType.ELIGIBLE -> R.string.swap_hot
        }

        val body = when (announcementType) {
            AnnouncementType.PROMO -> R.string.swap_upgrade_to_gold
            AnnouncementType.NOT_ELIGIBLE -> R.string.swap_better_experience
            AnnouncementType.ELIGIBLE -> R.string.swap_faster_cheaper_better
        }

        val ctaText = when (announcementType) {
            AnnouncementType.PROMO -> R.string.upgrade_now
            AnnouncementType.ELIGIBLE,
            AnnouncementType.NOT_ELIGIBLE -> R.string.swap_now
        }

        val ctaAction: () -> Unit = when (announcementType) {
            AnnouncementType.PROMO -> {
                {
                    host.startKyc(CampaignType.Swap)
                }
            }
            AnnouncementType.NOT_ELIGIBLE,
            AnnouncementType.ELIGIBLE -> {
                host::startSwap
            }
        }

        host.showAnnouncementCard(
            card = StandardAnnouncementCard(
                name = name,
                dismissRule = DismissRule.CardOneTime,
                dismissEntry = dismissEntry,
                titleText = title,
                bodyText = body,
                ctaText = ctaText,
                iconImage = R.drawable.ic_swap_blue_circle,
                dismissFunction = {
                    host.dismissAnnouncementCard()
                },
                ctaFunction = {
                    ctaAction()
                    host.dismissAnnouncementCard()
                }
            )
        )
    }

    companion object {
        private const val DISMISS_KEY = "SWAP_NEW_ANNOUNCEMENT_DISMISSED"
    }

    private enum class AnnouncementType {
        PROMO, NOT_ELIGIBLE, ELIGIBLE
    }
}