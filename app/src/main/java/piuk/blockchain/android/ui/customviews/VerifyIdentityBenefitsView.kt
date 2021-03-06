package piuk.blockchain.android.ui.customviews

import android.content.Context
import android.os.Parcelable
import android.util.AttributeSet
import androidx.annotation.DrawableRes
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.android.parcel.Parcelize
import kotlinx.android.synthetic.main.verify_identity_benefits_layout.view.*
import piuk.blockchain.android.R
import piuk.blockchain.android.util.gone
import piuk.blockchain.android.util.visibleIf

class VerifyIdentityBenefitsView(context: Context, attrs: AttributeSet) : ConstraintLayout(context, attrs) {

    init {
        inflate(context, R.layout.verify_identity_benefits_layout, this)
    }

    fun initWithBenefits(
        benefits: List<VerifyIdentityBenefit>,
        title: String,
        description: String,
        @DrawableRes icon: Int,
        primaryButton: ButtonOptions,
        secondaryButton: ButtonOptions,
        footerText: String = "",
        showSheetIndicator: Boolean = true
    ) {
        kyc_benefits_intro_title.text = title
        kyc_benefits_intro_description.text = description
        kyc_benefits_default_symbol.setImageResource(icon)
        kyc_benefits_negative_action.visibleIf { secondaryButton.visible }
        kyc_benefits_positive_action.visibleIf { primaryButton.visible }
        kyc_benefits_positive_action.setOnClickListener {
            primaryButton.cta()
        }
        primaryButton.text?.let {
            kyc_benefits_positive_action.text = it
        }

        secondaryButton.text?.let {
            kyc_benefits_negative_action.text = it
        }

        kyc_benefits_negative_action.setOnClickListener {
            secondaryButton.cta()
        }
        footer_text.visibleIf { footerText.isNotEmpty() }
        footer_text.text = footerText

        val adapter = BenefitsAdapter().apply {
            items = benefits
        }

        rv_benefits.layoutManager = LinearLayoutManager(context)
        rv_benefits.adapter = adapter

        if (!showSheetIndicator) {
            kyc_benefits_sheet_indicator.gone()
        }
    }
}

@Parcelize
data class VerifyIdentityBenefit(val title: String, val subtitle: String) : Parcelable
data class ButtonOptions(val visible: Boolean, val text: String? = null, val cta: () -> Unit = {})