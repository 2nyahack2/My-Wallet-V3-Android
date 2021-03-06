package piuk.blockchain.android.ui.customviews

import android.os.Bundle
import android.os.Parcelable
import android.view.View
import kotlinx.android.parcel.Parcelize
import kotlinx.android.synthetic.main.kyc_benefits_bottom_sheet.view.*
import piuk.blockchain.android.R
import piuk.blockchain.android.ui.base.SlidingModalBottomDialog

class KycBenefitsBottomSheet : SlidingModalBottomDialog() {

    interface Host : SlidingModalBottomDialog.Host {
        fun verificationCtaClicked()
    }

    private val benefitsDetails: BenefitsDetails by lazy {
        arguments?.getParcelable(BENEFITS_DETAILS) ?: BenefitsDetails()
    }

    override val host: Host by lazy {
        super.host as? Host ?: throw IllegalStateException(
            "Host fragment is not a KycBenefitsBottomSheet.Host")
    }

    override val layoutResource: Int
        get() = R.layout.kyc_benefits_bottom_sheet

    override fun initControls(view: View) {
        with(view) {
            benefits_view.initWithBenefits(
                benefits = benefitsDetails.listOfBenefits,
                title = benefitsDetails.title,
                description = benefitsDetails.description,
                icon = benefitsDetails.icon,
                primaryButton = ButtonOptions(true) {
                    dismiss()
                    host.verificationCtaClicked()
                },
                secondaryButton = ButtonOptions(true) {
                    dismiss()
                }
            )
        }
    }

    companion object {
        private const val BENEFITS_DETAILS = "BENEFITS_DETAILS"

        fun newInstance(
            details: BenefitsDetails
        ): KycBenefitsBottomSheet = KycBenefitsBottomSheet().apply {
            arguments = Bundle().apply {
                putParcelable(BENEFITS_DETAILS, details)
            }
        }
    }

    @Parcelize
    data class BenefitsDetails(
        val title: String = "",
        val description: String = "",
        val listOfBenefits: List<VerifyIdentityBenefit> = emptyList(),
        val icon: Int = R.drawable.ic_verification_badge
    ) : Parcelable
}