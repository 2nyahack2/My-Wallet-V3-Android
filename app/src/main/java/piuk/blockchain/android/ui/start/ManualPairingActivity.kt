package piuk.blockchain.android.ui.start

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.view.MenuItem
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.widget.Toolbar
import com.blockchain.koin.scopedInject
import com.blockchain.preferences.WalletStatus
import kotlinx.android.synthetic.main.activity_manual_pairing.*
import org.json.JSONObject
import org.koin.android.ext.android.inject
import piuk.blockchain.android.R
import piuk.blockchain.android.ui.auth.PinEntryActivity
import piuk.blockchain.android.ui.base.MvpActivity
import piuk.blockchain.android.ui.customviews.getTwoFactorDialog
import piuk.blockchain.android.ui.start.PasswordRequiredActivity.Companion.TWO_FA_COUNTDOWN
import piuk.blockchain.android.ui.start.PasswordRequiredActivity.Companion.TWO_FA_STEP
import piuk.blockchain.androidcoreui.ui.customviews.ToastCustom
import piuk.blockchain.android.util.ViewUtils

class ManualPairingActivity : MvpActivity<ManualPairingView, ManualPairingPresenter>(),
    ManualPairingView {

    override val view: ManualPairingView = this
    override val presenter: ManualPairingPresenter by scopedInject()
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

    private val guid: String
        get() = wallet_id.text.toString()
    private val password: String
        get() = wallet_pass.text.toString()

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manual_pairing)

        val toolbar = findViewById<Toolbar>(R.id.toolbar_general)
        setupToolbar(toolbar, R.string.manual_pairing)

        command_next.setOnClickListener { presenter.onContinueClicked(guid, password) }

        wallet_pass.setOnEditorActionListener { _, i, _ ->
            if (i == EditorInfo.IME_ACTION_GO) {
                presenter.onContinueClicked(guid, password)
            }
            true
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun showToast(@StringRes messageId: Int, @ToastCustom.ToastType toastType: String) {
        ToastCustom.makeText(this, getString(messageId), ToastCustom.LENGTH_SHORT, toastType)
    }

    override fun showErrorToastWithParameter(@StringRes messageId: Int, message: String) {
        ToastCustom.makeText(this, getString(messageId, message), ToastCustom.LENGTH_SHORT, ToastCustom.TYPE_ERROR)
    }

    override fun goToPinPage() {
        startActivity(Intent(this, PinEntryActivity::class.java))
    }

    override fun updateWaitingForAuthDialog(secondsRemaining: Int) {
        updateProgressDialog(getString(R.string.check_email_to_auth_login) + " " + secondsRemaining)
    }

    override fun showTwoFactorCodeNeededDialog(
        responseObject: JSONObject,
        sessionId: String,
        authType: Int,
        guid: String,
        password: String
    ) {

        ViewUtils.hideKeyboard(this)

        val dialog = getTwoFactorDialog(this, authType, walletPrefs, positiveAction = {
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

    override fun resetPasswordField() {
        if (!isFinishing)
            wallet_pass.setText("")
    }

    public override fun onDestroy() {
        currentFocus?.let {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(it.windowToken, 0)
        }
        dismissProgressDialog()
        presenter.cancelAuthTimer()
        super.onDestroy()
    }
}
