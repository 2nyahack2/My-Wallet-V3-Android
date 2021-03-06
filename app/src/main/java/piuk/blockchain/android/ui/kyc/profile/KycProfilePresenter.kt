package piuk.blockchain.android.ui.kyc.profile

import piuk.blockchain.android.ui.kyc.BaseKycPresenter
import com.blockchain.nabu.datamanagers.NabuDataManager
import com.blockchain.nabu.models.responses.nabu.NabuApiException
import com.blockchain.nabu.models.responses.nabu.NabuErrorStatusCodes
import com.blockchain.nabu.util.toISO8601DateString
import piuk.blockchain.android.campaign.CampaignType
import piuk.blockchain.android.ui.kyc.profile.models.ProfileModel
import com.blockchain.metadata.MetadataRepository
import com.blockchain.nabu.NabuToken
import com.blockchain.nabu.metadata.NabuCredentialsMetadata
import com.blockchain.nabu.models.responses.tokenresponse.NabuOfflineTokenResponse
import com.blockchain.nabu.models.responses.tokenresponse.mapFromMetadata
import com.blockchain.nabu.models.responses.tokenresponse.mapToMetadata
import io.reactivex.Completable
import io.reactivex.Maybe
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.properties.Delegates
import com.google.common.base.Optional
import piuk.blockchain.android.R
import piuk.blockchain.android.util.StringUtils

class KycProfilePresenter(
    nabuToken: NabuToken,
    private val nabuDataManager: NabuDataManager,
    private val metadataRepository: MetadataRepository,
    private val stringUtils: StringUtils
) : BaseKycPresenter<KycProfileView>(nabuToken) {

    var firstNameSet by Delegates.observable(false) { _, _, _ -> enableButtonIfComplete() }
    var lastNameSet by Delegates.observable(false) { _, _, _ -> enableButtonIfComplete() }
    var dateSet by Delegates.observable(false) { _, _, _ -> enableButtonIfComplete() }

    override fun onViewReady() {
        restoreDataIfPresent()
    }

    internal fun onContinueClicked(campaignType: CampaignType? = null) {
        check(!view.firstName.isEmpty()) { "firstName is empty" }
        check(!view.lastName.isEmpty()) { "lastName is empty" }
        check(view.dateOfBirth != null) { "dateOfBirth is null" }

        compositeDisposable +=
            metadataRepository.loadMetadata(
                NabuCredentialsMetadata.USER_CREDENTIALS_METADATA_NODE,
                NabuCredentialsMetadata::class.java
            ).toOptional()
                .flatMapCompletable { optionalToken ->
                    if (optionalToken.isPresent) {
                        val metadata = optionalToken.get()
                        if (metadata.isValid()) {
                            createBasicUser(metadata.mapFromMetadata())
                        } else {
                            createUserAndStoreInMetadata()
                        }
                    } else {
                        createUserAndStoreInMetadata()
                    }
                }
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe { view.showProgressDialog() }
                .doOnTerminate { view.dismissProgressDialog() }
                .doOnError(Timber::e)
                .subscribeBy(
                    onComplete = {
                        ProfileModel(
                            firstName = view.firstName,
                            lastName = view.lastName,
                            countryCode = view.countryCode,
                            state = view.state
                        ).run { view.continueSignUp(this) }
                    },
                    onError = {
                        if (it is NabuApiException &&
                            it.getErrorStatusCode() == NabuErrorStatusCodes.Conflict
                        ) {
                            view.showErrorToast(stringUtils.getString(R.string.kyc_profile_error_conflict))
                        } else {
                            view.showErrorToast(stringUtils.getString(R.string.kyc_profile_error))
                        }
                    }
                )
    }

    private fun restoreDataIfPresent() {
        // Don't restore data if data already present, as it'll overwrite what the user
        // may have edited themselves
        if (!firstNameSet && !lastNameSet && !dateSet) {
            compositeDisposable +=
                fetchOfflineToken
                    .flatMap {
                        nabuDataManager.getUser(it)
                            .subscribeOn(Schedulers.io())
                    }
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribeBy(
                        onSuccess = {
                            val firstName = it.firstName ?: return@subscribeBy
                            val lastName = it.lastName ?: return@subscribeBy
                            val dob = it.dob ?: return@subscribeBy
                            val displayFormat = SimpleDateFormat("MMMM dd, yyyy", Locale.US)
                            val backendFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                            val displayDate = backendFormat.parse(dob)

                            view.restoreUiState(
                                firstName,
                                lastName,
                                displayFormat.format(displayDate),
                                displayDate.toCalendar()
                            )
                        },
                        onError = {
                            // Silently fail
                            Timber.d(it)
                        }
                    )
        }
    }

    private fun createUserAndStoreInMetadata(): Completable = nabuDataManager.requestJwt()
        .subscribeOn(Schedulers.io())
        .flatMapCompletable { jwt ->
            nabuDataManager.getAuthToken(jwt)
                .subscribeOn(Schedulers.io())
                .flatMapCompletable { tokenResponse ->
                    metadataRepository.saveMetadata(
                        tokenResponse.mapToMetadata(),
                        NabuCredentialsMetadata::class.java,
                        NabuCredentialsMetadata.USER_CREDENTIALS_METADATA_NODE
                    ).toSingle { tokenResponse }
                        .flatMapCompletable { createBasicUser(it) }
                }
        }

    private fun createBasicUser(offlineToken: NabuOfflineTokenResponse): Completable =
        nabuDataManager.createBasicUser(
            view.firstName,
            view.lastName,
            view.dateOfBirth?.toISO8601DateString()
                ?: throw IllegalStateException("DoB has not been set"),
            offlineToken
        ).subscribeOn(Schedulers.io())

    private fun enableButtonIfComplete() {
        view.setButtonEnabled(firstNameSet && lastNameSet && dateSet)
    }

    internal fun onProgressCancelled() {
        compositeDisposable.clear()
    }

    private fun Date.toCalendar(): Calendar =
        Calendar.getInstance().apply { time = this@toCalendar }
}

private fun <T> Maybe<T>.toOptional(): Single<Optional<T>> =
    map { Optional.of(it) }
        .switchIfEmpty(Maybe.just(Optional.absent<T>()))
        .toSingle()
