package piuk.blockchain.androidcore.utils

import android.content.SharedPreferences
import com.blockchain.logging.CrashLogger
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.never
import com.nhaarman.mockito_kotlin.validateMockitoUsage
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import junit.framework.Assert.assertEquals
import org.junit.After
import org.junit.Before
import org.junit.Test

class PrefsUtilTest {

    private val store: SharedPreferences = mock()
    private val backupStore: SharedPreferences = mock()
    private val editor: SharedPreferences.Editor = mock()
    private val idGenerator: DeviceIdGenerator = mock()
    private val uuidGenerator: UUIDGenerator = mock()
    private val crashLogger: CrashLogger = mock()

    private val subject: PrefsUtil = PrefsUtil(store, backupStore, idGenerator, uuidGenerator, crashLogger)

    @Before
    fun setUpSharedPrefs() {
        whenever(store.edit()).thenReturn(editor)
        whenever(editor.putString(any(), any())).thenReturn(editor)
        whenever(editor.remove(any())).thenReturn(editor)
        whenever(editor.clear()).thenReturn(editor)
    }

    @Test
    fun getDeviceId_qaRandomiseNotSet_nothingStored() {
        // Arrange
        whenever(idGenerator.generateId()).thenReturn(STATIC_DEVICE_ID)
        whenever(store.getBoolean(PrefsUtil.KEY_IS_DEVICE_ID_RANDOMISED, false))
            .thenReturn(false)
        whenever(store.getString(PrefsUtil.KEY_PRE_IDV_DEVICE_ID, ""))
            .thenReturn("")

        // Act
        val id = subject.deviceId

        // Assert
        assertEquals(id, STATIC_DEVICE_ID)
        verify(store).getBoolean(PrefsUtil.KEY_IS_DEVICE_ID_RANDOMISED, false)
        verify(uuidGenerator, never()).generateUUID()
    }

    @Test
    fun getDeviceId_qaRandomiseIsSet_nothingStored() {
        // Arrange
        whenever(idGenerator.generateId()).thenReturn(STATIC_DEVICE_ID)
        whenever(uuidGenerator.generateUUID()).thenReturn(RANDOM_DEVICE_ID)
        whenever(store.getBoolean(PrefsUtil.KEY_IS_DEVICE_ID_RANDOMISED, false))
            .thenReturn(true)
        whenever(store.getString(PrefsUtil.KEY_PRE_IDV_DEVICE_ID, ""))
            .thenReturn("")

        // Act
        val id = subject.deviceId

        // Assert
        assertEquals(id, RANDOM_DEVICE_ID)
        verify(store).getBoolean(PrefsUtil.KEY_IS_DEVICE_ID_RANDOMISED, false)
        verify(uuidGenerator).generateUUID()
    }

    @Test
    fun getDeviceId_qaRandomiseNotSet_returnStored() {
        // Arrange
        whenever(store.getBoolean(PrefsUtil.KEY_IS_DEVICE_ID_RANDOMISED, false))
            .thenReturn(false)
        whenever(store.getString(PrefsUtil.KEY_PRE_IDV_DEVICE_ID, ""))
            .thenReturn(STATIC_DEVICE_ID)

        // Act
        val id = subject.deviceId

        // Assert
        assertEquals(id, STATIC_DEVICE_ID)
        verify(store).getBoolean(PrefsUtil.KEY_IS_DEVICE_ID_RANDOMISED, false)
        verify(uuidGenerator, never()).generateUUID()
        verify(idGenerator, never()).generateId()
    }

    @Test
    fun getDeviceId_qaRandomiseIsSet_valueStored_returnRandomised() {
        // Arrange
        whenever(idGenerator.generateId()).thenReturn(STATIC_DEVICE_ID)
        whenever(uuidGenerator.generateUUID()).thenReturn(RANDOM_DEVICE_ID)
        whenever(store.getBoolean(PrefsUtil.KEY_IS_DEVICE_ID_RANDOMISED, false))
            .thenReturn(true)

        // Act
        val id = subject.deviceId

        // Assert
        assertEquals(id, RANDOM_DEVICE_ID)
        verify(store).getBoolean(PrefsUtil.KEY_IS_DEVICE_ID_RANDOMISED, false)
        verify(store, never()).getString(PrefsUtil.KEY_PRE_IDV_DEVICE_ID, "")
        verify(uuidGenerator).generateUUID()
    }

    @Test
    fun getSelectedCrypto_corruptedStore_returnDefault() {
        // Arrange
        whenever(store.getString(PrefsUtil.KEY_SELECTED_CRYPTO, PrefsUtil.DEFAULT_CRYPTO_CURRENCY.name))
            .thenReturn("NOPE")

        // Act
        val currency = subject.selectedCryptoCurrency

        // Arrange
        assertEquals(currency, PrefsUtil.DEFAULT_CRYPTO_CURRENCY)
        verify(editor).remove(PrefsUtil.KEY_SELECTED_CRYPTO)
    }

    companion object {
        private const val STATIC_DEVICE_ID = "12345678901234567890"
        private const val RANDOM_DEVICE_ID = "84962066204735275920"
    }

    @After
    fun validate() {
        validateMockitoUsage()
    }
}