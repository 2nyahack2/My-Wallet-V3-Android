package piuk.blockchain.androidcore.data.walletoptions

import com.blockchain.android.testutils.rxInit
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import info.blockchain.wallet.api.data.AndroidUpgrade
import info.blockchain.wallet.api.data.UpdateType
import info.blockchain.wallet.api.data.WalletOptions
import io.reactivex.Observable
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import piuk.blockchain.androidcore.data.auth.AuthService
import piuk.blockchain.androidcore.data.settings.SettingsDataManager
import kotlin.test.assertEquals

class WalletOptionsDataManagerTest {

    private lateinit var subject: WalletOptionsDataManager

    private val authService: AuthService = mock()
    private var walletOptionsState = WalletOptionsState()
    private val mockSettingsDataManager: SettingsDataManager =
        mock(defaultAnswer = Mockito.RETURNS_DEEP_STUBS)
    private val explorerUrl: String = "https://blockchain.info/"

    @Suppress("unused")
    @get:Rule
    val initSchedulers = rxInit {
        ioTrampoline()
    }

    @Before
    fun setUp() {
        walletOptionsState.wipe()
        subject = WalletOptionsDataManager(
            authService,
            walletOptionsState,
            mockSettingsDataManager,
            explorerUrl
        )
    }

    @Test
    fun `checkForceUpgrade missing androidUpgrade JSON object`() {
        // Arrange
        val walletOptions: WalletOptions = mock()
        val versionName = "360.0.1"
        whenever(walletOptions.androidUpdate).thenReturn(AndroidUpgrade())
        whenever(authService.getWalletOptions()).thenReturn(Observable.just(walletOptions))
        // Act
        val testObserver = subject.checkForceUpgrade(versionName).test()
        // Assert
        testObserver.assertComplete()
        testObserver.assertNoErrors()
        testObserver.assertValue(UpdateType.NONE)
    }

    @Test
    fun `checkForceUpgrade empty androidUpgrade JSON object`() {
        // Arrange
        val walletOptions: WalletOptions = mock()
        whenever(walletOptions.androidUpdate).thenReturn(AndroidUpgrade())
        val versionName = "360.0.1"
        whenever(authService.getWalletOptions()).thenReturn(Observable.just(walletOptions))
        // Act
        val testObserver = subject.checkForceUpgrade(versionName).test()
        // Assert
        testObserver.assertComplete()
        testObserver.assertNoErrors()
        testObserver.assertValue(UpdateType.NONE)
    }

    @Test
    fun `checkForceUpgrade ignore minSdk despite versionCode unsupported`() {
        // Arrange
        val walletOptions: WalletOptions = mock()
        whenever(walletOptions.androidUpdate).thenReturn(
            AndroidUpgrade("361.0.1", "recommended")
        )
        val versionName = "360.0.1"
        whenever(authService.getWalletOptions()).thenReturn(Observable.just(walletOptions))
        // Act
        val testObserver = subject.checkForceUpgrade(versionName).test()
        // Assert
        testObserver.assertComplete()
        testObserver.assertNoErrors()
        testObserver.assertValue(UpdateType.RECOMMENDED)
    }

    @Test
    fun `checkForceUpgrade should force upgrade`() {
        // Arrange
        val walletOptions: WalletOptions = mock()
        whenever(walletOptions.androidUpdate).thenReturn(
            AndroidUpgrade("361.0.1", "force")
        )
        val versionName = "360.0.1"
        whenever(authService.getWalletOptions()).thenReturn(Observable.just(walletOptions))
        // Act
        val testObserver = subject.checkForceUpgrade(versionName).test()
        // Assert
        testObserver.assertComplete()
        testObserver.assertNoErrors()
        testObserver.assertValue(UpdateType.FORCE)
    }

    @Test
    fun `getBuyWebviewWalletLink wallet-options set`() {
        // Arrange
        val walletOptionsRoot = "https://blockchain.com/wallet"
        val mockOptions: WalletOptions = mock()
        whenever(mockOptions.buyWebviewWalletLink).thenReturn(walletOptionsRoot)
        whenever(authService.getWalletOptions()).thenReturn(Observable.just(mockOptions))
        // Act
        val result = subject.getBuyWebviewWalletLink()
        // Assert
        assertEquals("https://blockchain.com/wallet/#/intermediate", result)
    }

    @Test
    fun `getBuyWebviewWalletLink wallet-options unset`() {
        // Arrange
        val walletOptionsRoot = null
        val mockOptions: WalletOptions = mock()
        whenever(mockOptions.buyWebviewWalletLink).thenReturn(walletOptionsRoot)
        whenever(authService.getWalletOptions()).thenReturn(Observable.just(mockOptions))
        // Act
        val result = subject.getBuyWebviewWalletLink()
        // Assert
        assertEquals("https://blockchain.info/wallet/#/intermediate", result)
    }
}
