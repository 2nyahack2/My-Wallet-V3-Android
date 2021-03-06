package piuk.blockchain.android.ui.start

import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.verifyNoMoreInteractions
import com.nhaarman.mockito_kotlin.verifyZeroInteractions
import com.nhaarman.mockito_kotlin.whenever
import info.blockchain.wallet.api.Environment
import org.amshove.kluent.mock
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import piuk.blockchain.android.util.RootUtil
import piuk.blockchain.androidcore.data.api.EnvironmentConfig
import piuk.blockchain.androidcore.utils.PersistentPrefs
import piuk.blockchain.androidcoreui.ui.customviews.ToastCustom

class LandingPresenterTest {

    private lateinit var subject: LandingPresenter
    private val view: LandingView = mock()
    private val environmentSettings: EnvironmentConfig =
        mock(defaultAnswer = Mockito.RETURNS_DEEP_STUBS)

    private val prefs: PersistentPrefs = mock()
    private val rootUtil: RootUtil = mock()

    @Before
    fun setUp() {
        subject = LandingPresenter(environmentSettings, prefs, rootUtil)
    }

    @Test
    fun `onViewReady show debug`() {
        // Arrange
        whenever(environmentSettings.isRunningInDebugMode()).thenReturn(true)
        val environment = Environment.fromString("env_prod")
        whenever(environmentSettings.environment).thenReturn(environment)
        // Act
        subject.attachView(view)
        // Assert
        verify(view).showToast("Current environment: env_prod", ToastCustom.TYPE_GENERAL)
        verify(view).showDebugMenu()
    }

    @Test
    fun `onViewReady no debug`() {
        // Arrange
        whenever(environmentSettings.isRunningInDebugMode()).thenReturn(false)
        // Act
        subject.attachView(view)
        // Assert
        verifyNoMoreInteractions(view)
    }

    @Test
    fun `device is rooted and warnings are off - don't show dialog`() {
        // Arrange
        whenever(rootUtil.isDeviceRooted).thenReturn(true)
        whenever(prefs.disableRootedWarning).thenReturn(false)

        subject.attachView(view)

        // Act
        subject.checkForRooted()

        // Assert
        verify(view).showIsRootedWarning()
        verifyNoMoreInteractions(view)
    }

    @Test
    fun `device is not rooted and warnings are off - don't show dialog`() {
        // Arrange
        whenever(rootUtil.isDeviceRooted).thenReturn(false)
        whenever(prefs.disableRootedWarning).thenReturn(false)

        subject.attachView(view)

        // Act
        subject.checkForRooted()

        // Assert
        verifyZeroInteractions(view)
    }

    @Test
    fun `device is rooted and warnings are on - show dialog`() {
        // Arrange
        whenever(rootUtil.isDeviceRooted).thenReturn(true)
        whenever(prefs.disableRootedWarning).thenReturn(false)

        subject.attachView(view)

        // Act
        subject.checkForRooted()

        // Assert
        verify(view).showIsRootedWarning()
        verifyNoMoreInteractions(view)
    }

    @Test
    fun `device is not rooted and warnings are on - don't show dialog`() {
        // Arrange
        whenever(rootUtil.isDeviceRooted).thenReturn(false)
        whenever(prefs.disableRootedWarning).thenReturn(true)

        // Act
        subject.checkForRooted()

        // Assert
        verifyZeroInteractions(view)
    }
}
