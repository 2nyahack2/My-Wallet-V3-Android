package piuk.blockchain.android.ui.kyc.complete

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.blockchain.koin.scopedInject
import com.blockchain.notifications.analytics.Analytics
import com.blockchain.notifications.analytics.KYCAnalyticsEvents
import com.blockchain.nabu.models.responses.nabu.KycTierLevel
import com.blockchain.nabu.service.TierService
import com.blockchain.ui.extensions.throttledClicks
import io.reactivex.Observable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.rxkotlin.zipWith
import kotlinx.android.synthetic.main.fragment_kyc_complete.*
import org.koin.android.ext.android.inject
import piuk.blockchain.android.R
import piuk.blockchain.android.campaign.CampaignType
import piuk.blockchain.android.simplebuy.SimpleBuyActivity
import piuk.blockchain.android.ui.home.MainActivity
import piuk.blockchain.android.ui.kyc.navhost.KycProgressListener
import piuk.blockchain.android.ui.kyc.navigate
import piuk.blockchain.android.ui.kyc.ParentActivityDelegate
import piuk.blockchain.android.util.inflate
import timber.log.Timber

class ApplicationCompleteFragment : Fragment() {

    private val progressListener: KycProgressListener by ParentActivityDelegate(
        this
    )
    private val compositeDisposable = CompositeDisposable()
    private val analytics: Analytics by inject()
    private val tierService: TierService by scopedInject()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = container?.inflate(R.layout.fragment_kyc_complete)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        progressListener.setHostTitle(R.string.kyc_complete_title)
        progressListener.hideBackButton()
    }

    override fun onResume() {
        super.onResume()

        compositeDisposable +=
            button_done
                .throttledClicks().zipWith(
                    if (progressListener.campaignType == CampaignType.Swap) {
                        tierService.tiers().toObservable()
                            .map { it.isApprovedFor(KycTierLevel.SILVER) || it.isApprovedFor(KycTierLevel.GOLD) }
                            .onErrorReturn { false }
                    } else {
                        Observable.just(false)
                    }
                ).subscribeBy(
                    onNext = { (_, isTier1OrTier2Verified) ->
                        when (progressListener.campaignType) {
                            CampaignType.Swap -> {
                                launchSwap()
                            }
                            CampaignType.SimpleBuy -> {
                                activity?.setResult(SimpleBuyActivity.RESULT_KYC_SIMPLE_BUY_COMPLETE)
                                activity?.finish()
                            }
                            CampaignType.Interest -> activity?.finish()
                            else -> navigate(ApplicationCompleteFragmentDirections.actionTier2Complete())
                        }
                        analytics.logEvent(KYCAnalyticsEvents.VeriffInfoSubmitted)
                    },
                    onError = { Timber.e(it) }
                )
    }

    private fun launchSwap() {
        val b = Bundle().apply {
            putBoolean(MainActivity.SHOW_SWAP, true)
        }

        MainActivity.start(requireContext(), b)
        activity?.finish()
    }

    override fun onPause() {
        super.onPause()
        compositeDisposable.clear()
    }
}