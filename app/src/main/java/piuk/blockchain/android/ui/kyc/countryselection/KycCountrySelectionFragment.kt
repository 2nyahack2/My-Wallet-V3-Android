package piuk.blockchain.android.ui.kyc.countryselection

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.NavHostFragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.blockchain.koin.scopedInject
import com.blockchain.notifications.analytics.Analytics
import com.blockchain.notifications.analytics.AnalyticsEvents
import com.blockchain.notifications.analytics.KYCAnalyticsEvents
import com.blockchain.notifications.analytics.logEvent
import com.jakewharton.rxbinding2.support.v7.widget.queryTextChanges
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.subjects.ReplaySubject
import kotlinx.android.synthetic.main.fragment_kyc_country_selection.*
import org.koin.android.ext.android.inject
import piuk.blockchain.android.R
import piuk.blockchain.android.ui.customviews.dialogs.MaterialProgressDialog
import piuk.blockchain.android.ui.kyc.countryselection.adapter.CountryCodeAdapter
import piuk.blockchain.android.ui.kyc.countryselection.models.CountrySelectionState
import piuk.blockchain.android.ui.kyc.countryselection.util.CountryDisplayModel
import piuk.blockchain.android.ui.kyc.navhost.KycProgressListener
import piuk.blockchain.android.ui.kyc.navigate
import piuk.blockchain.android.ui.kyc.search.filterCountries
import piuk.blockchain.androidcore.utils.helperfunctions.unsafeLazy
import piuk.blockchain.androidcoreui.ui.base.BaseFragment
import piuk.blockchain.androidcoreui.ui.customviews.ToastCustom
import piuk.blockchain.androidcoreui.ui.customviews.toast
import piuk.blockchain.android.ui.kyc.ParentActivityDelegate
import piuk.blockchain.android.util.inflate
import java.util.concurrent.TimeUnit

internal class KycCountrySelectionFragment :
    BaseFragment<KycCountrySelectionView, KycCountrySelectionPresenter>(), KycCountrySelectionView {

    override val regionType by unsafeLazy {
        arguments?.getSerializable(ARGUMENT_STATE_OR_COUNTRY) as? RegionType ?: RegionType.Country
    }

    private val presenter: KycCountrySelectionPresenter by scopedInject()
    private val analytics: Analytics by inject()
    private val progressListener: KycProgressListener by ParentActivityDelegate(
        this
    )
    private val countryCodeAdapter = CountryCodeAdapter {
        presenter.onRegionSelected(it)
    }
    private var countryList = ReplaySubject.create<List<CountryDisplayModel>>(1)
    private var progressDialog: MaterialProgressDialog? = null
    private val compositeDisposable = CompositeDisposable()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = container?.inflate(R.layout.fragment_kyc_country_selection)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        country_selection.apply {
            layoutManager = LinearLayoutManager(requireContext())
            setHasFixedSize(true)
            adapter = countryCodeAdapter
        }

        when (regionType) {
            RegionType.Country -> {
                logEvent(AnalyticsEvents.KycCountry)
                progressListener.setHostTitle(R.string.kyc_country_selection_title_1)
            }
            RegionType.State -> {
                logEvent(AnalyticsEvents.KycStates)
                progressListener.setHostTitle(R.string.kyc_country_selection_state_title)
            }
        }
        onViewReady()
    }

    override fun onResume() {
        super.onResume()

        compositeDisposable += countryList
            .filterCountries(
                search_view.queryTextChanges().debounce(100, TimeUnit.MILLISECONDS)
            )
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe {
                countryCodeAdapter.items = it
                country_selection.scrollToPosition(0)
            }
    }

    override fun onPause() {
        super.onPause()
        compositeDisposable.clear()
    }

    override fun continueFlow(countryCode: String, stateCode: String?) {
        analytics.logEvent(KYCAnalyticsEvents.CountrySelected)
        navigate(
            KycCountrySelectionFragmentDirections.actionKycCountrySelectionFragmentToKycProfileFragment(
                countryCode,
                stateCode ?: ""
            )
        )
    }

    override fun invalidCountry(displayModel: CountryDisplayModel) {
        navigate(
            KycCountrySelectionFragmentDirections.actionKycCountrySelectionFragmentToKycInvalidCountryFragment(
                displayModel
            )
        )
    }

    override fun requiresStateSelection() {
        val args = bundleArgs(RegionType.State)
        findNavController(this).navigate(R.id.kycCountrySelectionFragment, args)
    }

    override fun renderUiState(state: CountrySelectionState) {
        when (state) {
            CountrySelectionState.Loading -> showProgress()
            is CountrySelectionState.Error -> showErrorToast(state.errorMessage)
            is CountrySelectionState.Data -> renderCountriesList(state)
        }
    }

    private fun renderCountriesList(state: CountrySelectionState.Data) {
        countryList.onNext(state.countriesList)
        hideProgress()
    }

    private fun showErrorToast(errorMessage: Int) {
        hideProgress()
        toast(errorMessage, ToastCustom.TYPE_ERROR)
    }

    private fun showProgress() {
        progressDialog = MaterialProgressDialog(
            requireContext()
        ).apply {
            setMessage(R.string.kyc_country_selection_please_wait)
            setOnCancelListener { presenter.onRequestCancelled() }
            show()
        }
    }

    private fun hideProgress() {
        if (progressDialog != null && progressDialog!!.isShowing) {
            progressDialog?.dismiss()
        }
    }

    override fun createPresenter(): KycCountrySelectionPresenter = presenter

    override fun getMvpView(): KycCountrySelectionView = this

    companion object {

        private const val ARGUMENT_STATE_OR_COUNTRY = "ARGUMENT_STATE_OR_COUNTRY"

        internal fun bundleArgs(regionType: RegionType): Bundle = Bundle().apply {
            putSerializable(ARGUMENT_STATE_OR_COUNTRY, regionType)
        }
    }
}

internal enum class RegionType {
    Country,
    State
}