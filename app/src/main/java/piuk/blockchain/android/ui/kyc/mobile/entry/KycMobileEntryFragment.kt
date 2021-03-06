package piuk.blockchain.android.ui.kyc.mobile.entry

import android.os.Bundle
import android.telephony.PhoneNumberFormattingTextWatcher
import android.telephony.PhoneNumberUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.blockchain.koin.scopedInject
import piuk.blockchain.android.ui.kyc.extensions.skipFirstUnless
import piuk.blockchain.android.ui.kyc.mobile.entry.models.PhoneDisplayModel
import piuk.blockchain.android.ui.kyc.navhost.KycProgressListener
import piuk.blockchain.android.ui.kyc.navhost.models.KycStep
import piuk.blockchain.android.ui.kyc.navigate
import com.blockchain.ui.extensions.throttledClicks
import com.jakewharton.rxbinding2.widget.afterTextChangeEvents
import io.michaelrocks.libphonenumber.android.PhoneNumberUtil
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.Observables
import io.reactivex.rxkotlin.plusAssign
import piuk.blockchain.android.R
import piuk.blockchain.android.ui.customviews.dialogs.MaterialProgressDialog
import piuk.blockchain.androidcore.data.settings.PhoneNumber
import piuk.blockchain.androidcore.utils.helperfunctions.unsafeLazy
import piuk.blockchain.androidcoreui.ui.base.BaseFragment
import piuk.blockchain.androidcoreui.ui.customviews.ToastCustom
import piuk.blockchain.androidcoreui.ui.customviews.toast
import piuk.blockchain.android.ui.kyc.ParentActivityDelegate
import piuk.blockchain.android.util.getTextString
import piuk.blockchain.android.util.inflate
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.android.synthetic.main.fragment_kyc_add_phone_number.button_kyc_phone_number_next as buttonNext
import kotlinx.android.synthetic.main.fragment_kyc_add_phone_number.edit_text_kyc_mobile_number as editTextPhoneNumber
import kotlinx.android.synthetic.main.fragment_kyc_add_phone_number.input_layout_kyc_mobile_number as inputLayoutPhoneNumber

class KycMobileEntryFragment : BaseFragment<KycMobileEntryView, KycMobileEntryPresenter>(),
    KycMobileEntryView {

    private val presenter: KycMobileEntryPresenter by scopedInject()
    private val progressListener: KycProgressListener by ParentActivityDelegate(
        this
    )
    private val compositeDisposable = CompositeDisposable()
    private val countryCode by unsafeLazy {
        KycMobileEntryFragmentArgs.fromBundle(
            arguments ?: Bundle()
        ).countryCode
    }
    private val phoneNumberObservable
        get() = editTextPhoneNumber.afterTextChangeEvents()
            .skipInitialValue()
            .debounce(300, TimeUnit.MILLISECONDS)
            .observeOn(AndroidSchedulers.mainThread())
            .doOnNext {
                val string = it.editable().toString()
                // Force plus sign even if user deletes it
                if (string.firstOrNull() != '+') {
                    editTextPhoneNumber.apply {
                        setText("+$string")
                        setSelection(getTextString().length)
                    }
                }
            }
            .map { PhoneNumber(editTextPhoneNumber.getTextString()) }

    override val uiStateObservable: Observable<Pair<PhoneNumber, Unit>>
        get() = Observables.combineLatest(
            phoneNumberObservable.cache(),
            buttonNext.throttledClicks()
        )

    private var progressDialog: MaterialProgressDialog? = null
    private val prefixGuess by unsafeLazy {
        "+" + PhoneNumberUtil.createInstance(context)
            .getCountryCodeForRegion(countryCode)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = container?.inflate(R.layout.fragment_kyc_add_phone_number)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        progressListener.setHostTitle(R.string.kyc_phone_number_title)

        editTextPhoneNumber.addTextChangedListener(PhoneNumberFormattingTextWatcher())
        editTextPhoneNumber.setOnFocusChangeListener { _, hasFocus ->
            inputLayoutPhoneNumber.hint = if (hasFocus) {
                getString(R.string.kyc_phone_number_hint_focused)
            } else {
                getString(R.string.kyc_phone_number_hint_unfocused)
            }

            // Insert our best guess for the device's dialling code
            if (hasFocus && editTextPhoneNumber.getTextString().isEmpty()) {
                editTextPhoneNumber.setText(prefixGuess)
            }
        }

        onViewReady()
    }

    override fun onResume() {
        super.onResume()

        compositeDisposable +=
            editTextPhoneNumber
                .onDelayedChange(KycStep.MobileNumberEntered)
                .subscribe()
    }

    override fun onPause() {
        super.onPause()
        compositeDisposable.clear()
    }

    override fun preFillPhoneNumber(phoneNumber: String) {
        val formattedNumber = PhoneNumberUtils.formatNumber(phoneNumber, Locale.getDefault().isO3Country)
        editTextPhoneNumber.setText(formattedNumber)
    }

    override fun showErrorToast(message: Int) {
        toast(message, ToastCustom.TYPE_ERROR)
    }

    override fun continueSignUp(displayModel: PhoneDisplayModel) {
        navigate(KycMobileEntryFragmentDirections.actionMobileCodeEntry(countryCode, displayModel))
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
            .skipFirstUnless { !it.isEmpty() }
            .observeOn(AndroidSchedulers.mainThread())
            .map { mapToCompleted(it) }
            .distinctUntilChanged()
            .doOnNext {
                buttonNext.isEnabled = it
            }

    private fun mapToCompleted(text: String): Boolean = PhoneNumber(text).isValid

    override fun createPresenter(): KycMobileEntryPresenter = presenter

    override fun getMvpView(): KycMobileEntryView = this
}
