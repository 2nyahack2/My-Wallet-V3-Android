package piuk.blockchain.android.data.biometrics

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import java.nio.charset.Charset
import java.security.InvalidAlgorithmParameterException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec

interface CryptographyManager {

    /**
     * This method first gets or generates an instance of SecretKey and then initializes the Cipher
     * with the key. The secret key uses [ENCRYPT_MODE][Cipher.ENCRYPT_MODE] is used.
     */
    fun getInitializedCipherForEncryption(keyName: String): CipherState

    /**
     * This method first gets or generates an instance of SecretKey and then initializes the Cipher
     * with the key. The secret key uses [DECRYPT_MODE][Cipher.DECRYPT_MODE] is used.
     */
    fun getInitializedCipherForDecryption(keyName: String, initializationVector: ByteArray): CipherState

    /**
     * The Cipher created with [getInitializedCipherForEncryption] is used here
     */
    fun encryptData(plaintext: String, cipher: Cipher): EncryptedData

    /**
     * The Cipher created with [getInitializedCipherForDecryption] is used here
     */
    fun decryptData(ciphertext: ByteArray, cipher: Cipher): String

    fun clearData(secretKeyName: String)
}

data class EncryptedData(val ciphertext: ByteArray, val initializationVector: ByteArray)

sealed class CipherState
data class CipherSuccess(val cipher: Cipher) : CipherState()
data class CipherInvalidatedError(val e: Throwable) : CipherState()
data class CipherNoSuitableBiometrics(val e: Throwable) : CipherState()
data class CipherOtherError(val e: Throwable) : CipherState()

class CryptographyManagerImpl : CryptographyManager {
    private val KEY_SIZE: Int = 256
    private val ANDROID_KEYSTORE = "AndroidKeyStore"
    private val ENCRYPTION_BLOCK_MODE = KeyProperties.BLOCK_MODE_CBC
    private val ENCRYPTION_PADDING = KeyProperties.ENCRYPTION_PADDING_PKCS7
    private val ENCRYPTION_ALGORITHM = KeyProperties.KEY_ALGORITHM_AES

    override fun getInitializedCipherForEncryption(keyName: String): CipherState =
        initialiseCipher(Cipher.ENCRYPT_MODE, keyName)

    override fun getInitializedCipherForDecryption(keyName: String, initializationVector: ByteArray): CipherState =
        initialiseCipher(Cipher.DECRYPT_MODE, keyName, initializationVector)

    private fun initialiseCipher(mode: Int, keyName: String, initializationVector: ByteArray? = null): CipherState {
        val cipher = getCipher()
        return try {
            val secretKey = getOrCreateSecretKey(keyName)
            if (mode == Cipher.ENCRYPT_MODE) {
                cipher.init(mode, secretKey)
            } else if (mode == Cipher.DECRYPT_MODE) {
                require(initializationVector != null)
                cipher.init(mode, secretKey, IvParameterSpec(initializationVector))
            }
            CipherSuccess(cipher)
        } catch (e: KeyPermanentlyInvalidatedException) {
            removeKey(keyName)
            CipherInvalidatedError(e)
        } catch (e: InvalidAlgorithmParameterException) {
            removeKey(keyName)
            CipherNoSuitableBiometrics(e)
        } catch (e: Exception) {
            CipherOtherError(e)
        }
    }

    private fun removeKey(keyName: String) {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null) // Keystore must be loaded before it can be accessed
        keyStore.deleteEntry(keyName)
    }

    override fun clearData(secretKeyName: String) {
        removeKey(secretKeyName)
    }

    override fun encryptData(plaintext: String, cipher: Cipher): EncryptedData {
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charset.forName("UTF-8")))
        return EncryptedData(ciphertext, cipher.iv)
    }

    override fun decryptData(ciphertext: ByteArray, cipher: Cipher): String {
        val plaintext = cipher.doFinal(ciphertext)
        return String(plaintext, Charset.forName("UTF-8"))
    }

    private fun getCipher(): Cipher {
        val transformation = "$ENCRYPTION_ALGORITHM/$ENCRYPTION_BLOCK_MODE/$ENCRYPTION_PADDING"
        return Cipher.getInstance(transformation)
    }

    private fun getOrCreateSecretKey(keyName: String): SecretKey {
        // If Secretkey was previously created for that keyName, then grab and return it.
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null) // Keystore must be loaded before it can be accessed
        keyStore.getKey(keyName, null)?.let { return it as SecretKey }

        // if you reach here, then a new SecretKey must be generated for that keyName
        val paramsBuilder = KeyGenParameterSpec.Builder(
            keyName,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
        paramsBuilder.apply {
            setBlockModes(ENCRYPTION_BLOCK_MODE)
            setEncryptionPaddings(ENCRYPTION_PADDING)
            setKeySize(KEY_SIZE)
            setUserAuthenticationRequired(true)
        }

        val keyGenParams = paramsBuilder.build()
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        keyGenerator.init(keyGenParams)
        return keyGenerator.generateKey()
    }
}
