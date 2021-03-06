package piuk.blockchain.android.ui.onboarding

import androidx.annotation.VisibleForTesting
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.rxkotlin.subscribeBy
import piuk.blockchain.android.data.biometrics.BiometricsController
import piuk.blockchain.androidcore.data.access.AccessState
import piuk.blockchain.androidcore.data.settings.SettingsDataManager
import piuk.blockchain.androidcoreui.ui.base.BasePresenter

internal class OnboardingPresenter constructor(
    private val biometricsController: BiometricsController,
    private val accessState: AccessState,
    private val settingsDataManager: SettingsDataManager
) : BasePresenter<OnboardingView>() {

    private val showEmail: Boolean by lazy { view.showEmail }
    private val showFingerprints: Boolean by lazy { view.showFingerprints }

    @VisibleForTesting
    internal var email: String? = null

    override fun onViewReady() {
        compositeDisposable += settingsDataManager.getSettings()
            .doAfterTerminate { this.checkAppState() }
            .subscribeBy(
                onNext = { settings -> email = settings.email },
                onError = { it.printStackTrace() }
            )
    }

    /**
     * Checks status of fingerprint hardware and either prompts the user to verify their fingerprint
     * or enroll one if the fingerprint sensor has never been set up.
     */
    internal fun onEnableFingerprintClicked() {
        if (biometricsController.isFingerprintAvailable) {
            val pin = accessState.pin

            if (pin.isNotEmpty()) {
                view.showFingerprintDialog(pin)
            } else {
                throw IllegalStateException("PIN not found")
            }
        } else if (biometricsController.isHardwareDetected) {
            // Hardware available but user has never set up fingerprints
            view.showEnrollFingerprintsDialog()
        } else {
            throw IllegalStateException("Fingerprint hardware not available, yet functions requiring hardware called.")
        }
    }

    /**
     * Sets fingerprint unlock enabled and clears the encrypted PIN if {@param enabled} is false
     *
     * @param enabled Whether or not the fingerprint unlock feature is set up
     */
    internal fun setFingerprintUnlockEnabled(enabled: Boolean) {
        biometricsController.setFingerprintUnlockEnabled(enabled)
    }

    private fun checkAppState() {
        when {
            showEmail -> view.showEmailPrompt()
            showFingerprints -> view.showFingerprintPrompt()
            else -> view.showEmailPrompt()
        }
    }

    internal fun disableAutoLogout() {
        accessState.canAutoLogout = false
    }

    internal fun enableAutoLogout() {
        accessState.canAutoLogout = true
    }
}
