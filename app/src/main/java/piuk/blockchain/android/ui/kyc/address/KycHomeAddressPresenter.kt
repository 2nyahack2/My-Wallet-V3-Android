package piuk.blockchain.android.ui.kyc.address

import piuk.blockchain.android.ui.kyc.BaseKycPresenter
import com.blockchain.nabu.models.responses.nabu.Scope
import piuk.blockchain.android.ui.kyc.address.models.AddressModel
import piuk.blockchain.android.campaign.CampaignType
import com.blockchain.nabu.NabuToken
import com.blockchain.nabu.datamanagers.NabuDataManager
import io.reactivex.Completable
import io.reactivex.Maybe
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.rxkotlin.zipWith
import io.reactivex.schedulers.Schedulers
import piuk.blockchain.android.R
import piuk.blockchain.androidcore.data.settings.PhoneVerificationQuery
import piuk.blockchain.androidcore.utils.helperfunctions.unsafeLazy
import timber.log.Timber
import java.util.SortedMap

interface Tier2Decision {

    enum class NextStep {
        Tier1Complete,
        Tier2ContinueTier1NeedsMoreInfo,
        Tier2Continue
    }

    fun progressToTier2(): Single<NextStep>
}

class KycHomeAddressPresenter(
    nabuToken: NabuToken,
    private val nabuDataManager: NabuDataManager,
    private val tier2Decision: Tier2Decision,
    private val phoneVerificationQuery: PhoneVerificationQuery
) : BaseKycPresenter<KycHomeAddressView>(nabuToken) {

    val countryCodeSingle: Single<SortedMap<String, String>> by unsafeLazy {
        fetchOfflineToken
            .flatMap {
                nabuDataManager.getCountriesList(Scope.None)
                    .subscribeOn(Schedulers.io())
            }
            .map { list ->
                list.associateBy({ it.name }, { it.code })
                    .toSortedMap()
            }
            .observeOn(AndroidSchedulers.mainThread())
            .cache()
    }

    override fun onViewReady() {
        compositeDisposable += view.address
            .subscribeBy(
                onNext = { enableButtonIfComplete(it) },
                onError = {
                    Timber.e(it)
                    // This is fatal - back out and allow the user to try again
                    view.finishPage()
                }
            )

        restoreDataIfPresent()
    }

    private fun restoreDataIfPresent() {
        compositeDisposable +=
            view.address
                .firstElement()
                .flatMap { addressModel ->
                    // Don't attempt to restore state if data is already present
                    if (addressModel.containsData()) {
                        Maybe.empty()
                    } else {
                        fetchOfflineToken
                            .flatMapMaybe { tokenResponse ->
                                nabuDataManager.getUser(tokenResponse)
                                    .subscribeOn(Schedulers.io())
                                    .flatMapMaybe { user ->
                                        user.address?.let { address ->
                                            Maybe.just(address)
                                                .flatMap { getCountryName(address.countryCode!!) }
                                                .map { it to address }
                                        } ?: Maybe.empty()
                                    }
                            }
                            .observeOn(AndroidSchedulers.mainThread())
                    }
                }
                .subscribeBy(
                    onSuccess = { (countryName, address) ->
                        view.restoreUiState(
                            address.line1!!,
                            address.line2,
                            address.city!!,
                            address.state,
                            address.postCode,
                            countryName
                        )
                    },
                    onError = {
                        // Silently fail
                        Timber.e(it)
                    }
                )
    }

    private data class State(
        val phoneNeedsToBeVerified: Boolean,
        val progressToTier2: Tier2Decision.NextStep,
        val countryCode: String
    )

    internal fun onContinueClicked(campaignType: CampaignType? = null) {
        compositeDisposable += view.address
            .firstOrError()
            .flatMap { address ->
                addAddress(address)
                    .andThen(phoneVerificationQuery.needsPhoneVerification())
                    .map { verified -> verified to address.country }
            }
            .flatMap { (verified, countryCode) ->
                updateNabuData(verified)
                    .andThen(Single.just(verified to countryCode))
            }
            .map { (verified, countryCode) ->
                State(
                    progressToTier2 = Tier2Decision.NextStep.Tier1Complete,
                    countryCode = countryCode,
                    phoneNeedsToBeVerified = verified
                )
            }
            .zipWith(tier2Decision.progressToTier2())
            .map { (x, progress) -> x.copy(progressToTier2 = progress) }
            .observeOn(AndroidSchedulers.mainThread())
            .doOnSubscribe { view.showProgressDialog() }
            .doOnEvent { _, _ -> view.dismissProgressDialog() }
            .doOnError(Timber::e)
            .subscribeBy(
                onSuccess = {
                    when (it.progressToTier2) {
                        Tier2Decision.NextStep.Tier1Complete -> view.tier1Complete()
                        Tier2Decision.NextStep.Tier2ContinueTier1NeedsMoreInfo ->
                            view.continueToTier2MoreInfoNeeded(it.countryCode)
                        Tier2Decision.NextStep.Tier2Continue ->
                            if (it.phoneNeedsToBeVerified) {
                                view.continueToMobileVerification(it.countryCode)
                            } else {
                                view.continueToVeriffSplash(it.countryCode)
                            }
                    }
                },
                onError = { view.showErrorToast(R.string.kyc_address_error_saving) }
            )
    }

    private fun addAddress(address: AddressModel): Completable = fetchOfflineToken
        .flatMapCompletable {
            nabuDataManager.addAddress(
                it,
                address.firstLine,
                address.secondLine,
                address.city,
                address.state,
                address.postCode,
                address.country
            ).subscribeOn(Schedulers.io())
        }

    private fun updateNabuData(isVerified: Boolean): Completable =
        if (!isVerified) {
            nabuDataManager.requestJwt()
                .subscribeOn(Schedulers.io())
                .flatMap { jwt ->
                    fetchOfflineToken.flatMap {
                        nabuDataManager.updateUserWalletInfo(it, jwt)
                            .subscribeOn(Schedulers.io())
                    }
                }
                .ignoreElement()
        } else {
            Completable.complete()
        }

    private fun getCountryName(countryCode: String): Maybe<String> = countryCodeSingle
        .map { it.entries.first { (_, value) -> value == countryCode }.key }
        .toMaybe()

    private fun enableButtonIfComplete(addressModel: AddressModel) {
        if (addressModel.country.equals("US", ignoreCase = true)) {
            view.setButtonEnabled(
                !addressModel.firstLine.isEmpty() &&
                        !addressModel.city.isEmpty() &&
                        !addressModel.state.isEmpty() &&
                        !addressModel.postCode.isEmpty()
            )
        } else {
            view.setButtonEnabled(
                !addressModel.firstLine.isEmpty() &&
                        !addressModel.city.isEmpty() &&
                        !addressModel.state.isEmpty()
            )
        }
    }

    internal fun onProgressCancelled() {
        compositeDisposable.clear()
    }

    private fun AddressModel.containsData(): Boolean =
        !firstLine.isEmpty() ||
                !secondLine.isNullOrEmpty() ||
                !city.isEmpty() ||
                !state.isEmpty() ||
                !postCode.isEmpty()
}
