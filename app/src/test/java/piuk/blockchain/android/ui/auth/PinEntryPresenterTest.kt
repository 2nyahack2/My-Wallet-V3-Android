package piuk.blockchain.android.ui.auth

import android.annotation.SuppressLint
import android.content.Intent
import android.view.View
import android.widget.ImageView
import com.blockchain.logging.CrashLogger
import com.blockchain.notifications.analytics.Analytics
import com.blockchain.remoteconfig.RemoteConfig
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doAnswer
import com.nhaarman.mockito_kotlin.eq
import com.nhaarman.mockito_kotlin.isNull
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.verifyNoMoreInteractions
import com.nhaarman.mockito_kotlin.verifyZeroInteractions
import com.nhaarman.mockito_kotlin.whenever
import info.blockchain.wallet.api.Environment
import info.blockchain.wallet.api.data.UpdateType
import info.blockchain.wallet.exceptions.AccountLockedException
import info.blockchain.wallet.exceptions.DecryptionException
import info.blockchain.wallet.exceptions.HDWalletException
import info.blockchain.wallet.exceptions.InvalidCredentialsException
import info.blockchain.wallet.exceptions.ServerConnectionException
import info.blockchain.wallet.exceptions.UnsupportedVersionException
import info.blockchain.wallet.payload.data.Account
import info.blockchain.wallet.payload.data.Wallet
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Answers
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.invocation.InvocationOnMock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.spongycastle.crypto.InvalidCipherTextException
import piuk.blockchain.android.BlockchainTestApplication
import piuk.blockchain.android.data.biometrics.BiometricsController
import piuk.blockchain.android.ui.home.CredentialsWiper
import piuk.blockchain.android.ui.launcher.LauncherActivity
import piuk.blockchain.android.util.AppUtil
import piuk.blockchain.android.util.StringUtils
import piuk.blockchain.androidcore.data.access.AccessState
import piuk.blockchain.androidcore.data.api.EnvironmentConfig
import piuk.blockchain.androidcore.data.auth.AuthDataManager
import piuk.blockchain.androidcore.data.payload.PayloadDataManager
import piuk.blockchain.androidcore.data.walletoptions.WalletOptionsDataManager
import piuk.blockchain.androidcore.utils.PersistentPrefs
import piuk.blockchain.androidcore.utils.PrngFixer
import piuk.blockchain.androidcoreui.ui.customviews.ToastCustom
import java.net.SocketTimeoutException
import java.util.Arrays

@Config(sdk = [23], application = BlockchainTestApplication::class) @RunWith(
    RobolectricTestRunner::class
)
class PinEntryPresenterTest {
    private lateinit var subject: PinEntryPresenter

    private val activity: PinEntryView = mock()
    private val authDataManager: AuthDataManager = mock()
    private val appUtil: AppUtil = mock()
    private val prefsUtil: PersistentPrefs = mock()

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private val payloadManager: PayloadDataManager = mock()
    private val stringUtils: StringUtils = mock()
    private val biometricsController: BiometricsController = mock()
    private val accessState: AccessState = mock()
    private val walletOptionsDataManager: WalletOptionsDataManager = mock()
    private val environmentSettings: EnvironmentConfig = mock()
    private val prngFixer: PrngFixer = mock()
    private val mobileNoticeRemoteConfig: MobileNoticeRemoteConfig = mock()
    private val crashLogger: CrashLogger = mock()
    private val analytics: Analytics = mock()
    private val remoteConfig: RemoteConfig = mock()
    private val credentialsWiper: CredentialsWiper = mock()

    @Before
    fun setUp() {

        val mockImageView = Mockito.mock(ImageView::class.java)
        whenever(activity.pinBoxList)
            .thenReturn(Arrays.asList(mockImageView, mockImageView, mockImageView, mockImageView))
        whenever(stringUtils.getString(anyInt())).thenReturn("string resource")

        subject = PinEntryPresenter(
            analytics,
            authDataManager,
            appUtil,
            prefsUtil,
            payloadManager,
            stringUtils,
            accessState,
            walletOptionsDataManager,
            environmentSettings,
            prngFixer,
            mobileNoticeRemoteConfig,
            crashLogger,
            remoteConfig,
            credentialsWiper,
            biometricsController
        )
        subject.initView(activity)
    }

    @Test fun onViewReadyValidatingPinForResult() {
        // Arrange
        whenever(environmentSettings.environment).thenReturn(Environment.PRODUCTION)
        val intent = Intent()
        intent.putExtra(KEY_VALIDATING_PIN_FOR_RESULT, true)
        whenever(activity.pageIntent).thenReturn(intent)
        whenever(remoteConfig.getFeatureCount(any())).thenReturn(Single.just(4L))

        // Act
        subject.onViewReady()
        // Assert
        Assert.assertTrue(subject.isForValidatingPinForResult)
    }

    @Test fun onViewReadyMaxAttemptsExceeded() {
        // Arrange
        whenever(environmentSettings.environment).thenReturn(Environment.PRODUCTION)
        whenever(activity.pageIntent).thenReturn(null)
        whenever(prefsUtil.getValue(PersistentPrefs.KEY_PIN_FAILS, 0)).thenReturn(4)
        whenever(payloadManager.wallet).thenReturn(
            Mockito.mock(
                Wallet::class.java
            )
        )
        whenever(prefsUtil.pinId).thenReturn("")
        whenever(biometricsController.getDecodedData()).thenReturn("")
        whenever(remoteConfig.getFeatureCount(anyString())).thenReturn(Single.just(4L))

        // Act
        subject.onViewReady()
        Assert.assertTrue(subject.allowExit())
        verify(activity)
            .showParameteredToast(anyInt(), anyString(), anyInt())
        verify(activity).showMaxAttemptsDialog()
    }

    @Test fun checkFingerprintStatusShouldShowDialog() {
        // Arrange
        subject.isForValidatingPinForResult = false
        whenever(prefsUtil.pinId).thenReturn("1234")
        whenever(biometricsController.isFingerprintUnlockEnabled).thenReturn(true)
        // Act
        subject.checkFingerprintStatus()
        // Assert
        verify(activity).showFingerprintDialog()
    }

    @Test fun checkFingerprintStatusDontShow() {
        // Arrange
        subject.isForValidatingPinForResult = true
        // Act
        subject.checkFingerprintStatus()
        // Assert
        verify(activity).showKeyboard()
    }

    @Test fun canShowFingerprintDialog() {
        // Arrange
        subject.canShowFingerprintDialog = true
        // Act
        val value = subject.canShowFingerprintDialog()
        // Assert
        Assert.assertTrue(value)
    }

    @Test fun loginWithDecryptedPin() {
        // Arrange
        val pincode = "1234"
        whenever(authDataManager.validatePin(pincode)).thenReturn(Observable.just("password"))
        // Act
        subject.loginWithDecryptedPin(pincode)
        // Assert
        verify(authDataManager).validatePin(pincode)
        verify(activity).pinBoxList
        Assert.assertFalse(subject.canShowFingerprintDialog())
    }

    @Test fun onDeleteClicked() {
        // Arrange
        subject.userEnteredPin = "1234"
        // Act
        subject.onDeleteClicked()
        // Assert
        Assert.assertEquals("123", subject.userEnteredPin)
        verify(activity).pinBoxList
    }

    @Test fun padClickedPinAlreadyFourDigits() {
        // Arrange
        subject.userEnteredPin = "0000"
        // Act
        subject.onPadClicked("0")
        // Assert
        verifyZeroInteractions(activity)
    }

    @Test fun padClickedAllZeros() {
        // Arrange
        subject.userEnteredPin = "000"
        // Act
        whenever(prefsUtil.pinId).thenReturn("")
        whenever(biometricsController.getDecodedData()).thenReturn("")
        subject.onPadClicked("0")
        // Assert
        verify(activity).clearPinBoxes()
        verify(activity).showToast(anyInt(), anyString())
        Assert.assertEquals("", subject.userEnteredPin)
        Assert.assertNull(subject.userEnteredConfirmationPin)
    }

    @Test fun padClickedShowCommonPinWarning() {
        // Arrange
        subject.userEnteredPin = "123"
        whenever(prefsUtil.pinId).thenReturn("")
        // Act
        subject.onPadClicked("4")
        // Assert
        verify(activity).showCommonPinWarning(any())
    }

    @Test fun padClickedShowCommonPinWarningAndClickRetry() {
        // Arrange
        subject.userEnteredPin = "123"
        whenever(prefsUtil.pinId).thenReturn("")
        doAnswer { invocation: InvocationOnMock ->
            (invocation.arguments[0] as DialogButtonCallback).onPositiveClicked()
            null
        }.whenever(activity).showCommonPinWarning(any())
        // Act
        subject.onPadClicked("4")
        // Assert
        verify(activity).showCommonPinWarning(any())
        verify(activity).clearPinBoxes()
        Assert.assertEquals("", subject.userEnteredPin)
        Assert.assertNull(subject.userEnteredConfirmationPin)
    }

    @Test fun padClickedShowCommonPinWarningAndClickContinue() {
        // Arrange
        subject.userEnteredPin = "123"
        whenever(prefsUtil.pinId).thenReturn("")
        doAnswer { invocation: InvocationOnMock ->
            (invocation.arguments[0] as DialogButtonCallback).onNegativeClicked()
            null
        }.whenever(activity).showCommonPinWarning(any())
        // Act
        subject.onPadClicked("4")
        // Assert
        verify(activity).showCommonPinWarning(any())
        Assert.assertEquals("", subject.userEnteredPin)
        Assert.assertEquals("1234", subject.userEnteredConfirmationPin)
    }

    @Test fun padClickedShowPinReuseWarning() {
        // Arrange
        subject.userEnteredPin = "258"
        whenever(prefsUtil.pinId).thenReturn("")
        whenever(accessState.pin).thenReturn("2580")
        // Act
        subject.onPadClicked("0")
        // Assert
        verify(activity).dismissProgressDialog()
        verify(activity).showToast(anyInt(), eq(ToastCustom.TYPE_ERROR))
        verify(activity).clearPinBoxes()
    }

    @Test fun padClickedVerifyPinValidateCalled() {
        // Arrange
        subject.userEnteredPin = "133"
        whenever(prefsUtil.pinId).thenReturn("1234567890")
        whenever(authDataManager.validatePin(anyString())).thenReturn(Observable.just(""))
        // Act
        subject.onPadClicked("7")
        // Assert
        verify(activity).setTitleVisibility(View.INVISIBLE)
        verify(activity, Mockito.times(2))
            .showProgressDialog(anyInt(), isNull())
        verify(authDataManager).validatePin(anyString())
    }

    @Test fun padClickedVerifyPinForResultReturnsValidPassword() {
        // Arrange
        subject.userEnteredPin = "133"
        subject.isForValidatingPinForResult = true
        whenever(prefsUtil.pinId).thenReturn("1234567890")
        whenever(authDataManager.validatePin(anyString())).thenReturn(Observable.just(""))
        // Act
        subject.onPadClicked("7")
        // Assert
        verify(activity).setTitleVisibility(View.INVISIBLE)
        verify(activity).showProgressDialog(anyInt(), isNull())
        verify(activity).dismissProgressDialog()
        verify(authDataManager).validatePin(anyString())
        verify(activity).finishWithResultOk("1337")
    }

    @Test fun padClickedVerifyPinValidateCalledReturnsErrorIncrementsFailureCount() {
        // Arrange
        subject.userEnteredPin = "133"
        whenever(prefsUtil.pinId).thenReturn("1234567890")
        whenever(authDataManager.validatePin(anyString()))
            .thenReturn(Observable.error(InvalidCredentialsException()))
        // Act
        subject.onPadClicked("7")
        // Assert
        verify(activity).setTitleVisibility(View.INVISIBLE)
        verify(activity).showProgressDialog(anyInt(), isNull())
        verify(authDataManager).validatePin(anyString())
        verify(prefsUtil).setValue(anyString(), anyInt())
        verify(prefsUtil).getValue(anyString(), anyInt())
        verify(activity).showToast(anyInt(), anyString())
        verify(activity).restartPageAndClearTop()
    }

    @Test fun padClickedVerifyPinValidateCalledReturnsServerError() {
        // Arrange
        subject.userEnteredPin = "133"
        whenever(prefsUtil.pinId).thenReturn("1234567890")
        whenever(authDataManager.validatePin(anyString()))
            .thenReturn(Observable.error(ServerConnectionException()))
        // Act
        subject.onPadClicked("7")
        // Assert
        verify(activity).setTitleVisibility(View.INVISIBLE)
        verify(activity).showProgressDialog(anyInt(), isNull())
        verify(authDataManager).validatePin(anyString())
        verify(activity).showToast(anyInt(), anyString())
        verify(activity).restartPageAndClearTop()
    }

    @Test fun padClickedVerifyPinValidateCalledReturnsTimeout() {
        // Arrange
        subject.userEnteredPin = "133"
        whenever(prefsUtil.pinId).thenReturn("1234567890")
        whenever(authDataManager.validatePin(anyString()))
            .thenReturn(Observable.error(SocketTimeoutException()))
        // Act
        subject.onPadClicked("7")
        // Assert
        verify(activity).setTitleVisibility(View.INVISIBLE)
        verify(activity).showProgressDialog(anyInt(), isNull())
        verify(authDataManager).validatePin(anyString())
        verify(activity).showToast(anyInt(), anyString())
        verify(activity).restartPageAndClearTop()
    }

    @Test fun padClickedVerifyPinValidateCalledReturnsInvalidCipherText() {
        // Arrange
        subject.userEnteredPin = "133"
        whenever(prefsUtil.pinId).thenReturn("1234567890")
        whenever(authDataManager.validatePin(anyString())).thenReturn(Observable.just(""))
        whenever(
            payloadManager.initializeAndDecrypt(
                anyString(), anyString(), anyString()
            )
        )
            .thenReturn(Completable.error(InvalidCipherTextException()))
        whenever(prefsUtil.getValue(anyString(), anyString()))
            .thenReturn("prefs string")
        // Act
        subject.onPadClicked("7")
        // Assert
        verify(activity).setTitleVisibility(View.INVISIBLE)
        verify(activity, Mockito.times(2))
            .showProgressDialog(anyInt(), isNull())
        verify(authDataManager).validatePin(anyString())
        verify(payloadManager).initializeAndDecrypt(
            anyString(), anyString(), anyString()
        )
        verify(prefsUtil).setValue(anyString(), anyInt())
        verify(activity).showToast(anyInt(), anyString())
        verify(accessState).clearPin()
        verify(appUtil).clearCredentials()
        verify(appUtil).restartApp(LauncherActivity::class.java)
    }

    @Test fun padClickedVerifyPinValidateCalledReturnsGenericException() {
        // Arrange
        subject.userEnteredPin = "133"
        whenever(prefsUtil.pinId).thenReturn("1234567890")
        whenever(authDataManager.validatePin(anyString())).thenReturn(Observable.just(""))
        whenever(
            payloadManager.initializeAndDecrypt(
                anyString(), anyString(), anyString()
            )
        )
            .thenReturn(Completable.error(Exception()))
        whenever(prefsUtil.getValue(anyString(), anyString()))
            .thenReturn("prefs string")
        // Act
        subject.onPadClicked("7")
        // Assert
        verify(activity).setTitleVisibility(View.INVISIBLE)
        verify(activity, Mockito.times(2))
            .showProgressDialog(anyInt(), isNull())
        verify(authDataManager).validatePin(anyString())
        verify(payloadManager).initializeAndDecrypt(
            anyString(), anyString(), anyString()
        )
        verify(prefsUtil).setValue(anyString(), anyInt())
        verify(activity).showToast(anyInt(), anyString())
        verify(appUtil).restartApp(LauncherActivity::class.java)
    }

    @Test fun padClickedCreatePinCreateSuccessful() {
        // Arrange
        subject.userEnteredPin = "133"
        subject.userEnteredConfirmationPin = "1337"
        whenever(payloadManager.tempPassword).thenReturn("temp password")
        whenever(prefsUtil.pinId).thenReturn("")
        whenever(
            authDataManager.createPin(anyString(), anyString())
        ).thenReturn(
            Completable.complete()
        )
        whenever(authDataManager.validatePin(anyString()))
            .thenReturn(Observable.just("password"))
        whenever(accessState.pin).thenReturn("1337")
        // Act
        subject.onPadClicked("7")
        // Assert
        verify(activity, Mockito.times(2))
            .showProgressDialog(anyInt(), isNull())
        verify(authDataManager).createPin(anyString(), anyString())
        verify(biometricsController).setFingerprintUnlockEnabled(false)
    }

    @Test fun padClickedCreatePinCreateFailed() {
        // Arrange
        subject.userEnteredPin = "133"
        subject.userEnteredConfirmationPin = "1337"
        whenever(payloadManager.tempPassword).thenReturn("temp password")
        whenever(prefsUtil.pinId).thenReturn("")
        whenever(
            authDataManager.createPin(anyString(), anyString())
        )
            .thenReturn(Completable.error(Throwable()))
        whenever(accessState.pin).thenReturn("")
        // Act
        subject.onPadClicked("7")
        // Assert
        verify(activity).showProgressDialog(anyInt(), isNull())
        verify(authDataManager).createPin(anyString(), anyString())
        verify(activity).showToast(anyInt(), anyString())
        verify(prefsUtil).clear()
        verify(appUtil).restartApp(LauncherActivity::class.java)
    }

    @Test fun padClickedCreatePinWritesNewConfirmationValue() {
        // Arrange
        subject.userEnteredPin = "133"
        whenever(prefsUtil.pinId).thenReturn("")
        whenever(
            authDataManager.createPin(anyString(), anyString())
        ).thenReturn(
            Completable.complete()
        )
        whenever(accessState.pin).thenReturn("")
        // Act
        subject.onPadClicked("7")
        // Assert
        Assert.assertEquals("1337", subject.userEnteredConfirmationPin)
        Assert.assertEquals("", subject.userEnteredPin)
    }

    @Test fun padClickedCreatePinMismatched() {
        // Arrange
        subject.userEnteredPin = "133"
        subject.userEnteredConfirmationPin = "1234"
        whenever(prefsUtil.pinId).thenReturn("")
        whenever(
            authDataManager.createPin(anyString(), anyString())
        ).thenReturn(
            Completable.complete()
        )
        whenever(accessState.pin).thenReturn("")
        // Act
        subject.onPadClicked("7")
        // Assert
        verify(activity).showToast(anyInt(), anyString())
        verify(activity).dismissProgressDialog()
    }

    @Test
    fun clearPinBoxes() {
        subject.clearPinBoxes()

        verify(activity).clearPinBoxes()
        Assert.assertEquals("", subject.userEnteredPin)
    }

    @Test fun validatePasswordSuccessful() {
        // Arrange
        val password = "1234567890"
        whenever(
            payloadManager.initializeAndDecrypt(
                anyString(), anyString(), eq(password)
            )
        )
            .thenReturn(Completable.complete())
        whenever(prefsUtil.getValue(anyString(), anyString()))
            .thenReturn("prefs string")
        // Act
        subject.validatePassword(password)
        // Assert
        verify(activity).showProgressDialog(anyInt(), isNull())
        verify(payloadManager).initializeAndDecrypt(
            anyString(), anyString(), eq(password)
        )
        verify(activity).dismissProgressDialog()
        verify(activity).showToast(anyInt(), anyString())
        verify(prefsUtil).removeValue(anyString())
        verify(prefsUtil).pinId = anyString()
        verify(accessState).clearPin()
        verify(activity).restartPageAndClearTop()
    }

    @Test fun validatePasswordThrowsGenericException() {
        // Arrange
        val password = "1234567890"
        whenever(
            payloadManager.initializeAndDecrypt(
                anyString(), anyString(), eq(password)
            )
        )
            .thenReturn(Completable.error(Throwable()))
        whenever(prefsUtil.getValue(anyString(), anyString()))
            .thenReturn("prefs string")
        // Act
        subject.validatePassword(password)
        // Assert
        verify(activity).showProgressDialog(anyInt(), isNull())
        verify(payloadManager).initializeAndDecrypt(
            anyString(), anyString(), eq(password)
        )
        verify(activity, Mockito.times(2)).dismissProgressDialog()
        verify(activity).showToast(anyInt(), anyString())
        verify(activity).showValidationDialog()
    }

    @Test fun validatePasswordThrowsServerConnectionException() {
        // Arrange
        val password = "1234567890"
        whenever(
            payloadManager.initializeAndDecrypt(
                anyString(), anyString(), eq(password)
            )
        )
            .thenReturn(Completable.error(ServerConnectionException()))
        whenever(prefsUtil.getValue(anyString(), anyString()))
            .thenReturn("prefs string")
        // Act
        subject.validatePassword(password)
        // Assert
        verify(activity).showProgressDialog(anyInt(), isNull())
        verify(payloadManager).initializeAndDecrypt(
            anyString(), anyString(), eq(password)
        )
        verify(activity).dismissProgressDialog()
        verify(activity).showToast(anyInt(), anyString())
    }

    @Test fun validatePasswordThrowsSocketTimeoutException() {
        // Arrange
        val password = "1234567890"
        whenever(
            payloadManager.initializeAndDecrypt(
                anyString(), anyString(), eq(password)
            )
        )
            .thenReturn(Completable.error(SocketTimeoutException()))
        whenever(prefsUtil.getValue(anyString(), anyString()))
            .thenReturn("prefs string")
        // Act
        subject.validatePassword(password)
        // Assert
        verify(activity).showProgressDialog(anyInt(), isNull())
        verify(payloadManager).initializeAndDecrypt(
            anyString(), anyString(), eq(password)
        )
        verify(activity).dismissProgressDialog()
        verify(activity).showToast(anyInt(), anyString())
    }

    @Test fun validatePasswordThrowsHDWalletExceptionException() {
        // Arrange
        val password = "1234567890"
        whenever(
            payloadManager.initializeAndDecrypt(
                anyString(), anyString(), eq(password)
            )
        )
            .thenReturn(Completable.error(HDWalletException()))
        whenever(prefsUtil.getValue(anyString(), anyString()))
            .thenReturn("prefs string")
        // Act
        subject.validatePassword(password)
        // Assert
        verify(activity).showProgressDialog(anyInt(), isNull())
        verify(payloadManager).initializeAndDecrypt(anyString(), anyString(), eq(password))
        verify(activity).dismissProgressDialog()
        verify(activity).showToast(anyInt(), anyString())
        verify(appUtil).restartApp(LauncherActivity::class.java)
    }

    @Test fun validatePasswordThrowsAccountLockedException() {
        // Arrange
        val password = "1234567890"
        whenever(
            payloadManager.initializeAndDecrypt(
                anyString(), anyString(), eq(password)
            )
        )
            .thenReturn(Completable.error(AccountLockedException()))
        whenever(prefsUtil.getValue(anyString(), anyString()))
            .thenReturn("prefs string")
        // Act
        subject.validatePassword(password)
        // Assert
        verify(activity).showProgressDialog(anyInt(), isNull())
        verify(payloadManager).initializeAndDecrypt(
            anyString(), anyString(), eq(password)
        )
        verify(activity).dismissProgressDialog()
        verify(activity).showAccountLockedDialog()
    }

    @SuppressLint("VisibleForTests") @Test fun updatePayloadInvalidCredentialsException() {
        // Arrange
        whenever(
            payloadManager.initializeAndDecrypt(
                anyString(), anyString(), anyString()
            )
        )
            .thenReturn(Completable.error(InvalidCredentialsException()))
        whenever(prefsUtil.getValue(anyString(), anyString()))
            .thenReturn("prefs string")
        val mockPayload = Mockito.mock(
            Wallet::class.java
        )
        whenever(mockPayload.sharedKey).thenReturn("1234567890")
        whenever(payloadManager.wallet).thenReturn(mockPayload)
        // Act
        subject.updatePayload("", false)
        // Assert
        verify(activity).showProgressDialog(anyInt(), isNull())
        verify(payloadManager).initializeAndDecrypt(
            anyString(), anyString(), anyString()
        )
        verify(activity).goToPasswordRequiredActivity()
    }

    @SuppressLint("VisibleForTests") @Test fun updatePayloadServerConnectionException() {
        // Arrange
        whenever(
            payloadManager.initializeAndDecrypt(
                anyString(), anyString(), anyString()
            )
        )
            .thenReturn(Completable.error(ServerConnectionException()))
        whenever(prefsUtil.getValue(anyString(), anyString()))
            .thenReturn("prefs string")
        val mockPayload = Mockito.mock(
            Wallet::class.java
        )
        whenever(mockPayload.sharedKey).thenReturn("1234567890")
        whenever(payloadManager.wallet).thenReturn(mockPayload)
        // Act
        subject.updatePayload("", false)
        // Assert
        verify(activity).showProgressDialog(anyInt(), isNull())
        verify(payloadManager).initializeAndDecrypt(
            anyString(), anyString(), anyString()
        )
        verify(activity).showToast(anyInt(), anyString())
    }

    @SuppressLint("VisibleForTests") @Test fun updatePayloadDecryptionException() {
        // Arrange
        whenever(
            payloadManager.initializeAndDecrypt(
                anyString(), anyString(), anyString()
            )
        )
            .thenReturn(Completable.error(DecryptionException()))
        whenever(prefsUtil.getValue(anyString(), anyString()))
            .thenReturn("prefs string")
        val mockPayload = Mockito.mock(
            Wallet::class.java
        )
        whenever(mockPayload.sharedKey).thenReturn("1234567890")
        whenever(payloadManager.wallet).thenReturn(mockPayload)
        // Act
        subject.updatePayload("", false)
        // Assert
        verify(activity).showProgressDialog(anyInt(), isNull())
        verify(payloadManager).initializeAndDecrypt(
            anyString(), anyString(), anyString()
        )
        verify(activity).goToPasswordRequiredActivity()
    }

    @SuppressLint("VisibleForTests") @Test fun updatePayloadHDWalletException() {
        // Arrange
        whenever(
            payloadManager.initializeAndDecrypt(
                anyString(), anyString(), anyString()
            )
        )
            .thenReturn(Completable.error(HDWalletException()))
        whenever(prefsUtil.getValue(anyString(), anyString()))
            .thenReturn("prefs string")
        val mockPayload = Mockito.mock(
            Wallet::class.java
        )
        whenever(mockPayload.sharedKey).thenReturn("1234567890")
        whenever(payloadManager.wallet).thenReturn(mockPayload)
        // Act
        subject.updatePayload("", false)
        // Assert
        verify(activity).showProgressDialog(anyInt(), isNull())
        verify(payloadManager).initializeAndDecrypt(
            anyString(), anyString(), anyString()
        )
        verify(activity).showToast(anyInt(), anyString())
        verify(appUtil).restartApp(LauncherActivity::class.java)
    }

    @SuppressLint("VisibleForTests") @Test fun updatePayloadVersionNotSupported() {
        // Arrange
        whenever(
            payloadManager.initializeAndDecrypt(
                anyString(), anyString(), anyString()
            )
        )
            .thenReturn(Completable.error(UnsupportedVersionException()))
        whenever(prefsUtil.getValue(anyString(), anyString()))
            .thenReturn("prefs string")
        val mockPayload = Mockito.mock(
            Wallet::class.java
        )
        whenever(mockPayload.sharedKey).thenReturn("1234567890")
        whenever(payloadManager.wallet).thenReturn(mockPayload)
        // Act
        subject.updatePayload("", false)
        // Assert
        verify(activity).showProgressDialog(anyInt(), isNull())
        verify(payloadManager).initializeAndDecrypt(
            anyString(), anyString(), anyString()
        )
        verify(activity).showWalletVersionNotSupportedDialog(isNull())
    }

    @SuppressLint("VisibleForTests")
    @Test
    fun updatePayloadAccountLocked() {
        // Arrange
        whenever(
            payloadManager.initializeAndDecrypt(
                anyString(), anyString(), anyString()
            )
        )
            .thenReturn(Completable.error(AccountLockedException()))
        whenever(prefsUtil.getValue(anyString(), anyString()))
            .thenReturn("prefs string")
        val mockPayload = Mockito.mock(
            Wallet::class.java
        )
        whenever(mockPayload.sharedKey).thenReturn("1234567890")
        whenever(payloadManager.wallet).thenReturn(mockPayload)
        // Act
        subject.updatePayload("", false)
        // Assert
        verify(activity).showProgressDialog(anyInt(), isNull())
        verify(payloadManager).initializeAndDecrypt(
            anyString(), anyString(), anyString()
        )
        verify(activity).showAccountLockedDialog()
    }

    @SuppressLint("VisibleForTests")
    @Test
    fun updatePayloadSuccessfulSetLabels() {
        // Arrange
        whenever(
            payloadManager.initializeAndDecrypt(
                anyString(), anyString(), anyString()
            )
        )
            .thenReturn(Completable.complete())
        whenever(prefsUtil.getValue(anyString(), anyString()))
            .thenReturn("prefs string")
        val mockAccount = mock<Account>()
        whenever(mockAccount.label).thenReturn("")
        whenever(payloadManager.accounts).thenReturn(listOf(mockAccount))
        whenever(payloadManager.getAccount(0)).thenReturn(mockAccount)

        val mockWallet = mock<Wallet>()
        whenever(payloadManager.wallet).thenReturn(mockWallet)
        whenever(mockWallet.sharedKey).thenReturn("shared_key")
        whenever(mockWallet.isUpgraded).thenReturn(true)
        whenever(accessState.isNewlyCreated).thenReturn(true)
        // Act
        subject.updatePayload("", false)
        // Assert
        verify(activity).showProgressDialog(anyInt(), isNull())
        verify(payloadManager).initializeAndDecrypt(
            anyString(), anyString(), anyString()
        )
        verify(appUtil).sharedKey = anyString()
        verify(payloadManager, Mockito.atLeastOnce()).wallet
        verify(stringUtils).getString(anyInt())
        verify(activity).dismissProgressDialog()
        Assert.assertTrue(subject.canShowFingerprintDialog)
    }

    @SuppressLint("VisibleForTests")
    @Test
    fun updatePayloadSuccessfulUpgradeWallet() {
        // Arrange
        whenever(
            payloadManager.initializeAndDecrypt(
                anyString(), anyString(), anyString()
            )
        )
            .thenReturn(Completable.complete())
        whenever(prefsUtil.getValue(anyString(), anyString()))
            .thenReturn("prefs string")
        val mockAccount = mock<Account>()
        whenever(mockAccount.label).thenReturn("label")
        whenever(payloadManager.getAccount(0)).thenReturn(mockAccount)
        val mockWallet = mock<Wallet>()
        whenever(payloadManager.wallet).thenReturn(mockWallet)
        whenever(mockWallet.sharedKey).thenReturn("shared_key")
        whenever(mockWallet.isUpgraded).thenReturn(false)
        whenever(accessState.isNewlyCreated).thenReturn(false)
        // Act
        subject.updatePayload("", false)
        // Assert
        verify(activity).showProgressDialog(anyInt(), isNull())
        verify(payloadManager).initializeAndDecrypt(
            anyString(), anyString(), anyString()
        )
        verify(appUtil).sharedKey = anyString()
        verify(activity).goToUpgradeWalletActivity()
        verify(activity).dismissProgressDialog()
        Assert.assertTrue(subject.canShowFingerprintDialog)
    }

    @SuppressLint("VisibleForTests")
    @Test
    fun updatePayloadSuccessfulVerifyPin() {
        // Arrange
        whenever(payloadManager.initializeAndDecrypt(anyString(), anyString(), anyString())).thenReturn(
            Completable.complete()
        )
        whenever(prefsUtil.getValue(anyString(), anyString()))
            .thenReturn("prefs string")
        val mockAccount = mock<Account>()
        whenever(mockAccount.label).thenReturn("label")
        whenever(payloadManager.getAccount(0)).thenReturn(mockAccount)
        val mockWallet = mock<Wallet>()
        whenever(payloadManager.wallet).thenReturn(mockWallet)
        whenever(mockWallet.sharedKey).thenReturn("shared_key")
        whenever(mockWallet.isUpgraded).thenReturn(true)
        whenever(accessState.isNewlyCreated).thenReturn(false)
        // Act
        subject.updatePayload("", false)
        // Assert
        verify(activity).showProgressDialog(anyInt(), isNull())
        verify(payloadManager).initializeAndDecrypt(
            anyString(), anyString(), anyString()
        )
        verify(appUtil).sharedKey = anyString()
        verify(activity).restartAppWithVerifiedPin()
        verify(activity).dismissProgressDialog()
        Assert.assertTrue(subject.canShowFingerprintDialog)
    }

    @Test
    fun incrementFailureCount() {
        // Act
        subject.incrementFailureCountAndRestart()
        // Assert
        verify(prefsUtil).getValue(anyString(), anyInt())
        verify(prefsUtil).setValue(anyString(), anyInt())
        verify(activity).showToast(anyInt(), anyString())
        verify(activity).restartPageAndClearTop()
    }

    @Test
    fun resetApp() {
        // Act
        subject.resetApp()
        // Assert
        verify(credentialsWiper).wipe()
    }

    @Test fun allowExit() {
        // Act
        val allowExit = subject.allowExit()
        // Assert
        Assert.assertEquals(subject.allowExit(), allowExit)
    }

    @Test
    fun isCreatingNewPin() {
        whenever(prefsUtil.pinId).thenReturn("")
        // Act
        val creatingNewPin = subject.isCreatingNewPin
        // Assert
        Assert.assertTrue(creatingNewPin)
    }

    @Test
    fun isNotCreatingNewPin() {
        // Arrange
        whenever(prefsUtil.pinId).thenReturn("1234567890")
        // Act
        val creatingNewPin = subject.isCreatingNewPin
        // Assert
        Assert.assertFalse(creatingNewPin)
    }

    @Test fun fetchInfoMessage() {
        // Arrange
        val mobileNoticeDialog = MobileNoticeDialog(
            "title",
            "body",
            "primarybutton",
            "link"
        )
        whenever(mobileNoticeRemoteConfig.mobileNoticeDialog()).thenReturn(Single.just(mobileNoticeDialog))

        // Act
        subject.fetchInfoMessage()
        // Assert
        verify(activity).showMobileNotice(mobileNoticeDialog)
    }

    @Test fun checkForceUpgradeStatus_false() {
        // Arrange
        val versionName = "281"
        whenever(
            walletOptionsDataManager.checkForceUpgrade(versionName)
        )
            .thenReturn(Observable.just(UpdateType.NONE))
        // Act
        subject.checkForceUpgradeStatus(versionName)
        // Assert
        verify(walletOptionsDataManager).checkForceUpgrade(versionName)
        verifyZeroInteractions(activity)
    }

    @Test fun checkForceUpgradeStatus_true() {
        // Arrange
        val versionName = "281"
        whenever(
            walletOptionsDataManager.checkForceUpgrade(versionName)
        )
            .thenReturn(Observable.just(UpdateType.FORCE))
        // Act
        subject.checkForceUpgradeStatus(versionName)
        // Assert
        verify(walletOptionsDataManager).checkForceUpgrade(versionName)
        verify(activity).appNeedsUpgrade(true)
        verifyNoMoreInteractions(activity)
    }

    @Test
    fun finishSignup_success() {
        subject.finishSignupProcess()

        verify(activity).restartAppWithVerifiedPin()
    }

    @Test
    fun handlePayloadUpdateComplete_needsUpgrade() {
        val mockWallet = mock<Wallet>()
        whenever(payloadManager.wallet).thenReturn(mockWallet)
        whenever(mockWallet.sharedKey).thenReturn("")
        whenever(mockWallet.isUpgraded).thenReturn(false)

        subject.handlePayloadUpdateComplete(false)

        verify(activity).goToUpgradeWalletActivity()
    }

    @Test
    fun handlePayloadUpdateComplete_fromPinCreationAndBiometricsEnabled() {
        val mockWallet = mock<Wallet>()
        whenever(payloadManager.wallet).thenReturn(mockWallet)
        whenever(mockWallet.sharedKey).thenReturn("")
        whenever(mockWallet.isUpgraded).thenReturn(true)
        whenever(biometricsController.isFingerprintAvailable).thenReturn(true)

        subject.handlePayloadUpdateComplete(true)

        verify(activity).askToUseBiometrics()
    }

    @Test
    fun handlePayloadUpdateComplete_fromPinCreationAndBiometricsNotEnabled() {
        val mockWallet = mock<Wallet>()
        whenever(payloadManager.wallet).thenReturn(mockWallet)
        whenever(mockWallet.sharedKey).thenReturn("")
        whenever(mockWallet.isUpgraded).thenReturn(true)
        whenever(biometricsController.isFingerprintAvailable).thenReturn(false)

        subject.handlePayloadUpdateComplete(true)

        verify(activity).restartAppWithVerifiedPin()
    }

    @Test
    fun handlePayloadUpdateComplete_notFromPinCreation() {
        val mockWallet = mock<Wallet>()
        whenever(payloadManager.wallet).thenReturn(mockWallet)
        whenever(mockWallet.sharedKey).thenReturn("")
        whenever(mockWallet.isUpgraded).thenReturn(true)
        whenever(biometricsController.isFingerprintAvailable).thenReturn(true)

        subject.handlePayloadUpdateComplete(false)

        verify(activity).restartAppWithVerifiedPin()
    }
}