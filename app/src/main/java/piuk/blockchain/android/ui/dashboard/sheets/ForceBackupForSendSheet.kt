package piuk.blockchain.android.ui.dashboard.sheets

import android.content.DialogInterface
import android.view.View
import piuk.blockchain.android.simplebuy.SimpleBuyAnalytics
import com.blockchain.preferences.DashboardPrefs
import kotlinx.android.synthetic.main.dialog_backup_for_send.view.*
import kotlinx.android.synthetic.main.dialog_custodial_intro.view.cta_button
import org.koin.android.ext.android.inject
import piuk.blockchain.android.R
import piuk.blockchain.android.coincore.AssetAction
import piuk.blockchain.android.coincore.SingleAccount
import piuk.blockchain.android.ui.base.SlidingModalBottomDialog

class ForceBackupForSendSheet : SlidingModalBottomDialog() {

    interface Host : SlidingModalBottomDialog.Host {
        fun startBackupForTransfer()
        fun startTransferFunds(account: SingleAccount, action: AssetAction)
    }

    private lateinit var account: SingleAccount
    private lateinit var action: AssetAction

    private val dashboardPrefs: DashboardPrefs by inject()

    override val host: Host by lazy {
        super.host as? Host ?: throw IllegalStateException("Host fragment is not a ForceBackupForSendSheet.Host")
    }

    override val layoutResource: Int = R.layout.dialog_backup_for_send

    override fun initControls(view: View) {
        analytics.logEvent(SimpleBuyAnalytics.BACK_UP_YOUR_WALLET_SHOWN)

        view.cta_button.setOnClickListener { onCtaClick() }

        view.cta_later.setOnClickListener {
            reduceAttemptsAndNavigate()
        }

        val remainingSendsWithoutBackup = dashboardPrefs.remainingSendsWithoutBackup

        if (remainingSendsWithoutBackup == 0) {
            view.cta_later.isEnabled = false
            view.backup_sends_label.text = getString(R.string.backup_before_send_now_label)
        } else {
            view.cta_later.isEnabled = true
            view.backup_sends_label.text =
                resources.getQuantityString(R.plurals.backup_before_send_later_label, remainingSendsWithoutBackup,
                    remainingSendsWithoutBackup)
        }
    }

    private fun reduceAttemptsAndNavigate() {
        if (dashboardPrefs.remainingSendsWithoutBackup > 0) {
            dashboardPrefs.remainingSendsWithoutBackup = dashboardPrefs.remainingSendsWithoutBackup - 1
            host.startTransferFunds(account, action)
        } else {
            dismiss()
        }
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        reduceAttemptsAndNavigate()
    }

    override fun onSheetHidden() {
        super.onSheetHidden()
        dialog?.let {
            onCancel(it)
        }
    }

    private fun onCtaClick() {
        analytics.logEvent(SimpleBuyAnalytics.BACK_UP_YOUR_WALLET_CLICKED)
        dismiss()
        host.startBackupForTransfer()
    }

    companion object {
        fun newInstance(backupDetails: BackupDetails): ForceBackupForSendSheet =
            ForceBackupForSendSheet().apply {
                account = backupDetails.account
                action = backupDetails.action
            }
    }
}

data class BackupDetails(
    val account: SingleAccount,
    val action: AssetAction
)
