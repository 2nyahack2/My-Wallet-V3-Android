package piuk.blockchain.android.ui.kyc.countryselection

import com.blockchain.nabu.datamanagers.NabuDataManager
import com.blockchain.nabu.models.responses.nabu.NabuRegion
import com.blockchain.nabu.models.responses.nabu.Scope
import piuk.blockchain.android.ui.kyc.countryselection.models.CountrySelectionState
import piuk.blockchain.android.ui.kyc.countryselection.util.CountryDisplayModel
import piuk.blockchain.android.ui.kyc.countryselection.util.toDisplayList
import io.reactivex.Maybe
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import piuk.blockchain.android.R
import piuk.blockchain.androidcore.utils.helperfunctions.unsafeLazy
import piuk.blockchain.androidcoreui.ui.base.BasePresenter
import timber.log.Timber

internal class KycCountrySelectionPresenter(
    private val nabuDataManager: NabuDataManager
) : BasePresenter<KycCountrySelectionView>() {

    private val usCountryCode = "US"

    private val countriesList by unsafeLazy {
        nabuDataManager.getCountriesList(Scope.None)
            .cache()
    }

    private val usStatesList by unsafeLazy {
        nabuDataManager.getStatesList(usCountryCode, Scope.None)
            .cache()
    }

    private fun getRegionList() =
        if (view.regionType == RegionType.Country) countriesList else usStatesList

    override fun onViewReady() {
        compositeDisposable +=
            getRegionList()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe { view.renderUiState(CountrySelectionState.Loading) }
                .doOnError {
                    view.renderUiState(
                        CountrySelectionState.Error(R.string.kyc_country_selection_connection_error)
                    )
                }
                .doOnSuccess { view.renderUiState(CountrySelectionState.Data(it.toDisplayList())) }
                .subscribeBy(onError = { Timber.e(it) })
    }

    internal fun onRegionSelected(
        countryDisplayModel: CountryDisplayModel
    ) {
        compositeDisposable +=
            getRegionList()
                .flatMapMaybe { regions ->
                    Maybe.just(regions)
                }
                .filter {
                    it.isKycAllowed(countryDisplayModel.regionCode) &&
                        !countryDisplayModel.requiresStateSelection()
                }
                .subscribeBy(
                    onSuccess = { view.continueFlow(countryDisplayModel.countryCode, countryDisplayModel.state) },
                    onComplete = {
                        when {
                            // Not found, is US, must select state
                            countryDisplayModel.requiresStateSelection() -> view.requiresStateSelection()
                            // Not found, invalid
                            else -> view.invalidCountry(countryDisplayModel)
                        }
                    },
                    onError = {
                        throw IllegalStateException("Region list should already be cached")
                    }
                )
    }

    private fun List<NabuRegion>.isKycAllowed(regionCode: String): Boolean =
        this.any { it.isMatchingRegion(regionCode) && it.isKycAllowed }

    private fun NabuRegion.isMatchingRegion(regionCode: String): Boolean =
        this.code.equals(regionCode, ignoreCase = true)

    private fun CountryDisplayModel.requiresStateSelection(): Boolean =
        this.countryCode.equals(usCountryCode, ignoreCase = true) && !this.isState

    internal fun onRequestCancelled() {
        compositeDisposable.clear()
    }
}