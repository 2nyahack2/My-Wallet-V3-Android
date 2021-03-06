package piuk.blockchain.android.ui.kyc.veriffsplash

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity.RESULT_OK
import androidx.core.content.ContextCompat
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment.findNavController
import com.blockchain.koin.scopedInject
import com.blockchain.notifications.analytics.AnalyticsEvents
import com.blockchain.notifications.analytics.KYCAnalyticsEvents
import com.blockchain.notifications.analytics.logEvent
import com.blockchain.nabu.models.responses.nabu.SupportedDocuments
import com.blockchain.ui.extensions.throttledClicks
import com.blockchain.ui.urllinks.URL_BLOCKCHAIN_GOLD_UNAVAILABLE_SUPPORT
import com.blockchain.ui.urllinks.URL_BLOCKCHAIN_KYC_SUPPORTED_COUNTRIES_LIST
import com.blockchain.veriff.VeriffApplicantAndToken
import com.blockchain.veriff.VeriffLauncher
import io.reactivex.Observable
import kotlinx.android.synthetic.main.fragment_kyc_veriff_splash.*
import org.koin.android.ext.android.inject
import piuk.blockchain.android.R
import piuk.blockchain.android.campaign.CampaignType
import piuk.blockchain.android.coincore.AssetAction
import piuk.blockchain.android.ui.customviews.dialogs.MaterialProgressDialog
import piuk.blockchain.android.ui.kyc.navhost.KycProgressListener
import piuk.blockchain.android.ui.transactionflow.DialogFlow
import piuk.blockchain.android.ui.transactionflow.TransactionFlow
import piuk.blockchain.android.util.StringUtils
import piuk.blockchain.androidcore.utils.helperfunctions.unsafeLazy
import piuk.blockchain.androidcoreui.ui.base.BaseFragment
import piuk.blockchain.androidcoreui.ui.base.UiState
import piuk.blockchain.androidcoreui.ui.base.UiState.CONTENT
import piuk.blockchain.androidcoreui.ui.base.UiState.EMPTY
import piuk.blockchain.androidcoreui.ui.base.UiState.FAILURE
import piuk.blockchain.androidcoreui.ui.base.UiState.LOADING
import piuk.blockchain.androidcoreui.ui.customviews.ToastCustom
import piuk.blockchain.androidcoreui.ui.customviews.toast
import piuk.blockchain.android.ui.kyc.ParentActivityDelegate
import piuk.blockchain.android.util.gone
import piuk.blockchain.android.util.goneIf
import piuk.blockchain.android.util.inflate
import piuk.blockchain.android.util.visible
import piuk.blockchain.android.util.visibleIf
import timber.log.Timber

class VeriffSplashFragment : BaseFragment<VeriffSplashView, VeriffSplashPresenter>(),
    VeriffSplashView, DialogFlow.FlowHost {

    private val presenter: VeriffSplashPresenter by scopedInject()
    private val stringUtils: StringUtils by inject()
    private val progressListener: KycProgressListener by ParentActivityDelegate(
        this
    )
    override val countryCode by unsafeLazy {
        VeriffSplashFragmentArgs.fromBundle(arguments ?: Bundle()).countryCode
    }
    private var progressDialog: MaterialProgressDialog? = null

    override val nextClick: Observable<Unit>
        get() = btn_next.throttledClicks()

    override val swapClick: Observable<Unit>
        get() = btn_goto_swap.throttledClicks()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = container?.inflate(R.layout.fragment_kyc_veriff_splash)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        logEvent(AnalyticsEvents.KycVerifyIdentity)

        checkCameraPermissions()
        setupTextLinks()
        onViewReady()
    }

    private fun setupTextLinks() {

        val linksMap = mapOf<String, Uri>(
            "gold_error" to Uri.parse(URL_BLOCKCHAIN_GOLD_UNAVAILABLE_SUPPORT),
            "country_list" to Uri.parse(URL_BLOCKCHAIN_KYC_SUPPORTED_COUNTRIES_LIST)
        )

        // On the content view:
        val countriesText = stringUtils.getStringWithMappedAnnotations(
            R.string.kyc_veriff_splash_country_supported_subheader,
            linksMap,
            requireActivity()
        )

        text_supported_countries.text = countriesText
        text_supported_countries.movementMethod = LinkMovementMethod.getInstance()

        // On the error view:
        val supportText = stringUtils.getStringWithMappedAnnotations(
            R.string.kyc_gold_unavailable_text_support,
            linksMap,
            requireActivity()
        )
        text_action.text = supportText
        text_action.movementMethod = LinkMovementMethod.getInstance()
    }

    private fun checkCameraPermissions() {
        val granted = ContextCompat.checkSelfPermission(
            requireActivity(),
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        text_view_veriff_splash_enable_camera_title.goneIf(granted)
        text_view_veriff_splash_enable_camera_body.goneIf(granted)
    }

    override fun showProgressDialog(cancelable: Boolean) {
        progressDialog = MaterialProgressDialog(requireContext()).apply {
            setOnCancelListener { presenter.onProgressCancelled() }
            setMessage(R.string.kyc_country_selection_please_wait)
            setCancelable(cancelable)
            show()
        }
    }

    override fun dismissProgressDialog() {
        progressDialog?.apply { dismiss() }
        progressDialog = null
    }

    @SuppressLint("InflateParams")
    override fun continueToVeriff(applicant: VeriffApplicantAndToken) {
        launchVeriff(applicant)
        logEvent(KYCAnalyticsEvents.VeriffInfoStarted)
    }

    private fun launchVeriff(applicant: VeriffApplicantAndToken) {
        VeriffLauncher().launchVeriff(requireActivity(), applicant, REQUEST_CODE_VERIFF)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_CODE_VERIFF) {
            Timber.d("Veriff result code $resultCode")
            if (resultCode == RESULT_OK) {
                presenter.submitVerification()
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    override fun showError(message: Int) =
        toast(message, ToastCustom.TYPE_ERROR)

    override fun continueToCompletion() {
        val navOptions = NavOptions.Builder()
            .setPopUpTo(R.id.kyc_nav_xml, true)
            .build()
        findNavController(this).navigate(R.id.applicationCompleteFragment, null, navOptions)
    }

    override fun continueToSwap() {
        TransactionFlow(
            action = AssetAction.Swap
        ).apply {
            startFlow(
                fragmentManager = childFragmentManager,
                host = this@VeriffSplashFragment
            )
        }
    }

    override fun createPresenter(): VeriffSplashPresenter = presenter

    override fun getMvpView(): VeriffSplashView = this

    override fun supportedDocuments(documents: List<SupportedDocuments>) {
        val makeVisible = { id: Int -> view?.findViewById<View>(id)?.visible() }
        documents.forEach {
            when (it) {
                SupportedDocuments.PASSPORT -> makeVisible(R.id.text_view_document_passport)
                SupportedDocuments.DRIVING_LICENCE -> makeVisible(R.id.text_view_document_drivers_license)
                SupportedDocuments.NATIONAL_IDENTITY_CARD -> makeVisible(R.id.text_view_document_id_card)
                SupportedDocuments.RESIDENCE_PERMIT -> makeVisible(R.id.text_view_document_residence_permit)
            }
        }
    }

    override fun setUiState(@UiState.UiStateDef state: Int) {
        when (state) {
            LOADING -> showLoadingState()
            CONTENT -> showContentState()
            FAILURE -> showErrorState()
            EMPTY -> showEmptyState()
        }
    }

    private fun showLoadingState() {
        showProgressDialog(cancelable = false)
        error_layout.gone()
        content_view.gone()
        loading_view.visible()
    }

    private fun showContentState() {
        dismissProgressDialog()
        progressListener.setHostTitle(R.string.kyc_veriff_splash_title)
        error_layout.gone()
        content_view.visible()
        loading_view.gone()
    }

    private fun showErrorState() {
        dismissProgressDialog()
        progressListener.setHostTitle(R.string.kyc_veriff_splash_error_silver)
        error_layout.visible()
        content_view.gone()
        loading_view.gone()
        btn_goto_swap.visibleIf {
            progressListener.campaignType != CampaignType.SimpleBuy
        }
    }

    private fun showEmptyState() {
        throw IllegalStateException("UiState == EMPTY. This should never happen")
    }

    override fun onFlowFinished() {
    }

    companion object {
        private const val REQUEST_CODE_VERIFF = 1440
    }
}