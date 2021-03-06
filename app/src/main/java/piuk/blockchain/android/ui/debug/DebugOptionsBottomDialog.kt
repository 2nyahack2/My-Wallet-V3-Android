package piuk.blockchain.android.ui.debug

import android.app.LauncherActivity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import com.blockchain.koin.scopedInject
import com.blockchain.logging.CrashLogger
import com.blockchain.preferences.CurrencyPrefs
import com.blockchain.preferences.SimpleBuyPrefs
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import io.reactivex.disposables.CompositeDisposable
import kotlinx.android.synthetic.main.dialog_debug_options.*
import org.koin.android.ext.android.inject
import piuk.blockchain.android.R
import piuk.blockchain.android.ui.dashboard.announcements.AnnouncementList
import piuk.blockchain.android.ui.dashboard.announcements.DismissRecorder
import piuk.blockchain.android.util.AppUtil
import piuk.blockchain.androidcore.data.access.AccessState
import piuk.blockchain.androidcore.utils.PersistentPrefs
import piuk.blockchain.androidcoreui.ui.customviews.toast

class DebugOptionsBottomDialog : BottomSheetDialogFragment() {

    private val compositeDisposable = CompositeDisposable()
    private val prefs: PersistentPrefs by inject()
    private val appUtil: AppUtil by inject()
    private val loginState: AccessState by inject()
    private val crashLogger: CrashLogger by inject()
    private val simpleBuyPrefs: SimpleBuyPrefs by inject()
    private val currencyPrefs: CurrencyPrefs by inject()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.dialog_debug_options, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        btn_rnd_device_id.setOnClickListener { onRndDeviceId() }
        btn_reset_wallet.setOnClickListener { onResetWallet() }
        btn_reset_announce.setOnClickListener { onResetAnnounce() }
        btn_reset_prefs.setOnClickListener { onResetPrefs() }
        clear_simple_buy_state.setOnClickListener { clearSimpleBuyState() }
        btn_store_linkId.setOnClickListener { prefs.pitToWalletLinkId = "11111111-2222-3333-4444-55556666677" }
        device_currency.text = "Select a new currency. Current one is ${currencyPrefs.selectedFiatCurrency}"
        firebase_token.text = prefs.firebaseToken

        swap_switch.setOnCheckedChangeListener { _, isChecked ->
            prefs.newSwapEnabled = isChecked
        }

        swap_switch.isChecked = prefs.newSwapEnabled

        radio_eur.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                currencyPrefs.selectedFiatCurrency = "EUR"
                context?.toast("Currency changed to EUR")
                dismiss()
            }
        }

        radio_usd.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                currencyPrefs.selectedFiatCurrency = "USD"
                context?.toast("Currency changed to USD")
                dismiss()
            }
        }

        radio_gbp.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                currencyPrefs.selectedFiatCurrency = "GBP"
                context?.toast("Currency changed to GBP")
                dismiss()
            }
        }
    }

    private fun clearSimpleBuyState() {
        simpleBuyPrefs.clearState()
        context?.toast("Local SB State cleared")
        dismiss()
    }

    private fun onRndDeviceId() {
        prefs.qaRandomiseDeviceId = true
        context?.toast("Device ID randomisation enabled")
        dismiss()
    }

    private fun onResetWallet() {
        appUtil.clearCredentialsAndRestart(LauncherActivity::class.java)
        dismiss()
    }

    private fun onResetAnnounce() {
        val announcementList: AnnouncementList by scopedInject()
        val dismissRecorder: DismissRecorder by scopedInject()

        dismissRecorder.undismissAll(announcementList)

        prefs.resetTour()
        context?.toast("Announcement reset")
        dismiss()
    }

    private fun onResetPrefs() {
        prefs.clear()

        crashLogger.logEvent("debug clear prefs. Pin reset")
        loginState.clearPin()

        context?.toast("Prefs Reset")
        dismiss()
    }

    override fun onPause() {
        compositeDisposable.clear()
        super.onPause()
    }

    companion object {
        fun show(fm: FragmentManager) {
            DebugOptionsBottomDialog().show(fm, "DEBUG_DIALOG")
        }
    }
}