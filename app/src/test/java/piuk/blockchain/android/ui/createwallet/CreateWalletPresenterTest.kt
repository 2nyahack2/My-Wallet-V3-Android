package piuk.blockchain.android.ui.createwallet

import com.blockchain.notifications.analytics.Analytics
import com.blockchain.notifications.analytics.AnalyticsEvents
import com.blockchain.preferences.WalletStatus
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.verifyNoMoreInteractions
import com.nhaarman.mockito_kotlin.verifyZeroInteractions
import com.nhaarman.mockito_kotlin.whenever
import info.blockchain.wallet.payload.data.Wallet
import io.reactivex.Observable
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import piuk.blockchain.android.R
import piuk.blockchain.android.util.AppUtil
import piuk.blockchain.androidcore.data.access.AccessState
import piuk.blockchain.androidcore.data.api.EnvironmentConfig
import piuk.blockchain.androidcore.data.payload.PayloadDataManager
import piuk.blockchain.androidcore.utils.PersistentPrefs
import piuk.blockchain.androidcore.utils.PrngFixer

class CreateWalletPresenterTest {

    private lateinit var subject: CreateWalletPresenter
    private var view: CreateWalletView = mock()
    private var appUtil: AppUtil = mock()
    private var accessState: AccessState = mock()
    private var payloadDataManager: PayloadDataManager = mock(defaultAnswer = Mockito.RETURNS_DEEP_STUBS)
    private var prefsUtil: PersistentPrefs = mock()
    private var prngFixer: PrngFixer = mock()
    private var analytics: Analytics = mock()
    private var walletPrefs: WalletStatus = mock()
    private var environmentConfig: EnvironmentConfig = mock()

    @Before
    fun setUp() {
        subject = CreateWalletPresenter(
            payloadDataManager = payloadDataManager,
            prefs = prefsUtil,
            appUtil = appUtil,
            accessState = accessState,
            prngFixer = prngFixer,
            analytics = analytics,
            walletPrefs = walletPrefs,
            environmentConfig = environmentConfig
        )
        subject.initView(view)
    }

    @Test
    fun onViewReady() {
        // Nothing to test
    }

    @Test
    fun `calculateEntropy on weak password`() {
        // Arrange

        // Act
        subject.calculateEntropy("password")
        // Assert
        verify(view).setEntropyStrength(8)
        verify(view).setEntropyLevel(0)
        verifyNoMoreInteractions(view)
    }

    @Test
    fun `calculateEntropy on regular password`() {
        // Arrange

        // Act
        subject.calculateEntropy("MyWallet")
        // Assert
        verify(view).setEntropyStrength(46)
        verify(view).setEntropyLevel(1)
        verifyNoMoreInteractions(view)
    }

    @Test
    fun `calculateEntropy on normal password`() {
        // Arrange

        // Act
        subject.calculateEntropy("MyTestWallet")
        // Assert
        verify(view).setEntropyStrength(69)
        verify(view).setEntropyLevel(2)
        verifyNoMoreInteractions(view)
    }

    @Test
    fun `calculateEntropy on strong password`() {
        // Arrange

        // Act
        subject.calculateEntropy("MyTestWallet!@!ASD@!")
        // Assert
        verify(view).setEntropyStrength(100)
        verify(view).setEntropyLevel(3)
        verifyNoMoreInteractions(view)
    }

    @Test
    fun `create wallet`() {
        // Arrange
        val email = "john@snow.com"
        val pw1 = "MyTestWallet"
        val accountName = "AccountName"
        val sharedKey = "SHARED_KEY"
        val guid = "GUID"
        val recoveryPhrase = ""

        whenever(view.getDefaultAccountName()).thenReturn(accountName)
        whenever(payloadDataManager.createHdWallet(any(), any(), any())).thenReturn(
            Observable.just(
                Wallet()
            )
        )
        whenever(payloadDataManager.wallet!!.guid).thenReturn(guid)
        whenever(payloadDataManager.wallet!!.sharedKey).thenReturn(sharedKey)
        // Act
        subject.passwordStrength = 80
        subject.createOrRestoreWallet(email, pw1, recoveryPhrase)
        // Assert
        verify(prngFixer).applyPRNGFixes()
        val observer = payloadDataManager.createHdWallet(pw1, accountName, email).test()
        observer.assertComplete()
        observer.assertNoErrors()

        verify(view).showProgressDialog(any())
        verify(prefsUtil).setValue(PersistentPrefs.KEY_EMAIL, email)
        verify(prefsUtil).setValue(PersistentPrefs.KEY_WALLET_GUID, guid)
        verify(accessState).isNewlyCreated = true
        verify(appUtil).sharedKey = sharedKey
        verify(view).startPinEntryActivity()
        verify(view).dismissProgressDialog()
        verify(analytics).logEvent(AnalyticsEvents.WalletCreation)
    }

    @Test
    fun `restore wallet`() {
        // Arrange
        val email = "john@snow.com"
        val pw1 = "MyTestWallet"
        val accountName = "AccountName"
        val sharedKey = "SHARED_KEY"
        val guid = "GUID"
        val recoveryPhrase = "all all all all all all all all all all all all"

        whenever(view.getDefaultAccountName()).thenReturn(accountName)
        whenever(payloadDataManager.restoreHdWallet(any(), any(), any(), any()))
            .thenReturn(Observable.just(Wallet()))
        whenever(payloadDataManager.wallet!!.guid).thenReturn(guid)
        whenever(payloadDataManager.wallet!!.sharedKey).thenReturn(sharedKey)

        // Act
        subject.createOrRestoreWallet(email, pw1, recoveryPhrase)

        // Assert
        val observer = payloadDataManager.restoreHdWallet(email, pw1, accountName, recoveryPhrase)
            .test()
        observer.assertComplete()
        observer.assertNoErrors()

        verify(view).showProgressDialog(any())
        verify(prefsUtil).setValue(PersistentPrefs.KEY_EMAIL, email)
        verify(prefsUtil).setValue(PersistentPrefs.KEY_ONBOARDING_COMPLETE, true)
        verify(prefsUtil).setValue(PersistentPrefs.KEY_WALLET_GUID, guid)
        verify(accessState).isNewlyCreated = true
        verify(appUtil).sharedKey = sharedKey
        verify(view).startPinEntryActivity()
        verify(view).dismissProgressDialog()
    }

    @Test
    fun `validateCredentials are valid`() {
        // Arrange
        val email = "john@snow.com"
        val pw1 = "MyTestWallet"
        val pw2 = "MyTestWallet"
        // Act
        subject.passwordStrength = 80
        val result = subject.validateCredentials(email, pw1, pw2)
        // Assert
        assert(result)
        verifyNoMoreInteractions(view)
    }

    @Test
    fun `validateCredentials invalid email`() {
        // Arrange

        // Act
        subject.passwordStrength = 80
        val result = subject.validateCredentials("john", "MyTestWallet", "MyTestWallet")
        // Assert
        assert(!result)
        verify(view).showError(R.string.invalid_email)
        verifyNoMoreInteractions(view)
    }

    @Test
    fun `validateCredentials short password`() {
        // Arrange

        // Act
        subject.passwordStrength = 80
        val result = subject.validateCredentials("john@snow.com", "aaa", "aaa")
        // Assert
        assert(!result)
        verify(view).showError(R.string.invalid_password_too_short)
        verifyNoMoreInteractions(view)
    }

    @Test
    fun `validateCredentials password missmatch`() {
        // Arrange

        // Act
        subject.passwordStrength = 80
        val result = subject.validateCredentials("john@snow.com", "MyTestWallet", "MyTestWallet2")
        // Assert
        assert(!result)
        verify(view).showError(R.string.password_mismatch_error)
        verifyNoMoreInteractions(view)
    }

    @Test
    fun `validateCredentials weak password running on non debug modde`() {
        // Arrange
        whenever(environmentConfig.isRunningInDebugMode()).thenReturn(false)
        // Act
        subject.passwordStrength = 20
        val result = subject.validateCredentials("john@snow.com", "aaaaaa", "aaaaaa")
        // Assert
        assert(!result)
        verify(view).warnWeakPassword(any(), any())
        verifyNoMoreInteractions(view)
    }

    @Test
    fun `validateCredentials weak password running on  debug modde`() {
        // Arrange
        whenever(environmentConfig.isRunningInDebugMode()).thenReturn(true)
        // Act
        subject.passwordStrength = 5
        val result = subject.validateCredentials("john@snow.com", "aaaaaa", "aaaaaa")
        // Assert
        assert(result)
        verifyZeroInteractions(view)
    }
}
