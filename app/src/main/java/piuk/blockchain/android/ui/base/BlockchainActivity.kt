package piuk.blockchain.android.ui.base

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.MotionEvent
import android.view.WindowManager
import androidx.annotation.CallSuper
import androidx.annotation.StringRes
import androidx.annotation.UiThread
import androidx.appcompat.app.AlertDialog
import com.blockchain.notifications.analytics.Analytics
import com.blockchain.preferences.SecurityPrefs
import com.blockchain.ui.ActivityIndicator
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.rxkotlin.subscribeBy
import org.koin.android.ext.android.inject
import piuk.blockchain.android.R
import piuk.blockchain.android.ui.customviews.dialogs.MaterialProgressDialog
import piuk.blockchain.android.util.AppUtil
import piuk.blockchain.androidcore.data.access.LogoutTimer
import piuk.blockchain.androidcoreui.ApplicationLifeCycle
import piuk.blockchain.androidcoreui.ui.base.ToolBarActivity

/**
 * A base Activity for all activities which need auth timeouts & screenshot prevention
 */

abstract class BlockchainActivity : ToolBarActivity() {

    private val logoutTimer: LogoutTimer by inject()
    private val securityPrefs: SecurityPrefs by inject()

    val analytics: Analytics by inject()
    val appUtil: AppUtil by inject()

    protected abstract val alwaysDisableScreenshots: Boolean

    private val activityIndicator = ActivityIndicator()
    private val compositeDisposable = CompositeDisposable()

    private val enableScreenshots: Boolean
        get() = securityPrefs.isUnderTest || (securityPrefs.areScreenshotsEnabled && !alwaysDisableScreenshots)

    protected open val enableLogoutTimer: Boolean = true

    private var alertDialog: AlertDialog? = null
        @UiThread
        set(dlg) {
            if (!(isFinishing || isDestroyed)) { // Prevent Not Attached To Window crash
                alertDialog?.dismiss() // Prevent multiple popups
            }
            field = dlg
        }
        @UiThread
        get() = field

    private var progressDialog: MaterialProgressDialog? = null

    @CallSuper
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lockScreenOrientation()
    }

    /**
     * Allows you to disable Portrait orientation lock on a per-Activity basis.
     */
    protected open fun lockScreenOrientation() {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }

    @CallSuper
    override fun onResume() {
        super.onResume()
        stopLogoutTimer()
        ApplicationLifeCycle.getInstance().onActivityResumed()

        if (enableScreenshots) {
            enableScreenshots()
        } else {
            disallowScreenshots()
        }
        appUtil.activityIndicator = activityIndicator

        compositeDisposable += activityIndicator.loading.observeOn(AndroidSchedulers.mainThread()).subscribeBy {
            if (it == true) {
                showLoading()
            } else {
                hideLoading()
            }
        }
    }

    protected open fun showLoading() {}
    protected open fun hideLoading() {}

    @CallSuper
    override fun onPause() {
        super.onPause()
        startLogoutTimer()
        ApplicationLifeCycle.getInstance().onActivityPaused()
        compositeDisposable.clear()
    }

    private fun startLogoutTimer() {
        if (enableLogoutTimer) {
            logoutTimer.start()
        }
    }

    private fun stopLogoutTimer() = logoutTimer.stop()

    private fun disallowScreenshots() =
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

    private fun enableScreenshots() =
        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)

    // Test for screen overlays before user creates a new wallet or enters confidential information
    override fun dispatchTouchEvent(event: MotionEvent): Boolean =
        detectObscuredWindow(event) || super.dispatchTouchEvent(event)

    // Detect if touch events are being obscured by hidden overlays - These could be used for tapjacking
    // There is a possible problem, here, in that once overlays have been accepted, new apps could install
    // an untrusted overlay. TODO: Think about how best to deal with this
    private fun detectObscuredWindow(event: MotionEvent): Boolean {
        if (!securityPrefs.trustScreenOverlay && event.isObscuredTouch()) {
            showAlert(overlayAlertDlg())
            return true
        }
        return false
    }

    private fun overlayAlertDlg() =
        AlertDialog.Builder(this, R.style.AlertDialogStyle)
            .setTitle(R.string.screen_overlay_warning)
            .setMessage(R.string.screen_overlay_note)
            .setCancelable(false)
            .setPositiveButton(R.string.dialog_continue) { _, _ ->
                securityPrefs.trustScreenOverlay = true //
            }
            .setNegativeButton(R.string.exit) { _, _ -> this.finish() }
            .create()

    @UiThread
    fun showAlert(dlg: AlertDialog) {
        alertDialog = dlg
        alertDialog?.show()
    }

    @UiThread
    fun clearAlert() {
        alertDialog = null
    }

    @UiThread
    fun showProgressDialog(@StringRes messageId: Int, onCancel: (() -> Unit)? = null) {
        dismissProgressDialog()
        progressDialog = MaterialProgressDialog(this).apply {
            setCancelable(onCancel != null)
            setMessage(getString(messageId))
            onCancel?.let { setOnCancelListener(it) }
            if (!isFinishing) show()
        }
    }

    @UiThread
    fun dismissProgressDialog() {
        progressDialog?.dismiss()
        progressDialog = null
    }

    @UiThread
    fun updateProgressDialog(msg: String) {
        progressDialog?.setMessage(msg)
    }

    @UiThread
    fun showBottomSheet(bottomSheet: BottomSheetDialogFragment) =
        bottomSheet.show(supportFragmentManager, BOTTOM_DIALOG)

    @UiThread
    fun clearBottomSheet() {
        val dlg = supportFragmentManager.findFragmentByTag(BOTTOM_DIALOG)

        dlg?.let {
            (it as? SlidingModalBottomDialog)?.dismiss()
                ?: throw IllegalStateException("Fragment is not a $BOTTOM_DIALOG")
        }
    }

    override fun onBackPressed() {
        val fragments = supportFragmentManager.fragments
        for (fragment in fragments) {
            if (fragment is FlowFragment && backActionShouldBeHandledByFragment(fragment)) {
                return
            }
        }
        super.onBackPressed()
    }

    private fun backActionShouldBeHandledByFragment(flowFragment: FlowFragment): Boolean =
        flowFragment.onBackPressed() && handleByScreenOrPop(flowFragment)

    private fun handleByScreenOrPop(flowFragment: FlowFragment): Boolean =
        flowFragment.backPressedHandled() || pop()

    private fun pop(): Boolean {
        val backStackEntryCount = supportFragmentManager.backStackEntryCount
        if (backStackEntryCount > 1) {
            supportFragmentManager.popBackStack()
            return true
        }
        return false
    }

    companion object {
        private const val BOTTOM_DIALOG = "BOTTOM_DIALOG"
    }
}

private fun MotionEvent.isObscuredTouch() = (flags and MotionEvent.FLAG_WINDOW_IS_OBSCURED != 0)