package piuk.blockchain.android.ui.kyc.splash

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.NavDirections
import com.blockchain.activities.StartOnboarding
import com.blockchain.koin.scopedInject
import com.blockchain.notifications.analytics.Analytics
import com.blockchain.notifications.analytics.AnalyticsEvents
import com.blockchain.notifications.analytics.KYCAnalyticsEvents
import com.blockchain.notifications.analytics.logEvent
import com.blockchain.ui.extensions.throttledClicks
import com.blockchain.ui.urllinks.URL_COINIFY_POLICY
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.rxkotlin.subscribeBy
import org.koin.android.ext.android.inject
import piuk.blockchain.android.R
import piuk.blockchain.android.campaign.CampaignType
import piuk.blockchain.android.ui.customviews.dialogs.MaterialProgressDialog
import piuk.blockchain.android.ui.kyc.hyperlinks.renderTermsLinks
import piuk.blockchain.android.ui.kyc.navhost.KycProgressListener
import piuk.blockchain.android.ui.kyc.navigate
import piuk.blockchain.androidcore.data.settings.SettingsDataManager
import piuk.blockchain.androidcoreui.ui.base.BaseFragment
import piuk.blockchain.androidcoreui.ui.customviews.ToastCustom
import piuk.blockchain.androidcoreui.ui.customviews.toast
import piuk.blockchain.android.ui.kyc.ParentActivityDelegate
import piuk.blockchain.android.util.inflate
import piuk.blockchain.android.util.visible
import timber.log.Timber
import kotlinx.android.synthetic.main.fragment_kyc_splash.button_kyc_splash_apply_now as buttonContinue
import kotlinx.android.synthetic.main.fragment_kyc_splash.text_view_kyc_splash_message as textViewMessage
import kotlinx.android.synthetic.main.fragment_kyc_splash.text_view_kyc_terms_and_conditions as textViewTerms

class KycSplashFragment : BaseFragment<KycSplashView, KycSplashPresenter>(), KycSplashView {

    private val presenter: KycSplashPresenter by scopedInject()

    private val settingsDataManager: SettingsDataManager by scopedInject()

    private val onBoardingStarter: StartOnboarding by inject()

    private val analytics: Analytics by inject()

    private val progressListener: KycProgressListener by ParentActivityDelegate(
        this
    )

    private var progressDialog: MaterialProgressDialog? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = container?.inflate(R.layout.fragment_kyc_splash)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val campaignType = progressListener.campaignType
        logEvent(
            when (campaignType) {
                CampaignType.Swap -> AnalyticsEvents.KycWelcome
                CampaignType.Sunriver -> AnalyticsEvents.KycSunriverStart
                CampaignType.Resubmission -> AnalyticsEvents.KycResubmission
                CampaignType.Blockstack -> AnalyticsEvents.KycBlockstackStart
                CampaignType.SimpleBuy -> AnalyticsEvents.KycSimpleBuyStart
                CampaignType.FiatFunds -> AnalyticsEvents.KycFiatFundsStart
                CampaignType.Interest -> AnalyticsEvents.KycFiatFundsStart
            }
        )

        val title = when (progressListener.campaignType) {
            CampaignType.Sunriver,
            CampaignType.Blockstack,
            CampaignType.SimpleBuy,
            CampaignType.Resubmission,
            CampaignType.FiatFunds -> R.string.buy_sell_splash_title
            CampaignType.Swap -> R.string.kyc_splash_title
            CampaignType.Interest -> R.string.earn_interest
        }

        progressListener.setHostTitle(title)

        textViewTerms.renderTermsLinks(
            R.string.buy_sell_splash_terms_and_conditions,
            URL_COINIFY_POLICY,
            URL_COINIFY_POLICY
        )
        textViewTerms.visible()

        textViewMessage.setText(R.string.buy_sell_splash_message)
    }

    private val disposable = CompositeDisposable()

    override fun onResume() {
        super.onResume()
        disposable += buttonContinue
            .throttledClicks()
            .subscribeBy(
                onNext = {
                    analytics.logEvent(KYCAnalyticsEvents.VerifyIdentityStart)
                    presenter.onCTATapped()
                },
                onError = { Timber.e(it) }
            )
    }

    override fun onPause() {
        disposable.clear()
        super.onPause()
    }

    override fun goToNextKycStep(direction: NavDirections) =
        navigate(direction)

    override fun displayLoading(isLoading: Boolean) {
        progressDialog = if (isLoading) {
            MaterialProgressDialog(requireContext()).apply {
                setMessage(R.string.buy_sell_please_wait)
                show()
            }
        } else {
            progressDialog?.apply { dismiss() }
            null
        }
    }

    override fun showError(message: String) =
        toast(message, ToastCustom.TYPE_ERROR)

    override fun onEmailNotVerified() {
        disposable += settingsDataManager.getSettings().subscribeBy(onNext = {
            activity?.let {
                onBoardingStarter.startEmailOnboarding(it)
            }
        }, onError = {})
    }

    override fun createPresenter(): KycSplashPresenter = presenter

    override fun getMvpView(): KycSplashView = this
}
