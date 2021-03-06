package piuk.blockchain.android.ui.settings.preferences

import android.content.Context
import androidx.core.content.ContextCompat
import android.util.AttributeSet
import android.widget.TextView
import com.blockchain.nabu.models.responses.nabu.KycTierLevel
import com.blockchain.nabu.models.responses.nabu.KycTiers
import piuk.blockchain.android.R

class KycStatusPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = R.attr.preferenceStyle,
    defStyleRes: Int = 0
) : BaseStatusPreference<KycTiers>(context, attrs, defStyleAttr, defStyleRes) {

    override val defaultValue = KycTiers.default()

    override fun doUpdateValue(value: KycTiers, view: TextView) {
        view.apply {
            text = when {
                value.isRejectedFor(KycTierLevel.GOLD) -> context.getString(R.string.kyc_settings_tier_status_failed)
                value.isApprovedFor(KycTierLevel.GOLD) -> context.getString(R.string.kyc_settings_gold_level_approved)
                value.isPendingFor(KycTierLevel.GOLD) || value.isUnderReviewFor(KycTierLevel.GOLD) ->
                    context.getString(R.string.kyc_settings_gold_level_in_review)
                value.isRejectedFor(KycTierLevel.SILVER) -> context.getString(R.string.kyc_settings_tier_status_failed)
                value.isApprovedFor(KycTierLevel.SILVER) ->
                    context.getString(R.string.kyc_settings_silver_level_approved)
                value.isPendingFor(KycTierLevel.SILVER) || value.isUnderReviewFor(KycTierLevel.SILVER) ->
                    context.getString(R.string.kyc_settings_silver_level_in_review)
                value.isInInitialState() -> context.getString(R.string.kyc_settings_tier_status_locked)
                else -> ""
            }

            val background = when {
                value.isRejectedFor(KycTierLevel.GOLD) -> R.drawable.pref_status_bkgrd_failed
                value.isApprovedFor(KycTierLevel.GOLD) -> R.drawable.pref_status_bkgrd_complete
                value.isPendingFor(KycTierLevel.GOLD) || value.isUnderReviewFor(KycTierLevel.GOLD) ->
                    R.drawable.pref_status_bkgrd_orange

                value.isRejectedFor(KycTierLevel.SILVER) -> R.drawable.pref_status_bkgrd_failed
                value.isApprovedFor(KycTierLevel.SILVER) -> R.drawable.pref_status_bkgrd_complete
                value.isPendingFor(KycTierLevel.SILVER) || value.isUnderReviewFor(KycTierLevel.SILVER) ->
                    R.drawable.pref_status_bkgrd_orange
                else -> 0
            }
            val foreground = when {
                value.isInInitialState() -> R.color.kyc_progress_text_blue
                else -> R.color.kyc_progress_text_white
            }
            setBackgroundResource(background)
            setTextColor(ContextCompat.getColor(context, foreground))
        }
    }
}
