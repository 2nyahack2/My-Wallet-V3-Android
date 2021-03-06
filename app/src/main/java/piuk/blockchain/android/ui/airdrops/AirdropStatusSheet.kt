package piuk.blockchain.android.ui.airdrops

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.net.Uri
import android.view.View
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.blockchain.extensions.exhaustive
import com.blockchain.koin.scopedInject
import com.blockchain.ui.urllinks.STX_STACKS_LEARN_MORE
import kotlinx.android.synthetic.main.dialog_airdrop_status.view.*
import piuk.blockchain.android.R
import piuk.blockchain.android.campaign.blockstackCampaignName
import piuk.blockchain.android.campaign.sunriverCampaignName
import piuk.blockchain.android.ui.base.SlidingModalBottomDialog
import piuk.blockchain.androidcore.utils.helperfunctions.unsafeLazy
import piuk.blockchain.android.util.gone
import piuk.blockchain.android.util.goneIf
import piuk.blockchain.android.util.visible
import java.lang.IllegalStateException
import java.text.DateFormat

class AirdropStatusSheet : SlidingModalBottomDialog(), AirdropCentreView {

    private val presenter: AirdropCentrePresenter by scopedInject()

    private val airdropName: String by unsafeLazy {
        arguments?.getString(ARG_AIRDROP_NAME) ?: blockstackCampaignName
    }

    override val layoutResource: Int = R.layout.dialog_airdrop_status

    override fun initControls(view: View) {
        view.cta_button.setOnClickListener { onCtaClick() }

        presenter.attachView(this)
    }

    @SuppressLint("SetTextI18n")
    override fun renderList(statusList: List<Airdrop>) {

        val airdrop = statusList.find { it.name == airdropName }
            ?: throw IllegalStateException("No $airdropName airdrop found")

        when (airdropName) {
            sunriverCampaignName -> {
                renderSunriver(airdrop)
            }
            blockstackCampaignName -> {
                renderBlockstacks(airdrop)
            }
        }
    }

    private fun renderBlockstacks(airdrop: Airdrop) {
        with(dialogView) {
            title.setText(R.string.airdrop_sheet_stx_title)
            body.setText(R.string.airdrop_sheet_stx_body)
            icon_crypto.setImageResource(R.drawable.ic_logo_stx)
        }

        renderStatus(airdrop)
        renderDate(airdrop)
        renderAmount(airdrop)

        if (airdrop.status == AirdropState.RECEIVED) {
            showSupportInfo(
                R.string.airdrop_sheet_stx_where_are_my_stacks_title,
                R.string.airdrop_sheet_stx_where_are_my_stacks,
                Uri.parse(STX_STACKS_LEARN_MORE)
            )
        }
    }

    private fun renderSunriver(airdrop: Airdrop) {
        with(dialogView) {
            title.setText(R.string.airdrop_sheet_xlm_title)
            body.gone()
            icon_crypto.setImageResource(R.drawable.vector_xlm_colored)
        }
        renderStatus(airdrop)
        renderDate(airdrop)
        renderAmount(airdrop)
    }

    private fun renderStatus(airdrop: Airdrop) {
        when (airdrop.status) {
            AirdropState.UNKNOWN ->
                setStatusView(
                    R.string.airdrop_status_unknown,
                    R.color.black,
                    R.drawable.bkgd_status_unknown
                )
            AirdropState.EXPIRED ->
                setStatusView(
                    R.string.airdrop_status_expired,
                    R.color.grey_600,
                    R.drawable.bkgd_grey_100_rounded
                )
            AirdropState.PENDING ->
                setStatusView(
                    R.string.airdrop_status_pending,
                    R.color.blue_600,
                    R.drawable.bkgd_status_pending
                )
            AirdropState.RECEIVED ->
                setStatusView(
                    R.string.airdrop_status_received,
                    R.color.green_600,
                    R.drawable.bkgd_green_100_rounded
                )
            AirdropState.REGISTERED -> TODO()
        }.exhaustive
    }

    private fun setStatusView(
        @StringRes message: Int,
        @ColorRes textColour: Int,
        @DrawableRes background: Int
    ) {
        with(dialogView.status_value) {
            setText(message)
            setTextColor(ContextCompat.getColor(context, textColour))
            setBackground(ContextCompat.getDrawable(context, background))
        }
    }

    private fun renderDate(airdrop: Airdrop) {
        airdrop.date?.let {
            val formatted = DateFormat.getDateInstance(DateFormat.SHORT).format(it)
            dialogView.date_value.text = formatted
        }
    }

    @SuppressLint("SetTextI18n")
    private fun renderAmount(airdrop: Airdrop) {

        val amount = if (airdrop.amountCrypto != null) {
            "${airdrop.amountCrypto.toStringWithSymbol()} (${airdrop.amountFiat?.toStringWithSymbol()})"
        } else {
            ""
        }

        with(dialogView) {
            amount_value.text = amount
            amount_label.goneIf(amount.isEmpty())
            amount_value.goneIf(amount.isEmpty())
            divider_amount.goneIf(amount.isEmpty())
        }
    }

    @Suppress("SameParameterValue")
    private fun showSupportInfo(@StringRes title: Int, @StringRes message: Int, link: Uri) {
        with(dialogView) {
            support_heading.setText(title)
            support_heading.visible()

            support_message.setText(message)
            support_message.visible()

            support_link.setOnClickListener {
                context?.startActivity(Intent(Intent.ACTION_VIEW, link))
            }
            support_link.visible()
        }
    }

    override fun onSheetHidden() {
        super.onSheetHidden()
        presenter.detachView(this)
        dialog?.let {
            onCancel(it)
        }
    }

    private fun onCtaClick() = dismiss()

    companion object {
        private const val ARG_AIRDROP_NAME = "AIRDROP_NAME"

        fun newInstance(airdropName: String): AirdropStatusSheet {
            return AirdropStatusSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_AIRDROP_NAME, airdropName)
                }
            }
        }
    }

    override fun renderListUnavailable() {
        dismiss()
    }

    override fun showProgressDialog(messageId: Int, onCancel: (() -> Unit)?) {}
    override fun dismissProgressDialog() {}
}
