package piuk.blockchain.android.ui.kyc.tiersplash

import androidx.navigation.NavDirections
import com.blockchain.nabu.models.responses.nabu.KycTierLevel
import com.blockchain.nabu.models.responses.nabu.KycTierState
import piuk.blockchain.android.ui.kyc.reentry.KycNavigator
import com.blockchain.nabu.service.TierService
import com.blockchain.nabu.service.TierUpdater
import io.reactivex.Maybe
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.rxkotlin.subscribeBy
import piuk.blockchain.android.R
import piuk.blockchain.androidcoreui.ui.base.BasePresenter
import timber.log.Timber

class KycTierSplashPresenter(
    private val tierUpdater: TierUpdater,
    private val tierService: TierService,
    private val kycNavigator: KycNavigator
) : BasePresenter<KycTierSplashView>() {

    override fun onViewReady() {}

    override fun onViewResumed() {
        super.onViewResumed()
        compositeDisposable +=
            tierService.tiers()
                .observeOn(AndroidSchedulers.mainThread())
                .doOnError(Timber::e)
                .subscribeBy(
                    onSuccess = {
                        view!!.renderTiersList(it)
                    },
                    onError = {
                        view!!.showError(R.string.kyc_non_specific_server_error)
                    }
                )
    }

    override fun onViewPaused() {
        compositeDisposable.clear()
        super.onViewPaused()
    }

    fun tier1Selected() {
        navigateToTier(1)
    }

    fun tier2Selected() {
        navigateToTier(2)
    }

    private fun navigateToTier(tier: Int) {
        compositeDisposable += navDirections(tier)
            .observeOn(AndroidSchedulers.mainThread())
            .doOnError(Timber::e)
            .subscribeBy(
                onSuccess = {
                    view!!.navigateTo(it, tier)
                },
                onError = {
                    view!!.showError(R.string.kyc_non_specific_server_error)
                }
            )
    }

    private fun navDirections(tier: Int): Maybe<NavDirections> =
        tierService.tiers()
            .filter { tier in (KycTierLevel.values().indices) }
            .map { it.tierForIndex(tier) }
            .filter { it.state == KycTierState.None }
            .flatMap {
                tierUpdater.setUserTier(tier)
                    .andThen(Maybe.just(tier))
            }
            .flatMap { kycNavigator.findNextStep().toMaybe() }
}
