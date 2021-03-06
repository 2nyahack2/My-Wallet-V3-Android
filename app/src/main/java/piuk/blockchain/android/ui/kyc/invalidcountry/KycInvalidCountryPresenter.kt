package piuk.blockchain.android.ui.kyc.invalidcountry

import com.blockchain.metadata.MetadataRepository
import com.blockchain.nabu.datamanagers.NabuDataManager
import com.blockchain.nabu.metadata.NabuCredentialsMetadata
import com.blockchain.nabu.models.responses.tokenresponse.NabuOfflineTokenResponse
import com.blockchain.nabu.models.responses.tokenresponse.mapToMetadata
import io.reactivex.Completable
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.schedulers.Schedulers
import piuk.blockchain.androidcoreui.ui.base.BasePresenter
import timber.log.Timber

class KycInvalidCountryPresenter(
    private val nabuDataManager: NabuDataManager,
    private val metadataRepository: MetadataRepository
) : BasePresenter<KycInvalidCountryView>() {

    override fun onViewReady() = Unit

    internal fun onNoThanks() {
        compositeDisposable +=
            recordCountryCode(false)
                .subscribe()
    }

    internal fun onNotifyMe() {
        compositeDisposable +=
            recordCountryCode(true)
                .subscribe()
    }

    private fun recordCountryCode(notifyMe: Boolean): Completable =
        createUserAndStoreInMetadata()
            .flatMapCompletable { (jwt, offlineToken) ->
                nabuDataManager.recordCountrySelection(
                    offlineToken,
                    jwt,
                    view.displayModel.countryCode,
                    view.displayModel.state,
                    notifyMe
                ).subscribeOn(Schedulers.io())
            }
            .doOnError { Timber.e(it) }
            // No need to notify users that this has failed
            .onErrorComplete()
            .observeOn(AndroidSchedulers.mainThread())
            .doOnComplete { view.finishPage() }
            .doOnSubscribe { view.showProgressDialog() }
            .doOnTerminate { view.dismissProgressDialog() }

    private fun createUserAndStoreInMetadata(): Single<Pair<String, NabuOfflineTokenResponse>> =
        nabuDataManager.requestJwt()
            .subscribeOn(Schedulers.io())
            .flatMap { jwt ->
                nabuDataManager.getAuthToken(jwt)
                    .subscribeOn(Schedulers.io())
                    .flatMap { tokenResponse ->
                        val nabuMetadata = tokenResponse.mapToMetadata()
                        metadataRepository.saveMetadata(
                            nabuMetadata,
                            NabuCredentialsMetadata::class.java,
                            NabuCredentialsMetadata.USER_CREDENTIALS_METADATA_NODE
                        ).toSingle { jwt to tokenResponse }
                    }
            }

    internal fun onProgressCancelled() {
        // Clear outbound requests
        compositeDisposable.clear()
    }
}