package piuk.blockchain.android.ui.backup.completed

import com.blockchain.preferences.WalletStatus
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.verifyNoMoreInteractions
import com.nhaarman.mockito_kotlin.whenever
import org.amshove.kluent.mock
import org.junit.Before
import org.junit.Test

class BackupWalletCompletedPresenterTest {

    private lateinit var subject: BackupWalletCompletedPresenter
    private val view: BackupWalletCompletedView = mock()
    private val walletStatus: WalletStatus = mock()

    @Before
    fun setUp() {
        subject = BackupWalletCompletedPresenter(walletStatus)
        subject.initView(view)
    }

    @Test
    fun `onViewReady set backup date`() {
        // Arrange
        val date = 1499181978000L
        whenever(walletStatus.lastBackupTime).thenReturn(date)
        // Act
        subject.onViewReady()
        // Assert
        verify(walletStatus).lastBackupTime
        verifyNoMoreInteractions(walletStatus)
        verify(view).showLastBackupDate(date)
        verifyNoMoreInteractions(view)
    }

    @Test
    fun `onViewReady hide backup date`() {
        // Arrange
        whenever(walletStatus.lastBackupTime).thenReturn(0L)
        // Act
        subject.onViewReady()
        // Assert
        verify(walletStatus).lastBackupTime
        verifyNoMoreInteractions(walletStatus)
        verify(view).hideLastBackupDate()
        verifyNoMoreInteractions(view)
    }
}
