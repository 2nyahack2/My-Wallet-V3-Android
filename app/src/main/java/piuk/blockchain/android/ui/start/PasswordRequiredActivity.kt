package piuk.blockchain.android.ui.start

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import com.blockchain.koin.scopedInject
import com.blockchain.preferences.WalletStatus
import kotlinx.android.synthetic.main.activity_password_required.*
import org.json.JSONObject
import org.koin.android.ext.android.inject
import piuk.blockchain.android.R
import piuk.blockchain.android.ui.auth.PinEntryActivity
import piuk.blockchain.android.ui.base.MvpActivity
import piuk.blockchain.android.ui.customviews.getTwoFactorDialog
import piuk.blockchain.android.ui.launcher.LauncherActivity
import piuk.blockchain.android.ui.recover.RecoverFundsActivity
import piuk.blockchain.androidcoreui.ui.customviews.ToastCustom
import piuk.blockchain.android.util.ViewUtils

class PasswordRequiredActivity : MvpActivity<PasswordRequiredView, PasswordRequiredPresenter>(),
    PasswordRequiredView {

    override val presenter: PasswordRequiredPresenter by scopedInject()
    override val view: PasswordRequiredView = this
    private val walletPrefs: WalletStatus by inject()

    private var isTwoFATimerRunning = false
    private val twoFATimer by lazy {
        object : CountDownTimer(TWO_FA_COUNTDOWN, TWO_FA_STEP) {
            override fun onTick(millisUntilFinished: Long) {
                isTwoFATimerRunning = true
            }

            override fun onFinish() {
                isTwoFATimerRunning = false
                walletPrefs.setResendSmsRetries(3)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_password_required)

        val toolbar = findViewById<Toolbar>(R.id.toolbar_general)
        setupToolbar(toolbar, R.string.confirm_password)
        supportActionBar!!.setDisplayHomeAsUpEnabled(false)

        button_continue.setOnClickListener { presenter.onContinueClicked(field_password.text.toString()) }
        button_forget.setOnClickListener { presenter.onForgetWalletClicked() }
        button_recover.setOnClickListener { launchRecoveryFlow() }
    }

    override fun onResume() {
        super.onResume()
        presenter.loadWalletGuid()
    }

    override fun showToast(@StringRes messageId: Int, @ToastCustom.ToastType toastType: String) {
        ToastCustom.makeText(this, getString(messageId), ToastCustom.LENGTH_SHORT, toastType)
    }

    override fun showErrorToastWithParameter(@StringRes messageId: Int, message: String) {
        ToastCustom.makeText(this, getString(messageId, message), ToastCustom.LENGTH_SHORT, ToastCustom.TYPE_ERROR)
    }

    override fun restartPage() {
        val intent = Intent(this, LauncherActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    override fun resetPasswordField() {
        if (!isFinishing) field_password.setText("")
    }

    override fun showWalletGuid(guid: String) {
        wallet_identifier.text = guid
    }

    override fun goToPinPage() {
        startActivity(Intent(this, PinEntryActivity::class.java))
    }

    override fun updateWaitingForAuthDialog(secondsRemaining: Int) =
        updateProgressDialog(getString(R.string.check_email_to_auth_login) + " " + secondsRemaining)

    override fun showForgetWalletWarning(onForgetConfirmed: () -> Unit) {
        showAlert(
            AlertDialog.Builder(this, R.style.AlertDialogStyle)
                .setTitle(R.string.warning)
                .setMessage(R.string.forget_wallet_warning)
                .setPositiveButton(R.string.forget_wallet) { _, _ -> onForgetConfirmed() }
                .setNegativeButton(android.R.string.cancel) { _, _ -> }
                .create()
        )
    }

    override fun showTwoFactorCodeNeededDialog(
        responseObject: JSONObject,
        sessionId: String,
        authType: Int,
        guid: String,
        password: String
    ) {
        ViewUtils.hideKeyboard(this)

        val dialog = getTwoFactorDialog(this, authType,
            walletPrefs,
            positiveAction = {
                presenter.submitTwoFactorCode(responseObject,
                    sessionId,
                    guid,
                    password,
                    it
                )
            }, resendAction = { limitReached ->
            if (!limitReached) {
                presenter.requestNew2FaCode(password, guid)
            } else {
                ToastCustom.makeText(this, getString(R.string.two_factor_retries_exceeded),
                    Toast.LENGTH_SHORT, ToastCustom.TYPE_ERROR)
                if (!isTwoFATimerRunning) {
                    twoFATimer.start()
                }
            }
        })

        showAlert(dialog)
    }

    override fun onDestroy() {
        dismissProgressDialog()
        presenter.cancelAuthTimer()
        super.onDestroy()
    }

    private fun launchRecoveryFlow() = RecoverFundsActivity.start(this)

    companion object {
        const val TWO_FA_COUNTDOWN = 60000L
        const val TWO_FA_STEP = 1000L
    }
}
