package piuk.blockchain.android.ui.pairingcode

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import com.blockchain.koin.scopedInject
import com.blockchain.notifications.analytics.Analytics
import com.blockchain.notifications.analytics.AnalyticsEvents
import kotlinx.android.synthetic.main.activity_pairing_code.*
import kotlinx.android.synthetic.main.toolbar_general.*
import org.koin.android.ext.android.get
import piuk.blockchain.android.R
import piuk.blockchain.androidcore.utils.helperfunctions.consume
import piuk.blockchain.androidcoreui.ui.base.BaseMvpActivity
import piuk.blockchain.androidcoreui.ui.customviews.toast
import piuk.blockchain.android.util.gone
import piuk.blockchain.android.util.visible

@Suppress("UNUSED_PARAMETER")
class PairingCodeActivity : BaseMvpActivity<PairingCodeView, PairingCodePresenter>(),
    PairingCodeView {

    @Suppress("MemberVisibilityCanBePrivate")
    private val pairingCodePresenter: PairingCodePresenter by scopedInject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pairing_code)
        get<Analytics>().logEvent(AnalyticsEvents.WebLogin)

        setupToolbar(toolbar_general, R.string.pairing_code_log_in)

        pairing_first_step.text = presenter.firstStep

        button_qr_toggle.setOnClickListener { onClickQRToggle() }

        onViewReady()
    }

    override fun onSupportNavigateUp(): Boolean =
        consume { onBackPressed() }

    override fun onQrLoaded(bitmap: Bitmap) {
        tv_warning.setText(R.string.pairing_code_warning_2)
        iv_qr.visible()

        val width = resources.displayMetrics.widthPixels
        val height = width * bitmap.height / bitmap.width
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, width, height, true)

        iv_qr.setImageBitmap(scaledBitmap)
    }

    override fun showToast(message: Int, toastType: String) {
        toast(message, toastType)
    }

    override fun showProgressSpinner() {
        progress_bar.visible()
    }

    override fun hideProgressSpinner() {
        progress_bar.gone()
    }

    override fun enforceFlagSecure() = true

    override fun createPresenter() = pairingCodePresenter

    override fun getView(): PairingCodeView = this

    private fun onClickQRToggle() {
        if (main_layout.visibility == View.VISIBLE) {
            // Show pairing QR
            main_layout.gone()
            button_qr_toggle.setText(R.string.pairing_code_hide_qr)
            qr_layout.visible()
            iv_qr.gone()

            presenter.generatePairingQr()
        } else {
            // Hide pairing QR
            tv_warning.setText(R.string.pairing_code_warning_1)
            main_layout.visible()
            button_qr_toggle.setText(R.string.pairing_code_show_qr)
            qr_layout.gone()
        }
    }

    companion object {

        @JvmStatic
        fun start(context: Context) {
            val intent = Intent(context, PairingCodeActivity::class.java)
            context.startActivity(intent)
        }
    }
}