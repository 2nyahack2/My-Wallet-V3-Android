package piuk.blockchain.android.ui.dashboard.sheets

import android.view.View
import com.blockchain.koin.scopedInject
import com.blockchain.preferences.CurrencyPrefs
import kotlinx.android.synthetic.main.fiat_funds_no_kyc_details_sheet.view.*
import piuk.blockchain.android.R
import piuk.blockchain.android.ui.base.SlidingModalBottomDialog
import piuk.blockchain.android.ui.customviews.ButtonOptions
import piuk.blockchain.android.ui.customviews.VerifyIdentityBenefit

class FiatFundsNoKycDetailsSheet : SlidingModalBottomDialog() {

    interface Host : SlidingModalBottomDialog.Host {
        fun fiatFundsVerifyIdentityCta()
    }

    private val prefs: CurrencyPrefs by scopedInject()

    override val host: Host by lazy {
        super.host as? Host ?: throw IllegalStateException(
            "Host fragment is not a FiatFundsNoKycDetailsSheet.Host")
    }

    override val layoutResource: Int
        get() = R.layout.fiat_funds_no_kyc_details_sheet

    override fun initControls(view: View) {
        with(view) {
            benefits_view.initWithBenefits(
                benefits = listOf(
                    VerifyIdentityBenefit(
                        getString(R.string.fiat_funds_no_kyc_step_1_title),
                        getString(R.string.fiat_funds_no_kyc_step_1_description)
                    ),
                    VerifyIdentityBenefit(
                        getString(R.string.fiat_funds_no_kyc_step_2_title),
                        getString(R.string.fiat_funds_no_kyc_step_2_description)
                    ),
                    VerifyIdentityBenefit(
                        getString(R.string.fiat_funds_no_kyc_step_3_title),
                        getString(R.string.fiat_funds_no_kyc_step_3_description)
                    )
                ),
                title = getString(R.string.fiat_funds_no_kyc_announcement_title),
                description = getString(R.string.fiat_funds_no_kyc_announcement_description),
                icon = currencyIcon(),
                primaryButton = ButtonOptions(
                    true) {
                    dismiss()
                    host.fiatFundsVerifyIdentityCta()
                },
                secondaryButton = ButtonOptions(
                    true) {
                    dismiss()
                }
            )
        }
    }

    private fun currencyIcon(): Int = when (prefs.selectedFiatCurrency) {
        "EUR" -> R.drawable.ic_funds_euro
        "GBP" -> R.drawable.ic_funds_gbp
        else -> R.drawable.ic_funds_usd // show dollar if currency isn't selected
    }

    companion object {
        fun newInstance(): FiatFundsNoKycDetailsSheet = FiatFundsNoKycDetailsSheet()
    }
}