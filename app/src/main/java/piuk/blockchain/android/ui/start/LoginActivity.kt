package piuk.blockchain.android.ui.start

import android.content.Intent
import android.os.Bundle
import com.blockchain.koin.scopedInject
import kotlinx.android.synthetic.main.activity_login.*
import kotlinx.android.synthetic.main.toolbar_general.*
import piuk.blockchain.android.R
import piuk.blockchain.android.ui.auth.PinEntryActivity
import piuk.blockchain.android.ui.scan.QrScanActivity
import piuk.blockchain.androidcore.utils.helperfunctions.consume
import piuk.blockchain.android.ui.base.MvpActivity
import piuk.blockchain.android.ui.scan.QrExpected
import piuk.blockchain.android.ui.scan.QrScanActivity.Companion.getRawScanData
import piuk.blockchain.androidcoreui.ui.customviews.toast

class LoginActivity : MvpActivity<LoginView, LoginPresenter>(), LoginView {

    override val presenter: LoginPresenter by scopedInject()
    override val view: LoginView = this

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        setupToolbar(toolbar_general, R.string.login_auto_pair_title)

        step_one.text = getString(R.string.pair_wallet_step_1, WEB_WALLET_URL_PROD)

        btn_manual_pair.setOnClickListener { onClickManualPair() }
        btn_scan_qr.setOnClickListener { startScanActivity() }
    }

    override fun showToast(message: Int, toastType: String) = toast(message, toastType)

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK && requestCode == QrScanActivity.SCAN_URI_RESULT) {
            if (data.getRawScanData() != null) {
                presenter.pairWithQR(data.getRawScanData())
            }
        }
    }

    override fun startPinEntryActivity() {
        val intent = Intent(this, PinEntryActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    override fun onSupportNavigateUp() = consume { onBackPressed() }

    private fun onClickManualPair() {
        startActivity(Intent(this, ManualPairingActivity::class.java))
    }

    private fun startScanActivity() {
        QrScanActivity.start(this, QrExpected.LEGACY_PAIRING_QR)
    }

    companion object {
        private const val WEB_WALLET_URL_PROD = "https://login.blockchain.com/"
    }
}