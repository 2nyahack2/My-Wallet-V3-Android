package piuk.blockchain.android.ui.start

import com.blockchain.preferences.SecurityPrefs
import piuk.blockchain.android.ui.base.MvpPresenter
import piuk.blockchain.android.ui.base.MvpView
import piuk.blockchain.android.util.RootUtil
import piuk.blockchain.androidcore.data.api.EnvironmentConfig
import piuk.blockchain.androidcoreui.ui.customviews.ToastCustom

interface LandingView : MvpView {
    fun showDebugMenu()
    fun showToast(message: String, @ToastCustom.ToastType toastType: String)
    fun showIsRootedWarning()
}

class LandingPresenter(
    private val environmentSettings: EnvironmentConfig,
    private val prefs: SecurityPrefs,
    private val rootUtil: RootUtil
) : MvpPresenter<LandingView>() {

    override val alwaysDisableScreenshots = false
    override val enableLogoutTimer = false

    override fun onViewAttached() {
        if (environmentSettings.isRunningInDebugMode()) {
            view?.let {
                it.showToast(
                    "Current environment: ${environmentSettings.environment.getName()}",
                    ToastCustom.TYPE_GENERAL
                )
                it.showDebugMenu()
            }
        }
    }

    override fun onViewDetached() { /* no-op */ }

    internal fun checkForRooted() {
        if (rootUtil.isDeviceRooted && !prefs.disableRootedWarning) {
            view?.showIsRootedWarning()
        }
    }
}