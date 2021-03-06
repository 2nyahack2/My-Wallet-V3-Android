package piuk.blockchain.android.ui.kyc.status

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.constraintlayout.widget.ConstraintSet
import com.blockchain.extensions.px
import com.blockchain.koin.scopedInject
import com.blockchain.notifications.analytics.AnalyticsEvents
import com.blockchain.notifications.analytics.logEvent
import com.blockchain.nabu.models.responses.nabu.KycTierState
import piuk.blockchain.android.R
import piuk.blockchain.android.campaign.CampaignType
import piuk.blockchain.android.coincore.AssetAction
import piuk.blockchain.android.ui.customviews.dialogs.MaterialProgressDialog
import piuk.blockchain.android.ui.transactionflow.DialogFlow
import piuk.blockchain.android.ui.transactionflow.TransactionFlow
import piuk.blockchain.androidcore.utils.helperfunctions.consume
import piuk.blockchain.androidcore.utils.helperfunctions.unsafeLazy
import piuk.blockchain.androidcoreui.ui.base.BaseMvpActivity
import piuk.blockchain.androidcoreui.ui.customviews.ToastCustom
import piuk.blockchain.androidcoreui.ui.customviews.toast
import piuk.blockchain.androidcoreui.utils.extensions.getResolvedColor
import piuk.blockchain.androidcoreui.utils.extensions.getResolvedDrawable
import piuk.blockchain.android.util.gone
import piuk.blockchain.android.util.visible
import kotlinx.android.synthetic.main.activity_kyc_status.button_kyc_status_next as buttonNext
import kotlinx.android.synthetic.main.activity_kyc_status.constraint_layout_kyc_status as constraintLayout
import kotlinx.android.synthetic.main.activity_kyc_status.image_view_kyc_status as imageView
import kotlinx.android.synthetic.main.activity_kyc_status.text_view_kyc_status_no_thanks as buttonNoThanks
import kotlinx.android.synthetic.main.activity_kyc_status.text_view_verification_message as textViewMessage
import kotlinx.android.synthetic.main.activity_kyc_status.text_view_verification_state as textViewStatus
import kotlinx.android.synthetic.main.activity_kyc_status.text_view_verification_subtitle as textViewSubtitle
import kotlinx.android.synthetic.main.activity_kyc_status.toolbar_kyc as toolBar

class KycStatusActivity : BaseMvpActivity<KycStatusView, KycStatusPresenter>(), KycStatusView, DialogFlow.FlowHost {

    private val presenter: KycStatusPresenter by scopedInject()
    private val campaignType by unsafeLazy { intent.getSerializableExtra(EXTRA_CAMPAIGN_TYPE) as CampaignType }
    private var progressDialog: MaterialProgressDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_kyc_status)
        logEvent(AnalyticsEvents.KycComplete)

        val title = when (campaignType) {
            CampaignType.Swap -> R.string.kyc_splash_title
            CampaignType.Sunriver,
            CampaignType.Blockstack,
            CampaignType.SimpleBuy,
            CampaignType.Resubmission,
            CampaignType.FiatFunds,
            CampaignType.Interest -> R.string.sunriver_splash_title
        }
        setupToolbar(toolBar, title)

        onViewReady()
    }

    override fun startExchange() {
        startSwapFlow()
    }

    private fun startSwapFlow() {
        TransactionFlow(
            action = AssetAction.Swap
        ).apply {
            startFlow(
                fragmentManager = supportFragmentManager,
                host = this@KycStatusActivity
            )
        }
    }

    override fun renderUi(kycState: KycTierState) {
        when (kycState) {
            KycTierState.Pending -> onPending()
            KycTierState.UnderReview -> onInReview()
            KycTierState.Expired, KycTierState.Rejected -> onFailed()
            KycTierState.Verified -> onVerified()
            KycTierState.None -> throw IllegalStateException(
                "Users who haven't started KYC should not be able to access this page"
            )
        }
    }

    private fun onPending() {
        imageView.setImageDrawable(
            getResolvedDrawable(
                if (campaignType == CampaignType.Sunriver) {
                    R.drawable.vector_xlm_check
                } else {
                    R.drawable.vector_in_progress
                }
            )
        )
        textViewSubtitle.visible()
        textViewStatus.setTextColor(getResolvedColor(R.color.kyc_in_progress))
        textViewStatus.setText(R.string.kyc_status_title_in_progress)
        displayNotificationButton()
        val message = when (campaignType) {
            CampaignType.Swap,
            CampaignType.Resubmission -> R.string.kyc_status_message_in_progress
            CampaignType.Blockstack,
            CampaignType.SimpleBuy,
            CampaignType.Sunriver,
            CampaignType.FiatFunds,
            CampaignType.Interest -> R.string.sunriver_status_message
        }
        textViewMessage.setText(message)
    }

    private fun onInReview() {
        imageView.setImageDrawable(getResolvedDrawable(R.drawable.vector_in_progress))
        textViewStatus.setTextColor(getResolvedColor(R.color.kyc_in_progress))
        textViewStatus.setText(R.string.kyc_status_title_in_review)
        textViewMessage.setText(R.string.kyc_status_message_under_review)
        displayNotificationButton()
    }

    private fun displayNotificationButton() {
        buttonNext.apply {
            setText(R.string.kyc_status_button_notify_me)
            setOnClickListener { presenter.onClickNotifyUser() }
            visible()
        }
        buttonNoThanks.apply {
            visible()
            setOnClickListener { finish() }
        }
    }

    private fun onFailed() {
        imageView.setImageDrawable(getResolvedDrawable(R.drawable.vector_failed))
        textViewStatus.setTextColor(getResolvedColor(R.color.product_red_medium))
        textViewStatus.setText(R.string.kyc_status_title_failed)
        textViewMessage.setText(R.string.kyc_status_message_failed)
        buttonNext.gone()
    }

    private fun onVerified() {
        imageView.setImageDrawable(getResolvedDrawable(R.drawable.vector_verified))
        textViewStatus.setTextColor(getResolvedColor(R.color.kyc_progress_green))
        textViewStatus.setText(R.string.kyc_settings_status_verified)
        textViewMessage.setText(R.string.kyc_status_message_verified)
        buttonNext.apply {
            setText(R.string.kyc_status_button_get_started)
            setOnClickListener { presenter.onClickContinue() }
            ConstraintSet().apply {
                clone(constraintLayout)
                setMargin(
                    R.id.button_kyc_status_next,
                    ConstraintSet.BOTTOM,
                    32.px
                )
                applyTo(constraintLayout)
            }

            visible()
        }
    }

    override fun showToast(message: Int) {
        toast(message, ToastCustom.TYPE_OK)
    }

    override fun showNotificationsEnabledDialog() {
        AlertDialog.Builder(this, R.style.AlertDialogStyle)
            .setTitle(R.string.kyc_status_button_notifications_enabled_title)
            .setMessage(R.string.kyc_status_button_notifications_enabled_message)
            .setPositiveButton(android.R.string.ok, null)
            .setOnDismissListener { finish() }
            .show()
    }

    override fun showProgressDialog() {
        progressDialog = MaterialProgressDialog(this).apply {
            setOnCancelListener { presenter.onProgressCancelled() }
            setMessage(R.string.kyc_country_selection_please_wait)
            show()
        }
    }

    override fun dismissProgressDialog() {
        progressDialog?.apply { dismiss() }
        progressDialog = null
    }

    override fun finishPage() {
        toast(R.string.kyc_status_error, ToastCustom.TYPE_ERROR)
        finish()
    }

    override fun onSupportNavigateUp(): Boolean = consume { finish() }

    override fun createPresenter(): KycStatusPresenter = presenter

    override fun getView(): KycStatusView = this

    companion object {

        private const val EXTRA_CAMPAIGN_TYPE =
            "info.blockchain.wallet.ui.BalanceFragment.EXTRA_CAMPAIGN_TYPE"

        @JvmStatic
        fun start(context: Context, campaignType: CampaignType) {
            Intent(context, KycStatusActivity::class.java)
                .apply { putExtra(EXTRA_CAMPAIGN_TYPE, campaignType) }
                .run { context.startActivity(this) }
        }
    }

    override fun onFlowFinished() {
    }
}
