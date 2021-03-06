package piuk.blockchain.android.ui.launcher

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.text.InputType
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatEditText
import com.blockchain.koin.scopedInject
import com.blockchain.notifications.NotificationsUtil
import com.blockchain.notifications.analytics.Analytics
import com.blockchain.notifications.analytics.NotificationAppOpened
import kotlinx.android.synthetic.main.activity_launcher.*
import org.koin.android.ext.android.inject
import piuk.blockchain.android.R
import piuk.blockchain.android.simplebuy.SimpleBuyIntroFragment
import piuk.blockchain.android.ui.auth.PinEntryActivity
import piuk.blockchain.android.ui.home.MainActivity
import piuk.blockchain.android.ui.start.LandingActivity
import piuk.blockchain.android.ui.start.PasswordRequiredActivity
import piuk.blockchain.android.ui.upgrade.UpgradeWalletActivity
import piuk.blockchain.androidcoreui.ui.base.BaseMvpActivity
import piuk.blockchain.androidcoreui.ui.customviews.toast
import piuk.blockchain.android.util.ViewUtils
import piuk.blockchain.android.util.visibleIf
import timber.log.Timber

class LauncherActivity : BaseMvpActivity<LauncherView, LauncherPresenter>(), LauncherView {

    private val analytics: Analytics by inject()
    private val launcherPresenter: LauncherPresenter by scopedInject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_launcher)

        if (intent.hasExtra(NotificationsUtil.INTENT_FROM_NOTIFICATION) &&
            intent.getBooleanExtra(NotificationsUtil.INTENT_FROM_NOTIFICATION, false)
        ) {
            analytics.logEvent(NotificationAppOpened)
        }

        Handler().postDelayed(DelayStartRunnable(this), 500)
    }

    override fun logScreenView() = Unit

    override fun createPresenter() = launcherPresenter

    override fun getView() = this

    override fun getPageIntent(): Intent = intent

    override fun onNoGuid() = LandingActivity.start(this)

    override fun onRequestPin() {
        startSingleActivity(PinEntryActivity::class.java, null)
    }

    override fun onCorruptPayload() {
        AlertDialog.Builder(this, R.style.AlertDialogStyle)
            .setTitle(R.string.app_name)
            .setMessage(getString(R.string.not_sane_error))
            .setCancelable(false)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                presenter.clearCredentialsAndRestart()
            }
            .show()
    }

    override fun onRequestUpgrade() {
        startActivity(Intent(this, UpgradeWalletActivity::class.java))
        finish()
    }

    override fun onStartMainActivity(uri: Uri?, launchBuySellIntro: Boolean) {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
            data = uri
            putExtra(MainActivity.START_BUY_SELL_INTRO_KEY, launchBuySellIntro)
        }
        startActivity(intent)
    }

    override fun launchBuySellIntro() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.content_frame, SimpleBuyIntroFragment(), SimpleBuyIntroFragment::class.simpleName)
            .commitAllowingStateLoss()
    }

    override fun onReEnterPassword() {
        startSingleActivity(PasswordRequiredActivity::class.java, null)
    }

    override fun showToast(message: Int, toastType: String) = toast(message, toastType)

    override fun showMetadataNodeFailure() {
        if (!isFinishing) {
            AlertDialog.Builder(this, R.style.AlertDialogStyle)
                .setTitle(R.string.app_name)
                .setMessage(R.string.metadata_load_failure)
                .setPositiveButton(R.string.retry) { _, _ -> presenter.onViewReady() }
                .setNegativeButton(R.string.exit) { _, _ -> presenter.clearLoginState() }
                .setCancelable(false)
                .create()
                .show()
        }
    }

    override fun showSecondPasswordDialog() {
        val editText = AppCompatEditText(this)
        editText.setHint(R.string.password)
        editText.inputType =
            InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_VARIATION_PASSWORD or
                    InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS

        val frameLayout = ViewUtils.getAlertDialogPaddedView(this, editText)

        AlertDialog.Builder(this, R.style.AlertDialogStyle)
            .setTitle(R.string.second_password_dlg_title)
            .setMessage(R.string.eth_second_password_prompt)
            .setView(frameLayout)
            .setCancelable(false)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                ViewUtils.hideKeyboard(this)
                presenter.decryptAndSetupMetadata(editText.text.toString())
            }
            .create()
            .show()
    }

    override fun updateProgressVisibility(show: Boolean) {
        progress.visibleIf { show }
    }

    private fun startSingleActivity(clazz: Class<*>, extras: Bundle?, uri: Uri? = null) {
        val intent = Intent(this, clazz).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
            data = uri
        }
        Timber.d("DeepLink: Starting Activity $clazz with: $uri")
        extras?.let { intent.putExtras(extras) }
        startActivity(intent)
    }

    private class DelayStartRunnable internal constructor(
        private val activity: LauncherActivity
    ) : Runnable {

        override fun run() {
            if (activity.presenter != null && !activity.isFinishing) {
                activity.onViewReady()
            }
        }
    }
}
