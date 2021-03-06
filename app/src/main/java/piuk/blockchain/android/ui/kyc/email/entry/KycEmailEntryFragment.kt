package piuk.blockchain.android.ui.kyc.email.entry

import android.os.Bundle
import android.text.TextUtils
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.blockchain.koin.scopedInject
import com.blockchain.notifications.analytics.Analytics
import com.blockchain.notifications.analytics.KYCAnalyticsEvents
import com.blockchain.ui.extensions.throttledClicks
import com.jakewharton.rxbinding2.widget.afterTextChangeEvents
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.Observables
import io.reactivex.rxkotlin.plusAssign
import org.koin.android.ext.android.inject
import piuk.blockchain.android.R
import piuk.blockchain.android.ui.customviews.dialogs.MaterialProgressDialog
import piuk.blockchain.android.ui.kyc.extensions.skipFirstUnless
import piuk.blockchain.android.ui.kyc.navhost.KycProgressListener
import piuk.blockchain.android.ui.kyc.navhost.models.KycStep
import piuk.blockchain.android.ui.kyc.navigate
import piuk.blockchain.androidcoreui.ui.base.BaseFragment
import piuk.blockchain.androidcoreui.ui.customviews.ToastCustom
import piuk.blockchain.androidcoreui.ui.customviews.toast
import piuk.blockchain.android.ui.kyc.ParentActivityDelegate
import piuk.blockchain.android.util.getTextString
import piuk.blockchain.android.util.inflate
import java.util.concurrent.TimeUnit
import kotlinx.android.synthetic.main.fragment_kyc_add_email.button_kyc_email_next as buttonNext
import kotlinx.android.synthetic.main.fragment_kyc_add_email.edit_text_kyc_email as editTextEmail
import kotlinx.android.synthetic.main.fragment_kyc_add_email.input_layout_kyc_email as inputLayoutEmail

class KycEmailEntryFragment : BaseFragment<KycEmailEntryView, KycEmailEntryPresenter>(),
    KycEmailEntryView {

    private val presenter: KycEmailEntryPresenter by scopedInject()
    private val analytics: Analytics by inject()
    private val progressListener: KycProgressListener by ParentActivityDelegate(
        this
    )
    private val compositeDisposable = CompositeDisposable()
    private val emailObservable
        get() = editTextEmail.afterTextChangeEvents()
            .skipInitialValue()
            .debounce(300, TimeUnit.MILLISECONDS)
            .observeOn(AndroidSchedulers.mainThread())
            .map { editTextEmail.getTextString() }

    override val uiStateObservable: Observable<Pair<String, Unit>>
        get() = Observables.combineLatest(
            emailObservable.cache(),
            buttonNext.throttledClicks()
        ).doOnNext {
            analytics.logEvent(KYCAnalyticsEvents.EmailUpdateButtonClicked)
        }

    private var progressDialog: MaterialProgressDialog? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = container?.inflate(R.layout.fragment_kyc_add_email)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        progressListener.setHostTitle(R.string.kyc_email_title)

        editTextEmail.setOnFocusChangeListener { _, hasFocus ->
            inputLayoutEmail.hint = if (hasFocus) {
                getString(R.string.kyc_email_hint_focused)
            } else {
                getString(R.string.kyc_email_hint_unfocused)
            }
        }

        onViewReady()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        presenter.dispose()
    }

    override fun onResume() {
        super.onResume()

        compositeDisposable +=
            editTextEmail
                .onDelayedChange(KycStep.EmailEntered)
                .subscribe()
    }

    override fun onPause() {
        compositeDisposable.clear()
        super.onPause()
    }

    override fun preFillEmail(email: String) {
        editTextEmail.setText(email)
    }

    override fun showError(message: Int) =
        toast(message, ToastCustom.TYPE_ERROR)

    override fun continueSignUp(email: String) {
        navigate(KycEmailEntryFragmentDirections.actionValidateEmail(email))
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

    private fun mapToCompleted(text: String): Boolean = emailIsValid(text)

    override fun createPresenter(): KycEmailEntryPresenter = presenter

    override fun getMvpView(): KycEmailEntryView = this
}

private fun emailIsValid(target: String) =
    !TextUtils.isEmpty(target) && Patterns.EMAIL_ADDRESS.matcher(target).matches()
