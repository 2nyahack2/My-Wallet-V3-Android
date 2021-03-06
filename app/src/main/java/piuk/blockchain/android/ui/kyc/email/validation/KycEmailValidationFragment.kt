package piuk.blockchain.android.ui.kyc.email.validation

import android.net.Uri
import android.os.Bundle
import android.text.method.LinkMovementMethod
import androidx.appcompat.app.AlertDialog
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.blockchain.koin.scopedInject
import com.blockchain.notifications.analytics.Analytics
import com.blockchain.notifications.analytics.logEvent
import piuk.blockchain.android.ui.kyc.navhost.KycProgressListener
import piuk.blockchain.android.ui.kyc.navigate
import com.blockchain.notifications.analytics.AnalyticsEvents
import com.blockchain.notifications.analytics.KYCAnalyticsEvents
import com.blockchain.ui.extensions.throttledClicks
import io.reactivex.Observable
import io.reactivex.rxkotlin.Observables
import io.reactivex.subjects.PublishSubject
import org.koin.android.ext.android.inject
import piuk.blockchain.android.R
import piuk.blockchain.androidcore.utils.helperfunctions.unsafeLazy
import piuk.blockchain.androidcoreui.ui.base.BaseMvpFragment
import piuk.blockchain.android.ui.customviews.dialogs.MaterialProgressDialog
import piuk.blockchain.android.util.StringUtils
import piuk.blockchain.android.ui.kyc.ParentActivityDelegate
import piuk.blockchain.android.util.ViewUtils
import piuk.blockchain.android.util.goneIf
import piuk.blockchain.android.util.inflate
import kotlinx.android.synthetic.main.fragment_kyc_email_validation.button_kyc_email_validation_next as buttonNext
import kotlinx.android.synthetic.main.fragment_kyc_email_validation.text_view_email as textViewEmail
import kotlinx.android.synthetic.main.fragment_kyc_email_validation.text_view_resend_prompt as textViewResend

class KycEmailValidationFragment :
    BaseMvpFragment<KycEmailValidationView, KycEmailValidationPresenter>(),
    KycEmailValidationView {

    private val presenter: KycEmailValidationPresenter by scopedInject()
    private val analytics: Analytics by inject()
    private val stringUtils: StringUtils by inject()
    private val progressListener: KycProgressListener by ParentActivityDelegate(
        this
    )
    private var progressDialog: MaterialProgressDialog? = null
    private val email by unsafeLazy {
        KycEmailValidationFragmentArgs.fromBundle(
            arguments ?: Bundle()
        ).email
    }

    private val resend = PublishSubject.create<Unit>()

    override val uiStateObservable: Observable<Pair<String, Unit>> by unsafeLazy {
        Observables.combineLatest(
            Observable.just(email),
            resend.throttledClicks()
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = container?.inflate(R.layout.fragment_kyc_email_validation)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        logEvent(AnalyticsEvents.KycEmail)
        progressListener.setHostTitle(R.string.kyc_email_title)
        textViewEmail.text = email

        val linksMap = mapOf<String, Uri?>(
            "send_again" to null
        )

        textViewResend.text =
            stringUtils.getStringWithMappedAnnotations(
                R.string.kyc_email_did_not_see_email,
                linksMap,
                requireActivity()
            ) { resend.onNext(Unit) }

        textViewResend.movementMethod = LinkMovementMethod.getInstance()

        buttonNext.setOnClickListener {
            continueSignUp()
            analytics.logEvent(KYCAnalyticsEvents.VerifyEmailButtonClicked)
        }

        onViewReady()
    }

    override fun showProgressDialog() {
        progressDialog = MaterialProgressDialog(requireContext()).apply {
            setOnCancelListener { presenter.onProgressCancelled() }
            setMessage(R.string.kyc_country_selection_please_wait)
            show()
        }
    }

    override fun dismissProgressDialog() {
        progressDialog?.apply { dismiss() }
        progressDialog = null
    }

    fun continueSignUp() {
        ViewUtils.hideKeyboard(requireActivity())
        navigate(
            KycEmailValidationFragmentDirections.actionAfterValidation()
        )
    }

    override fun displayErrorDialog(message: Int) {
        AlertDialog.Builder(requireContext(), R.style.AlertDialogStyle)
            .setTitle(R.string.app_name)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    override fun createPresenter() = presenter

    override fun getMvpView(): KycEmailValidationView = this

    override fun theEmailWasResent() {
        Toast.makeText(requireContext(), R.string.kyc_email_email_was_resent, Toast.LENGTH_SHORT).show()
    }

    override fun setVerified(verified: Boolean) {
        buttonNext.isEnabled = verified
        textViewResend.goneIf(verified)
    }
}
