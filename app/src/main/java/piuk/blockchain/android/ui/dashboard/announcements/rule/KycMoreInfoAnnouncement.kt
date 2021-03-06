package piuk.blockchain.android.ui.dashboard.announcements.rule

import androidx.annotation.VisibleForTesting
import com.blockchain.remoteconfig.FeatureFlag
import com.blockchain.nabu.models.responses.nabu.KycTierLevel
import com.blockchain.nabu.service.TierService
import io.reactivex.Single
import piuk.blockchain.android.R
import piuk.blockchain.android.ui.dashboard.announcements.AnnouncementHost
import piuk.blockchain.android.ui.dashboard.announcements.AnnouncementRule
import piuk.blockchain.android.ui.dashboard.announcements.DismissRecorder
import piuk.blockchain.android.ui.dashboard.announcements.DismissRule
import piuk.blockchain.android.campaign.CampaignType
import piuk.blockchain.android.ui.dashboard.announcements.StandardAnnouncementCard

internal class KycMoreInfoAnnouncement(
    private val tierService: TierService,
    private val showPopupFeatureFlag: FeatureFlag,
    dismissRecorder: DismissRecorder
) : AnnouncementRule(dismissRecorder) {

    override val dismissKey =
        DISMISS_KEY

    override fun shouldShow(): Single<Boolean> {
        if (dismissEntry.isDismissed) {
            return Single.just(false)
        }

        return Single.merge(
            didNotStartGoldLevelKyc(),
            showPopupFeatureFlag.enabled
        ).all { it }
    }

    private fun didNotStartGoldLevelKyc(): Single<Boolean> =
        tierService.tiers().map {
            it.isNotInitialisedFor(KycTierLevel.GOLD)
        }

    override fun show(host: AnnouncementHost) {
        host.showAnnouncementCard(
            StandardAnnouncementCard(
                name = name,
                titleText = R.string.kyc_more_info_title,
                bodyText = R.string.kyc_more_info_body,
                ctaText = R.string.kyc_more_info_cta,
                iconImage = R.drawable.ic_announce_kyc,
                dismissFunction = {
                    host.dismissAnnouncementCard()
                },
                ctaFunction = {
                    host.dismissAnnouncementCard()
                    host.startKyc(CampaignType.Sunriver)
                },
                dismissEntry = dismissEntry,
                dismissRule = DismissRule.CardOneTime
            )
        )
    }

    override val name = "kyc_more_info"

    companion object {
        @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
        const val DISMISS_KEY = "CoinifyKycModalPopup_DISMISSED"
    }
}
