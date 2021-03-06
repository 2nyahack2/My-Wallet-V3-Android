package piuk.blockchain.android.data.biometrics

import android.content.Context
import android.os.Build
import android.util.Base64
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AlertDialog
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.biometric.BiometricPrompt.ERROR_LOCKOUT
import androidx.biometric.BiometricPrompt.ERROR_LOCKOUT_PERMANENT
import androidx.biometric.BiometricPrompt.ERROR_NEGATIVE_BUTTON
import androidx.biometric.BiometricPrompt.ERROR_USER_CANCELED
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import org.koin.core.KoinComponent
import piuk.blockchain.android.R
import piuk.blockchain.androidcore.data.access.AccessState
import piuk.blockchain.androidcore.utils.PrefsUtil
import timber.log.Timber
import java.util.concurrent.Executor
import javax.crypto.IllegalBlockSizeException

interface BiometricsCallback {
    fun onAuthSuccess(data: String)

    fun onAuthFailed(error: BiometricAuthError)

    fun onAuthCancelled()
}

sealed class BiometricAuthError
object BiometricAuthLockout : BiometricAuthError()
object BiometricAuthLockoutPermanent : BiometricAuthError()
data class BiometricAuthOther(val error: String) : BiometricAuthError()
object BiometricAuthFailed : BiometricAuthError()
object BiometricKeysInvalidated : BiometricAuthError()
object BiometricsNoSuitableMethods : BiometricAuthError()

class BiometricsController(
    private val applicationContext: Context,
    private val prefs: PrefsUtil,
    private val accessState: AccessState,
    private val cryptographyManager: CryptographyManager
) : BiometricAuth, KoinComponent {

    private val biometricsManager by lazy {
        BiometricManager.from(applicationContext)
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    lateinit var biometricPrompt: BiometricPrompt

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    lateinit var promptInfo: BiometricPrompt.PromptInfo

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    lateinit var callback: BiometricsCallback

    private val secretKeyName: String by lazy {
        prefs.encodedKeyName
    }

    private val separator = "-_-"

    enum class BiometricsType {
        TYPE_REGISTER,
        TYPE_LOGIN
    }

    override val isFingerprintAvailable: Boolean
        get() = getStrongAuthMethods() == BiometricManager.BIOMETRIC_SUCCESS

    override val isHardwareDetected: Boolean
        get() = getStrongAuthMethods() != BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE

    override val areFingerprintsEnrolled: Boolean
        get() = getStrongAuthMethods() != BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED

    override val isFingerprintUnlockEnabled: Boolean
        get() = isFingerprintAvailable && prefs.biometricsEnabled && getDecodedData().isNotEmpty()

    override fun setFingerprintUnlockEnabled(enabled: Boolean) {
        if (!enabled) {
            cryptographyManager.clearData(secretKeyName)
            prefs.clearEncodedPin()
        }
        prefs.biometricsEnabled = enabled
    }

    private fun getStrongAuthMethods() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            biometricsManager.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG
            )
        } else {
            biometricsManager.canAuthenticate()
        }

    fun init(
        fragment: Fragment,
        type: BiometricsType,
        callback: BiometricsCallback
    ) {
        biometricPrompt = createBiometricPrompt(type, callback, fragment)
        promptInfo = createPromptInfo(type)
        this.callback = callback
    }

    fun init(
        activity: FragmentActivity,
        type: BiometricsType,
        callback: BiometricsCallback
    ) {
        biometricPrompt = createBiometricPrompt(type, callback, activity)
        promptInfo = createPromptInfo(type)
        this.callback = callback
    }

    private fun createBiometricPrompt(
        type: BiometricsType,
        callback: BiometricsCallback,
        fragment: Fragment
    ): BiometricPrompt = BiometricPrompt(fragment, getExecutor(), getAuthenticationCallback(callback, type))

    private fun getExecutor(): Executor = ContextCompat.getMainExecutor(applicationContext)

    private fun createBiometricPrompt(
        type: BiometricsType,
        callback: BiometricsCallback,
        activity: FragmentActivity
    ): BiometricPrompt = BiometricPrompt(activity, getExecutor(), getAuthenticationCallback(callback, type))

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun getAuthenticationCallback(
        callback: BiometricsCallback,
        type: BiometricsType
    ) = object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            super.onAuthenticationError(errorCode, errString)
            when (errorCode) {
                ERROR_NEGATIVE_BUTTON,
                ERROR_USER_CANCELED -> callback.onAuthCancelled()
                ERROR_LOCKOUT -> callback.onAuthFailed(BiometricAuthLockout)
                ERROR_LOCKOUT_PERMANENT -> callback.onAuthFailed(BiometricAuthLockoutPermanent)
                else -> callback.onAuthFailed(BiometricAuthOther(errString.toString()))
            }
            Timber.d("Biometric authentication failed: $errorCode - $errString")
        }

        override fun onAuthenticationFailed() {
            super.onAuthenticationFailed()
            callback.onAuthFailed(BiometricAuthFailed)
        }

        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            super.onAuthenticationSucceeded(result)
            if (type == BiometricsType.TYPE_REGISTER) {
                result.cryptoObject?.let {
                    try {
                        val encryptedString = processEncryption(it, accessState.pin)
                        storeEncodedData(encryptedString)
                        callback.onAuthSuccess(encryptedString)
                    } catch (e: IllegalBlockSizeException) {
                        callback.onAuthFailed(BiometricKeysInvalidated)
                    }
                }
            } else {
                result.cryptoObject?.let {
                    try {
                        callback.onAuthSuccess(processDecryption(it))
                    } catch (e: IllegalBlockSizeException) {
                        callback.onAuthFailed(BiometricKeysInvalidated)
                    }
                }
            }
        }
    }

    private fun createPromptInfo(type: BiometricsType): BiometricPrompt.PromptInfo {
        return if (type == BiometricsType.TYPE_REGISTER) {
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(applicationContext.getString(R.string.fingerprint_login_title))
                .setDescription(applicationContext.getString(R.string.fingerprint_register_description))
                .setConfirmationRequired(false)
                .setNegativeButtonText(
                    applicationContext.getString(R.string.common_cancel)
                )
                .build()
        } else {
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(applicationContext.getString(R.string.fingerprint_login_title))
                .setDescription(applicationContext.getString(R.string.fingerprint_login_description))
                .setConfirmationRequired(false)
                .setNegativeButtonText(
                    applicationContext.getString(R.string.fingerprint_use_pin)
                )
                .build()
        }
    }

    fun authenticateForRegistration() {
        checkIsInitialised()

        if (checkCanAuthenticate()) {
            handleCipherStates(cryptographyManager.getInitializedCipherForEncryption(secretKeyName))
        }
    }

    fun authenticateForLogin() {
        checkIsInitialised()

        if (checkCanAuthenticate()) {
            val dataAndIV = getDataAndIV(getDecodedData())
            val ivSpec = decodeFromBase64ToArray(dataAndIV.second)
            handleCipherStates(cryptographyManager.getInitializedCipherForDecryption(secretKeyName, ivSpec))
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun handleCipherStates(state: CipherState) =
        when (state) {
            is CipherSuccess -> {
                biometricPrompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(state.cipher))
            }
            is CipherInvalidatedError -> {
                setFingerprintUnlockEnabled(false)
                callback.onAuthFailed(BiometricKeysInvalidated)
            }
            is CipherNoSuitableBiometrics -> {
                setFingerprintUnlockEnabled(false)
                callback.onAuthFailed(BiometricsNoSuitableMethods)
            }
            is CipherOtherError -> {
                callback.onAuthFailed(BiometricAuthOther(state.e.message ?: "Unknown error"))
            }
        }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun checkIsInitialised() {
        if (!::biometricPrompt.isInitialized && !::promptInfo.isInitialized) {
            throw IllegalStateException("Attempting to use an uninitialised BiometricsController")
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun checkCanAuthenticate(): Boolean =
        getStrongAuthMethods() == BiometricManager.BIOMETRIC_SUCCESS

    /**
     * We must keep the current separator and ordering of data to maintain compatibility with the old fingerprinting library
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun processEncryption(cryptoObject: BiometricPrompt.CryptoObject, textToEncrypt: String): String {
        return cryptoObject.cipher?.let {
            val encryptedData = cryptographyManager.encryptData(textToEncrypt, it)

            generateCompositeKey(encryptedData.ciphertext, encryptedData.initializationVector)
        } ?: throw IllegalStateException("There is no cipher with which to encrypt")
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun generateCompositeKey(encryptedText: ByteArray, initializationVector: ByteArray) =
        encodeToBase64(encryptedText) + separator + encodeToBase64(initializationVector)

    /**
     * We must keep the current separator and ordering of data to maintain compatibility with the old fingerprinting library
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun processDecryption(cryptoObject: BiometricPrompt.CryptoObject): String {
        val dataAndIV = getDataAndIV(getDecodedData())
        val encryptedPin = decodeFromBase64ToArray(dataAndIV.first)

        cryptoObject.cipher?.let { cipher ->
            return cryptographyManager.decryptData(encryptedPin, cipher)
        } ?: throw IllegalStateException("There is no cipher with which to decrypt")
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun getDataAndIV(data: String): Pair<String, String> {
        if (!data.contains(separator)) {
            throw IllegalStateException("Passed data does not contain expected separator")
        }

        val split = data.split(separator.toRegex())
        if (split.size != 2 || (split.size == 2 && split[1].isEmpty())) {
            throw IllegalArgumentException("Passed data is incorrect. There was no IV specified with it.")
        }
        return Pair(split[0], split[1])
    }

    /**
     * Allows you to store the encrypted result of fingerprint authentication. The data is converted
     * into a Base64 string and written to shared prefs with a key. Please note that this doesn't
     * encrypt the data in any way, just obfuscates it.
     * @param data The data to be stored
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun storeEncodedData(data: String) {
        prefs.encodedPin = data
    }

    /**
     * Retrieve previously saved encoded & encrypted data from shared preferences
     * @return A [String] wrapping the saved String, or null if not found
     */
    fun getDecodedData(): String = prefs.encodedPin

    private fun encodeToBase64(data: ByteArray) =
        Base64.encodeToString(data, Base64.DEFAULT)

    private fun decodeFromBase64ToArray(data: String): ByteArray =
        Base64.decode(data, Base64.DEFAULT)

    /**
     * Reusable static functions for displaying different Biometrics error states
     */
    companion object {
        fun showAuthLockoutDialog(context: Context) {
            AlertDialog.Builder(context, R.style.AlertDialogStyle)
                .setTitle(R.string.biometrics_disabled_lockout_title)
                .setMessage(R.string.biometrics_disabled_lockout_desc)
                .setCancelable(false)
                .setPositiveButton(R.string.common_ok) { di, _ ->
                    di.dismiss()
                }
                .show()
        }

        fun showPermanentAuthLockoutDialog(context: Context) {
            AlertDialog.Builder(context, R.style.AlertDialogStyle)
                .setTitle(R.string.biometrics_disabled_lockout_title)
                .setMessage(R.string.biometrics_disabled_lockout_perm_desc)
                .setCancelable(false)
                .setPositiveButton(R.string.common_ok) { di, _ ->
                    di.dismiss()
                }
                .show()
        }

        fun showActionableInvalidatedKeysDialog(
            context: Context,
            positiveActionCallback: () -> Unit,
            negativeActionCallback: () -> Unit
        ) {
            AlertDialog.Builder(context, R.style.AlertDialogStyle)
                .setTitle(R.string.app_name)
                .setMessage(R.string.biometrics_key_invalidated_settings_description)
                .setCancelable(false)
                .setPositiveButton(R.string.common_try_again) { _, _ ->
                    positiveActionCallback.invoke()
                }
                .setNegativeButton(R.string.biometrics_action_settings) { di, _ ->
                    di.dismiss()
                    negativeActionCallback.invoke()
                }
                .show()
        }

        fun showInfoInvalidatedKeysDialog(
            context: Context
        ) {
            AlertDialog.Builder(context, R.style.AlertDialogStyle)
                .setTitle(R.string.biometrics_key_invalidated_title)
                .setMessage(R.string.biometrics_key_invalidated_description)
                .setCancelable(false)
                .setPositiveButton(
                    R.string.fingerprint_use_pin
                ) { di, _ -> di.dismiss() }
                .show()
        }

        fun showBiometricsGenericError(
            context: Context,
            error: String = ""
        ) {
            AlertDialog.Builder(context, R.style.AlertDialogStyle)
                .setTitle(R.string.fingerprint_fatal_error_brief)
                .setMessage(context.getString(R.string.fingerprint_fatal_error_desc, error))
                .setCancelable(false)
                .setPositiveButton(
                    R.string.fingerprint_use_pin
                ) { di, _ -> di.dismiss() }
                .create()
                .show()
        }
    }
}