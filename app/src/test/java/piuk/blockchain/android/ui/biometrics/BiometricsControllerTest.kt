package piuk.blockchain.android.ui.biometrics

import android.content.Context
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricPrompt
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doNothing
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.spy
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.verifyNoMoreInteractions
import com.nhaarman.mockito_kotlin.verifyZeroInteractions
import com.nhaarman.mockito_kotlin.whenever
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyString
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import piuk.blockchain.android.BlockchainTestApplication
import piuk.blockchain.android.data.biometrics.BiometricAuthFailed
import piuk.blockchain.android.data.biometrics.BiometricAuthLockout
import piuk.blockchain.android.data.biometrics.BiometricAuthLockoutPermanent
import piuk.blockchain.android.data.biometrics.BiometricKeysInvalidated
import piuk.blockchain.android.data.biometrics.BiometricsCallback
import piuk.blockchain.android.data.biometrics.BiometricsController
import piuk.blockchain.android.data.biometrics.CipherInvalidatedError
import piuk.blockchain.android.data.biometrics.CipherOtherError
import piuk.blockchain.android.data.biometrics.CipherSuccess
import piuk.blockchain.android.data.biometrics.CryptographyManager
import piuk.blockchain.android.data.biometrics.EncryptedData
import piuk.blockchain.androidcore.data.access.AccessState
import piuk.blockchain.androidcore.utils.PrefsUtil
import java.security.Signature
import javax.crypto.Cipher

@Config(sdk = [23], application = BlockchainTestApplication::class) @RunWith(
    RobolectricTestRunner::class
)
class BiometricsControllerTest {
    private lateinit var subject: BiometricsController

    private val applicationContext: Context = mock()
    private val prefsUtil: PrefsUtil = mock()
    private val accessState: AccessState = mock()
    private val cryptographyManager: CryptographyManager = mock()
    private val ENCRYPTION_BLOCK_MODE = KeyProperties.BLOCK_MODE_CBC
    private val ENCRYPTION_PADDING = KeyProperties.ENCRYPTION_PADDING_PKCS7
    private val ENCRYPTION_ALGORITHM = KeyProperties.KEY_ALGORITHM_AES
    private val transformation = "$ENCRYPTION_ALGORITHM/$ENCRYPTION_BLOCK_MODE/$ENCRYPTION_PADDING"
    private val cipher = Cipher.getInstance(transformation)
    private lateinit var cryptoObject: BiometricPrompt.CryptoObject

    @Before @Throws(java.lang.Exception::class)
    fun setup() {
        cryptoObject = BiometricPrompt.CryptoObject(cipher)
        subject = spy(BiometricsController(applicationContext, prefsUtil, accessState, cryptographyManager))
    }

    @Test
    fun ifFingerprintUnlockEnabledTrue() {
        doReturn(true).whenever(subject).isFingerprintAvailable
        doReturn("1234").whenever(subject).getDecodedData()
        whenever(prefsUtil.biometricsEnabled).thenReturn(true)

        val value = subject.isFingerprintUnlockEnabled
        Assert.assertEquals(true, value)
    }

    @Test
    fun ifFingerprintUnlockEnabledFalse() {
        doReturn(true).`when`(subject).isFingerprintAvailable
        whenever(prefsUtil.biometricsEnabled).thenReturn(false)

        val value = subject.isFingerprintUnlockEnabled
        Assert.assertEquals(false, value)
    }

    @Test
    fun setFingerprintUnlockEnabled() {
        subject.setFingerprintUnlockEnabled(true)
        verify(prefsUtil).biometricsEnabled = true
        verifyNoMoreInteractions(prefsUtil)
        verifyZeroInteractions(cryptographyManager)
    }

    @Test
    fun setFingerprintUnlockDisabled() {
        whenever(prefsUtil.encodedKeyName).thenReturn("a string")
        subject.setFingerprintUnlockEnabled(false)
        verify(prefsUtil).biometricsEnabled = false
        verify(prefsUtil).clearEncodedPin()
        verify(cryptographyManager).clearData(anyString())
    }

    @Test
    fun storeEncodedData() {
        subject.storeEncodedData("data")
        verify(prefsUtil).encodedPin = "data"
    }

    @Test
    fun getEncodedData() {
        whenever(prefsUtil.encodedPin).thenReturn("data")

        val result = subject.getDecodedData()
        verify(prefsUtil).encodedPin
        Assert.assertEquals("data", result)
    }

    @Test
    fun getDataAndIV_success() {
        val encodedValue = "ZGF0YQ==\n"
        val data = "$encodedValue-_-$encodedValue"
        val result = subject.getDataAndIV(data)
        Assert.assertEquals(result.first, encodedValue)
        Assert.assertEquals(result.second, encodedValue)
    }

    @Test(expected = IllegalStateException::class)
    fun getDataAndIV_noSeparator() {
        val encodedValue = "ZGF0YQ==\n"
        val data = "$encodedValue$encodedValue"
        subject.getDataAndIV(data)
    }

    @Test(expected = IllegalArgumentException::class)
    fun getDataAndIV_noIV() {
        val encodedValue = "ZGF0YQ==\n"
        val data = "$encodedValue-_-"
        subject.getDataAndIV(data)
    }

    @Test
    fun authCallbackSuccess_register() {
        val encryptedData = EncryptedData(byteArrayOf(), byteArrayOf())
        val result = mock<BiometricPrompt.AuthenticationResult>()

        whenever(accessState.pin).thenReturn("")
        whenever(result.cryptoObject).thenReturn(cryptoObject)
        whenever(cryptographyManager.encryptData(anyString(), any())).thenReturn(encryptedData)

        val callback = mock<BiometricsCallback>()
        val auth = subject.getAuthenticationCallback(callback, BiometricsController.BiometricsType.TYPE_REGISTER)
        auth.onAuthenticationSucceeded(result)

        verify(callback).onAuthSuccess(anyString())
    }

    @Test
    fun authCallbackSuccess_login() {
        val result = mock<BiometricPrompt.AuthenticationResult>()

        val data = "1234-_-1234"
        whenever(prefsUtil.encodedPin).thenReturn(data)

        whenever(result.cryptoObject).thenReturn(cryptoObject)
        val decryptedResult = "a string"
        whenever(cryptographyManager.decryptData(any(), any())).thenReturn(decryptedResult)

        val callback = mock<BiometricsCallback>()
        val auth = subject.getAuthenticationCallback(callback, BiometricsController.BiometricsType.TYPE_LOGIN)
        auth.onAuthenticationSucceeded(result)
        verify(callback).onAuthSuccess(decryptedResult)
    }

    @Test
    fun authCallbackErrorLockout() {
        val callback = mock<BiometricsCallback>()
        val auth = subject.getAuthenticationCallback(callback, BiometricsController.BiometricsType.TYPE_REGISTER)

        auth.onAuthenticationError(BiometricPrompt.ERROR_LOCKOUT, "a string")
        verify(callback).onAuthFailed(BiometricAuthLockout)
    }

    @Test
    fun authCallbackErrorLockoutPermanent() {
        val callback = mock<BiometricsCallback>()
        val auth = subject.getAuthenticationCallback(callback, BiometricsController.BiometricsType.TYPE_REGISTER)

        auth.onAuthenticationError(BiometricPrompt.ERROR_LOCKOUT_PERMANENT, "a string")
        verify(callback).onAuthFailed(BiometricAuthLockoutPermanent)
    }

    @Test
    fun authCallbackErrorUnknown() {
        val callback = mock<BiometricsCallback>()
        val auth = subject.getAuthenticationCallback(callback, BiometricsController.BiometricsType.TYPE_REGISTER)

        val error = "error"
        auth.onAuthenticationError(-999, error)

        verify(callback).onAuthFailed(any())
    }

    @Test
    fun authCallbackFailure() {
        val callback = mock<BiometricsCallback>()
        val auth = subject.getAuthenticationCallback(callback, BiometricsController.BiometricsType.TYPE_REGISTER)

        auth.onAuthenticationFailed()
        verify(callback).onAuthFailed(BiometricAuthFailed)
    }

    @Test
    fun authCallbackCancelledCta() {
        val callback = mock<BiometricsCallback>()
        val auth = subject.getAuthenticationCallback(callback, BiometricsController.BiometricsType.TYPE_REGISTER)

        val errString = "error"
        auth.onAuthenticationError(BiometricPrompt.ERROR_NEGATIVE_BUTTON, errString)
        verify(callback).onAuthCancelled()
    }

    @Test
    fun authCallbackCancelledCode() {
        val callback = mock<BiometricsCallback>()
        val auth = subject.getAuthenticationCallback(callback, BiometricsController.BiometricsType.TYPE_REGISTER)

        val errString = "error"
        auth.onAuthenticationError(BiometricPrompt.ERROR_USER_CANCELED, errString)
        verify(callback).onAuthCancelled()
    }

    @Test(expected = IllegalStateException::class)
    fun processDecryption_noData() {
        whenever(prefsUtil.encodedPin).thenReturn("")

        subject.processDecryption(cryptoObject)
    }

    @Test(expected = IllegalStateException::class)
    fun processDecryption_noCipher() {
        val data = "1234-_-1234" // "MTIzNC1fLTEyMzQ="
        whenever(prefsUtil.encodedPin).thenReturn(data)
        // cipher can only be null when the object is started with a different base param
        val cryptoObj = BiometricPrompt.CryptoObject(Signature.getInstance("SHA1withRSA"))
        subject.processDecryption(cryptoObj)
    }

    @Test
    fun processDecryption_success() {
        val data = "1234-_-1234" // "MTIzNC1fLTEyMzQ="
        whenever(prefsUtil.encodedPin).thenReturn(data)

        val pin = "1234"
        whenever(cryptographyManager.decryptData(any(), any())).thenReturn(pin)

        val decryptedData = subject.processDecryption(cryptoObject)
        Assert.assertEquals(pin, decryptedData)
    }

    @Test
    fun generateCompositeKey() {
        val byteArrayData = "data".toByteArray()
        val base64EncodedData = "ZGF0YQ==\n"
        val expectedValue = "$base64EncodedData-_-$base64EncodedData"

        val result = subject.generateCompositeKey(byteArrayData, byteArrayData)
        Assert.assertEquals(result, expectedValue)
    }

    @Test
    fun authForRegistration_success() {
        subject.biometricPrompt = mock()
        subject.promptInfo = mock()
        subject.callback = mock()

        doNothing().whenever(subject).checkIsInitialised()
        doReturn(true).whenever(subject).checkCanAuthenticate()

        val data = "1234-_-1234" // "MTIzNC1fLTEyMzQ="

        whenever(prefsUtil.encodedPin).thenReturn(data)
        val keyName = "astring"
        whenever(prefsUtil.encodedKeyName).thenReturn(keyName)

        val cipherSucess = CipherSuccess(mock())
        whenever(cryptographyManager.getInitializedCipherForEncryption(keyName)).thenReturn(cipherSucess)

        subject.authenticateForRegistration()

        verify(subject).handleCipherStates(cipherSucess)
    }

    @Test
    fun authForRegistration_invalidated() {
        subject.biometricPrompt = mock()
        subject.promptInfo = mock()
        subject.callback = mock()

        doNothing().whenever(subject).checkIsInitialised()
        doReturn(true).whenever(subject).checkCanAuthenticate()

        val data = "1234-_-1234" // "MTIzNC1fLTEyMzQ="

        whenever(prefsUtil.encodedPin).thenReturn(data)
        val keyName = "astring"
        whenever(prefsUtil.encodedKeyName).thenReturn(keyName)

        val cipherInvalidated = CipherInvalidatedError(KeyPermanentlyInvalidatedException())
        whenever(cryptographyManager.getInitializedCipherForEncryption(keyName)).thenReturn(cipherInvalidated)

        subject.authenticateForRegistration()

        verify(subject).handleCipherStates(cipherInvalidated)
    }

    @Test
    fun authForRegistration_other() {
        subject.biometricPrompt = mock()
        subject.promptInfo = mock()
        subject.callback = mock()

        doNothing().whenever(subject).checkIsInitialised()
        doReturn(true).whenever(subject).checkCanAuthenticate()

        val data = "1234-_-1234" // "MTIzNC1fLTEyMzQ="

        whenever(prefsUtil.encodedPin).thenReturn(data)
        val keyName = "astring"
        whenever(prefsUtil.encodedKeyName).thenReturn(keyName)

        val cipherError = CipherOtherError(Exception())
        whenever(cryptographyManager.getInitializedCipherForEncryption(keyName)).thenReturn(cipherError)

        subject.authenticateForRegistration()

        verify(subject).handleCipherStates(cipherError)
    }

    @Test
    fun authForLogin_success() {
        subject.biometricPrompt = mock()
        subject.promptInfo = mock()
        subject.callback = mock()

        doNothing().whenever(subject).checkIsInitialised()
        doReturn(true).whenever(subject).checkCanAuthenticate()

        val data = "1234-_-1234" // "MTIzNC1fLTEyMzQ="

        whenever(prefsUtil.encodedPin).thenReturn(data)
        val keyName = "astring"
        whenever(prefsUtil.encodedKeyName).thenReturn(keyName)

        val cipherSuccess = CipherSuccess(mock())
        whenever(cryptographyManager.getInitializedCipherForDecryption(any(), any())).thenReturn(cipherSuccess)

        subject.authenticateForLogin()

        verify(subject).handleCipherStates(cipherSuccess)
    }

    @Test
    fun authForLogin_invalidated() {
        subject.biometricPrompt = mock()
        subject.promptInfo = mock()
        subject.callback = mock()

        doNothing().whenever(subject).checkIsInitialised()
        doReturn(true).whenever(subject).checkCanAuthenticate()

        val data = "1234-_-1234" // "MTIzNC1fLTEyMzQ="

        whenever(prefsUtil.encodedPin).thenReturn(data)
        val keyName = "astring"
        whenever(prefsUtil.encodedKeyName).thenReturn(keyName)
        val cipherInvalidation = CipherInvalidatedError(KeyPermanentlyInvalidatedException())
        whenever(cryptographyManager.getInitializedCipherForDecryption(any(), any())).thenReturn(cipherInvalidation)

        subject.authenticateForLogin()

        verify(subject).handleCipherStates(cipherInvalidation)
    }

    @Test
    fun authForLogin_other() {
        subject.biometricPrompt = mock()
        subject.promptInfo = mock()
        subject.callback = mock()

        doNothing().whenever(subject).checkIsInitialised()
        doReturn(true).whenever(subject).checkCanAuthenticate()

        val data = "1234-_-1234" // "MTIzNC1fLTEyMzQ="

        whenever(prefsUtil.encodedPin).thenReturn(data)
        val keyName = "astring"
        whenever(prefsUtil.encodedKeyName).thenReturn(keyName)

        val cipherError = CipherOtherError(Exception())
        whenever(cryptographyManager.getInitializedCipherForDecryption(any(), any())).thenReturn(cipherError)

        subject.authenticateForLogin()

        verify(subject).handleCipherStates(cipherError)
    }

    @Test
    fun cipherState_success() {
        subject.callback = mock()
        subject.biometricPrompt = mock()
        subject.promptInfo = mock()

        subject.handleCipherStates(CipherSuccess(mock()))

        verify(subject.biometricPrompt).authenticate(any(), any())
    }

    @Test
    fun cipherState_invalidated() {
        subject.callback = mock()

        subject.handleCipherStates(CipherInvalidatedError(KeyPermanentlyInvalidatedException()))

        verify(subject.callback).onAuthFailed(BiometricKeysInvalidated)
    }

    @Test
    fun cipherState_other() {
        subject.callback = mock()

        subject.handleCipherStates(CipherInvalidatedError(Exception()))

        verify(subject.callback).onAuthFailed(any())
    }

    @Test(expected = IllegalStateException::class)
    fun uninitialisedRequirementsThrows() {
        subject.checkIsInitialised()
    }

    @Test
    fun initialisedRequirementsWorks() {
        subject.biometricPrompt = mock()
        subject.promptInfo = mock()

        subject.checkIsInitialised()
    }
}