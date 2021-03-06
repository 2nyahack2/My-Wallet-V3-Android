package piuk.blockchain.android.ui.kyc.resubmission

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import piuk.blockchain.android.ui.kyc.navhost.KycProgressListener
import piuk.blockchain.android.ui.kyc.navigate
import piuk.blockchain.android.ui.kyc.reentry.KycNavigator
import com.blockchain.notifications.analytics.AnalyticsEvents
import com.blockchain.notifications.analytics.logEvent
import com.blockchain.ui.extensions.throttledClicks
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.rxkotlin.subscribeBy
import org.koin.android.ext.android.inject
import piuk.blockchain.android.R
import piuk.blockchain.android.ui.kyc.ParentActivityDelegate
import piuk.blockchain.android.util.inflate
import timber.log.Timber
import kotlinx.android.synthetic.main.fragment_kyc_resubmission_splash.button_kyc_resubmission_splash_next as buttonContinue

class KycResubmissionSplashFragment : Fragment() {

    private val progressListener: KycProgressListener by ParentActivityDelegate(
        this
    )

    private val kycNavigator: KycNavigator by inject()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = container?.inflate(R.layout.fragment_kyc_resubmission_splash)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        logEvent(AnalyticsEvents.KycResubmission)

        progressListener.setHostTitle(R.string.kyc_resubmission_splash_title)
    }

    private val disposable = CompositeDisposable()

    override fun onResume() {
        super.onResume()
        disposable += buttonContinue
            .throttledClicks()
            .flatMapSingle { kycNavigator.findNextStep() }
            .subscribeBy(
                onNext = { navigate(it) },
                onError = { Timber.e(it) }
            )
    }

    override fun onPause() {
        disposable.clear()
        super.onPause()
    }
}
