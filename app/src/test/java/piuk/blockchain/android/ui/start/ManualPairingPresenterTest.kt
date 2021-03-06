package piuk.blockchain.android.ui.start

import com.blockchain.android.testutils.rxInit
import com.blockchain.logging.CrashLogger
import com.blockchain.notifications.analytics.Analytics
import com.nhaarman.mockito_kotlin.whenever
import info.blockchain.wallet.payload.data.Wallet
import org.amshove.kluent.any
import org.amshove.kluent.mock
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import piuk.blockchain.android.util.AppUtil
import piuk.blockchain.androidcore.data.auth.AuthDataManager
import piuk.blockchain.androidcore.data.payload.PayloadDataManager
import piuk.blockchain.androidcore.utils.PrefsUtil

class ManualPairingPresenterTest {

    private lateinit var subject: ManualPairingPresenter

    private val view: ManualPairingView = mock()
    private val appUtil: AppUtil = mock()
    private val authDataManager: AuthDataManager = mock()
    private val payloadDataManager: PayloadDataManager = mock()
    private val wallet: Wallet = mock()
    private val prefsUtil: PrefsUtil = mock()
    private val analytics: Analytics = mock()
    private val crashLogger: CrashLogger = mock()

    @get:Rule
    val initSchedulers = rxInit {
        mainTrampoline()
        ioTrampoline()
    }

    @Before
    fun setUp() {
        subject = ManualPairingPresenter(
            appUtil,
            authDataManager,
            payloadDataManager,
            prefsUtil,
            analytics,
            crashLogger
        )
        subject.attachView(view)

        whenever(wallet.guid).thenReturn(GUID)
        whenever(wallet.sharedKey).thenReturn("shared_key")
        whenever(payloadDataManager.wallet).thenReturn(wallet)
    }

    /**
     * Password is missing, should trigger [ManualPairingActivity.showToast]
     */
    @Test
    fun onContinueClickedNoPassword() {
        // Arrange

        // Act
        subject.onContinueClicked(GUID, "")

        // Assert
        verify(view).showToast(any(), any())
        verify(analytics, never()).logEvent(any())
    }

    /**
     * GUID is missing, should trigger [ManualPairingActivity.showToast]
     */
    @Test
    fun onContinueClickedNoGuid() {
        // Arrange

        // Act
        subject.onContinueClicked("", PASSWORD)

        // Assert
        verify(view).showToast(any(), any())
        verify(analytics, never()).logEvent(any())
    }

    companion object {
        private const val GUID = "1234567890"
        private const val PASSWORD = "PASSWORD"
    }
}
