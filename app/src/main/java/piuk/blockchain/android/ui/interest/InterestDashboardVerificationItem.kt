package piuk.blockchain.android.ui.interest

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.extensions.LayoutContainer
import kotlinx.android.synthetic.main.item_interest_dashboard_verification.view.*
import piuk.blockchain.android.R
import piuk.blockchain.android.ui.adapters.AdapterDelegate
import piuk.blockchain.android.ui.customviews.ButtonOptions
import piuk.blockchain.android.ui.customviews.VerifyIdentityBenefit
import piuk.blockchain.android.util.inflate

class InterestDashboardVerificationItem<in T>(
    private val verificationClicked: () -> Unit
) : AdapterDelegate<T> {
    override fun isForViewType(items: List<T>, position: Int): Boolean {
        val item = items[position] as InterestDashboardItem
        return item is InterestIdentityVerificationItem
    }

    override fun onCreateViewHolder(parent: ViewGroup): RecyclerView.ViewHolder =
        InterestAssetVerificationViewHolder(
            parent.inflate(R.layout.item_interest_dashboard_verification)
        )

    override fun onBindViewHolder(
        items: List<T>,
        position: Int,
        holder: RecyclerView.ViewHolder
    ) = (holder as InterestAssetVerificationViewHolder).bind(
        verificationClicked
    )
}

private class InterestAssetVerificationViewHolder(val parent: View) :
    RecyclerView.ViewHolder(parent),
    LayoutContainer {

    override val containerView: View?
        get() = itemView

    fun bind(
        verificationClicked: () -> Unit
    ) {
        itemView.item_verification.initWithBenefits(
            benefits = listOf(
                VerifyIdentityBenefit(
                    parent.context.getString(R.string.interest_dashboard_verify_point_one_title),
                    parent.context.getString(R.string.interest_dashboard_verify_point_one_label)),
                VerifyIdentityBenefit(
                    parent.context.getString(R.string.interest_dashboard_verify_point_two_title),
                    parent.context.getString(R.string.interest_dashboard_verify_point_two_label)),
                VerifyIdentityBenefit(
                    parent.context.getString(R.string.interest_dashboard_verify_point_three_title),
                    parent.context.getString(R.string.interest_dashboard_verify_point_three_label))
            ),
            title = parent.context.getString(R.string.interest_dashboard_verify_title),
            description = parent.context.getString(R.string.interest_dashboard_verify_label),
            icon = R.drawable.ic_interest_blue_circle,
            primaryButton = ButtonOptions(true, cta = verificationClicked),
            secondaryButton = ButtonOptions(false),
            showSheetIndicator = false
        )
    }
}