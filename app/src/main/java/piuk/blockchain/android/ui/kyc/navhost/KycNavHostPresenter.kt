package piuk.blockchain.android.ui.kyc.navhost

import com.blockchain.exceptions.MetadataNotFoundException
import com.blockchain.nabu.NabuToken
import com.blockchain.nabu.datamanagers.NabuDataManager
import com.blockchain.nabu.models.responses.nabu.KycState
import com.blockchain.nabu.models.responses.nabu.NabuUser
import com.blockchain.nabu.models.responses.nabu.UserState
import com.blockchain.nabu.service.TierUpdater
import io.reactivex.Completable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import piuk.blockchain.android.R
import piuk.blockchain.android.campaign.CampaignRegistration
import piuk.blockchain.android.campaign.CampaignType
import piuk.blockchain.android.campaign.SunriverCampaignRegistration
import piuk.blockchain.android.ui.kyc.BaseKycPresenter
import piuk.blockchain.android.ui.kyc.logging.kycResumedEvent
import piuk.blockchain.android.ui.kyc.profile.models.ProfileModel
import piuk.blockchain.android.ui.kyc.reentry.KycNavigator
import piuk.blockchain.android.ui.kyc.reentry.ReentryDecision
import piuk.blockchain.androidcoreui.utils.logging.Logging
import timber.log.Timber

class KycNavHostPresenter(
    nabuToken: NabuToken,
    private val nabuDataManager: NabuDataManager,
    private val sunriverCampaign: SunriverCampaignRegistration,
    private val reentryDecision: ReentryDecision,
    private val kycNavigator: KycNavigator,
    private val tierUpdater: TierUpdater
) : BaseKycPresenter<KycNavHostView>(nabuToken) {

    override fun onViewReady() {
        compositeDisposable +=
            fetchOfflineToken.flatMap {
                nabuDataManager.getUser(it)
                    .subscribeOn(Schedulers.io())
            }.observeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe { view.displayLoading(true) }
                .subscribeBy(
                    onSuccess = {
                        registerForCampaignsIfNeeded()
                        updateTier2SelectedTierIfNeeded()
                        redirectUserFlow(it)
                    },
                    onError = {
                        Timber.e(it)
                        if (it is MetadataNotFoundException) {
                            // No user, hide loading and start full KYC flow
                            view.displayLoading(false)
                        } else {
                            view.showErrorToastAndFinish(R.string.kyc_status_error)
                        }
                    }
                )
    }

    /**
     * Registers the user to the various campaigns if they are not yet registered with them, on completion of Gold
     */
    private fun registerForCampaignsIfNeeded() {
        if (view.campaignType == CampaignType.Sunriver) {
            checkAndRegisterForCampaign(sunriverCampaign)
        }
    }

    private fun checkAndRegisterForCampaign(campaign: CampaignRegistration) {
        compositeDisposable += campaign.userIsInCampaign()
            .flatMapCompletable { isInCampaign ->
                if (isInCampaign) {
                    Completable.complete()
                } else {
                    campaign.registerCampaign()
                }
            }
            .subscribeOn(Schedulers.io())
            .doOnError(Timber::e)
            .subscribe()
    }

    private fun updateTier2SelectedTierIfNeeded() {
        if (view.campaignType != CampaignType.Sunriver) {
            return
        }

        compositeDisposable += tierUpdater
            .setUserTier(2)
            .doOnError(Timber::e)
            .subscribe()
    }

    private fun redirectUserFlow(user: NabuUser) {
        when {
            view.campaignType == CampaignType.Resubmission || user.isMarkedForResubmission -> {
                view.navigateToResubmissionSplash()
            }
            view.campaignType == CampaignType.Blockstack ||
                view.campaignType == CampaignType.SimpleBuy ||
                view.campaignType == CampaignType.Interest ||
                view.campaignType == CampaignType.FiatFunds ||
                (view.campaignType == CampaignType.Swap && !view.showTiersLimitsSplash) -> {
                compositeDisposable += kycNavigator.findNextStep()
                    .subscribeBy(
                        onError = { Timber.e(it) },
                        onSuccess = { view.navigate(it) }
                    )
            }
            user.state != UserState.None && user.kycState == KycState.None && !view.showTiersLimitsSplash -> {
                val current = user.tiers?.current
                if (current == null || current == 0) {
                    val reentryPoint = reentryDecision.findReentryPoint(user)
                    val directions = kycNavigator.userAndReentryPointToDirections(user, reentryPoint)
                    view.navigate(directions)
                    Logging.logEvent(kycResumedEvent(reentryPoint))
                }
            }
            view.campaignType == CampaignType.Sunriver -> {
                view.navigateToKycSplash()
            }
        }

        // If no other methods are triggered, this will start KYC from scratch. If others have been called,
        // this will make the host fragment visible.
        view.displayLoading(false)
    }
}

internal fun NabuUser.toProfileModel(): ProfileModel = ProfileModel(
    firstName = firstName ?: throw IllegalStateException("First Name is null"),
    lastName = lastName ?: throw IllegalStateException("Last Name is null"),
    countryCode = address?.countryCode ?: throw IllegalStateException("Country Code is null"),
    state = address?.state
)