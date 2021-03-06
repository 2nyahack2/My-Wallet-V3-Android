package piuk.blockchain.android.ui.thepit

import com.blockchain.nabu.datamanagers.NabuDataManager
import com.blockchain.nabu.NabuToken
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.rxkotlin.subscribeBy
import piuk.blockchain.android.deeplink.EmailVerificationDeepLinkHelper
import piuk.blockchain.androidcore.data.settings.Email
import piuk.blockchain.androidcore.data.settings.EmailSyncUpdater
import piuk.blockchain.androidcoreui.ui.base.BasePresenter
import java.util.concurrent.TimeUnit

class PitVerifyEmailPresenter(
    nabuToken: NabuToken,
    private val nabu: NabuDataManager,
    private val emailSyncUpdater: EmailSyncUpdater
) : BasePresenter<PitVerifyEmailView>() {

    private val fetchUser = nabuToken.fetchNabuToken().flatMap { nabu.getUser(it) }

    override fun onViewReady() {
        reset()
    }

    private fun reset() {
        // Poll for 'is verified' status (ugh!) and close this activity when it is
        compositeDisposable += Observable.interval(5, TimeUnit.SECONDS)
            .flatMapSingle { fetchUser }
            .subscribeBy(
                onNext = { if (it.emailVerified) { view?.emailVerified() } },
                onError = { reset() }
            )
    }

    private fun onResendFailed() {
        view?.mailResendFailed()
    }

    private fun onResendSuccess(email: Email) {
        if (email.address.isNotEmpty()) {
            view?.mailResentSuccessfully()
        } else {
            onResendFailed()
        }
    }

    fun resendMail(emailAddress: String) {
        compositeDisposable +=
            emailSyncUpdater.updateEmailAndSync(emailAddress, EmailVerificationDeepLinkHelper.PIT_SIGNUP)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeBy(
                    onError = { onResendFailed() },
                    onSuccess = { onResendSuccess(it) }
                )
    }
}