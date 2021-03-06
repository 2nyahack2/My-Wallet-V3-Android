package piuk.blockchain.android.ui.kyc.mobile.validation

import android.net.Uri
import android.os.Bundle
import android.text.method.LinkMovementMethod
import androidx.appcompat.app.AlertDialog
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.navigation.fragment.NavHostFragment.findNavController
import com.blockchain.koin.scopedInject
import com.blockchain.notifications.analytics.Analytics
import com.blockchain.notifications.analytics.KYCAnalyticsEvents
import piuk.blockchain.android.ui.kyc.extensions.skipFirstUnless
import piuk.blockchain.android.ui.kyc.mobile.entry.models.PhoneVerificationModel
import piuk.blockchain.android.ui.kyc.mobile.validation.models.VerificationCode
import piuk.blockchain.android.ui.kyc.navhost.KycProgressListener
import piuk.blockchain.android.ui.kyc.navhost.models.KycStep
import com.blockchain.ui.extensions.throttledClicks
import com.jakewharton.rxbinding2.widget.afterTextChangeEvents
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.Observables
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.subjects.PublishSubject
import org.koin.android.ext.android.inject
import piuk.blockchain.android.KycNavXmlDirections
import piuk.blockchain.android.R
import piuk.blockchain.androidcore.data.settings.PhoneNumber
import piuk.blockchain.androidcore.utils.helperfunctions.unsafeLazy
import piuk.blockchain.androidcoreui.ui.base.BaseMvpFragment
import kotlinx.android.synthetic.main.fragment_kyc_mobile_validation.*
import piuk.blockchain.android.ui.customviews.dialogs.MaterialProgressDialog
import piuk.blockchain.android.util.StringUtils
import piuk.blockchain.android.ui.kyc.ParentActivityDelegate
import piuk.blockchain.android.util.ViewUtils
import piuk.blockchain.android.util.inflate
import java.util.concurrent.TimeUnit
import kotlinx.android.synthetic.main.fragment_kyc_mobile_validation.button_kyc_mobile_validation_next as buttonNext
import kotlinx.android.synthetic.main.fragment_kyc_mobile_validation.edit_text_kyc_mobile_validation_code as editTextVerificationCode
import kotlinx.android.synthetic.main.fragment_kyc_mobile_validation.text_view_mobile_validation_message as textViewPhoneNumber

class KycMobileValidationFragment :
    BaseMvpFragment<KycMobileValidationView, KycMobileValidationPresenter>(),
    KycMobileValidationView {

    private val presenter: KycMobileValidationPresenter by scopedInject()
    private val analytics: Analytics by inject()
    private val stringUtils: StringUtils by inject()
    private val progressListener: KycProgressListener by ParentActivityDelegate(
        this
    )
    private val compositeDisposable = CompositeDisposable()
    private var progressDialog: MaterialProgressDialog? = null
    private val args by unsafeLazy {
        KycMobileValidationFragmentArgs.fromBundle(
            arguments ?: Bundle())
    }
    private val displayModel by unsafeLazy { args.mobileNumber }
    private val countryCode by unsafeLazy { args.countryCode }
    private val verificationCodeObservable by unsafeLazy {
        editTextVerificationCode.afterTextChangeEvents()
            .skipInitialValue()
            .debounce(300, TimeUnit.MILLISECONDS)
            .observeOn(AndroidSchedulers.mainThread())
            .map {
                PhoneVerificationModel(
                    displayModel.sanitizedString,
                    VerificationCode(it.editable().toString())
                )
            }
    }

    private val resend = PublishSubject.create<Unit>()

    override val resendObservable: Observable<Pair<PhoneNumber, Unit>> by unsafeLazy {
        Observables.combineLatest(
            Observable.just(PhoneNumber(displayModel.formattedString)),
            resend.throttledClicks()
        )
    }

    override val uiStateObservable: Observable<Pair<PhoneVerificationModel, Unit>> by unsafeLazy {
        Observables.combineLatest(
            verificationCodeObservable.cache(),
            buttonNext.throttledClicks()
        ).doOnNext {
            analytics.logEvent(KYCAnalyticsEvents.PhoneNumberUpdateButtonClicked)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = container?.inflate(R.layout.fragment_kyc_mobile_validation)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        progressListener.setHostTitle(R.string.kyc_phone_number_title)
        textViewPhoneNumber.text = displayModel.formattedString

        val linksMap = mapOf<String, Uri?>(
            "resend_code" to null
        )

        text_view_resend_prompt.text =
            stringUtils.getStringWithMappedAnnotations(
                R.string.kyc_phone_send_again,
                linksMap,
                requireActivity()
            ) { resend.onNext(Unit) }

        text_view_resend_prompt.movementMethod = LinkMovementMethod.getInstance()

        onViewReady()
    }

    override fun onResume() {
        super.onResume()

        compositeDisposable +=
            editTextVerificationCode
                .onDelayedChange(KycStep.VerificationCodeEntered)
                .subscribe()
    }

    override fun onPause() {
        super.onPause()
        compositeDisposable.clear()
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

    override fun continueSignUp() {
        ViewUtils.hideKeyboard(requireActivity())
        findNavController(this).apply {
            // Remove phone entry and validation pages from back stack as it would be confusing for the user
            popBackStack(R.id.kycPhoneNumberFragment, true)
            navigate(KycNavXmlDirections.actionStartVeriff(countryCode))
        }
    }

    override fun displayErrorDialog(message: Int) {
        AlertDialog.Builder(requireContext(), R.style.AlertDialogStyle)
            .setTitle(R.string.app_name)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun TextView.onDelayedChange(
        kycStep: KycStep
    ): Observable<Boolean> =
        this.afterTextChangeEvents()
            .debounce(300, TimeUnit.MILLISECONDS)
            .map { it.editable()?.toString() ?: "" }
            .skipFirstUnless { it.isNotEmpty() }
            .observeOn(AndroidSchedulers.mainThread())
            .map { mapToCompleted(it) }
            .distinctUntilChanged()
            .doOnNext {
                buttonNext.isEnabled = it
            }

    private fun mapToCompleted(text: String): Boolean = VerificationCode(text).isValid

    override fun createPresenter(): KycMobileValidationPresenter = presenter

    override fun getMvpView(): KycMobileValidationView = this

    override fun theCodeWasResent() {
        Toast.makeText(requireContext(), R.string.kyc_phone_number_code_was_resent, Toast.LENGTH_SHORT).show()
    }
}