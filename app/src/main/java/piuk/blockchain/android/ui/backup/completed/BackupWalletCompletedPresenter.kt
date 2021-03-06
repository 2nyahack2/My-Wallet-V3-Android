package piuk.blockchain.android.ui.backup.completed

import com.blockchain.preferences.WalletStatus
import piuk.blockchain.androidcoreui.ui.base.BasePresenter

class BackupWalletCompletedPresenter(
    private val walletStatus: WalletStatus
) : BasePresenter<BackupWalletCompletedView>() {

    override fun onViewReady() {
        val lastBackup = walletStatus.lastBackupTime
        if (lastBackup != 0L) {
            view.showLastBackupDate(lastBackup)
        } else {
            view.hideLastBackupDate()
        }
    }
}
