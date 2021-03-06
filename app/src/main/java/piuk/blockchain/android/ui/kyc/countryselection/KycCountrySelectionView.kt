package piuk.blockchain.android.ui.kyc.countryselection

import piuk.blockchain.android.ui.kyc.countryselection.models.CountrySelectionState
import piuk.blockchain.android.ui.kyc.countryselection.util.CountryDisplayModel
import piuk.blockchain.androidcoreui.ui.base.View

internal interface KycCountrySelectionView : View {

    val regionType: RegionType

    fun continueFlow(countryCode: String, stateCode: String?)

    fun invalidCountry(displayModel: CountryDisplayModel)

    fun renderUiState(state: CountrySelectionState)

    fun requiresStateSelection()
}