package piuk.blockchain.android.ui.backup.completed

import piuk.blockchain.androidcoreui.ui.base.View

interface BackupWalletCompletedView : View {

    fun showLastBackupDate(lastBackup: Long)

    fun hideLastBackupDate()
}