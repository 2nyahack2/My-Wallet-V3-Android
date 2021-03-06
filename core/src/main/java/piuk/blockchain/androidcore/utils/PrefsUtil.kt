package piuk.blockchain.androidcore.utils

import android.annotation.SuppressLint
import android.app.backup.BackupManager
import android.content.SharedPreferences
import android.util.Base64
import androidx.annotation.VisibleForTesting
import com.blockchain.logging.CrashLogger
import info.blockchain.balance.CryptoCurrency
import info.blockchain.wallet.api.data.Settings.Companion.UNIT_FIAT
import info.blockchain.wallet.crypto.AESUtil
import piuk.blockchain.androidcore.BuildConfig
import piuk.blockchain.androidcore.utils.PersistentPrefs.Companion.KEY_SWIPE_TO_RECEIVE_ENABLED
import java.util.Currency
import java.util.Locale

interface UUIDGenerator {
    fun generateUUID(): String
}

class PrefsUtil(
    private val store: SharedPreferences,
    private val backupStore: SharedPreferences,
    private val idGenerator: DeviceIdGenerator,
    private val uuidGenerator: UUIDGenerator,
    private val crashLogger: CrashLogger
) : PersistentPrefs {

    private var isUnderAutomationTesting = false // Don't persist!

    override val isUnderTest: Boolean
        get() = isUnderAutomationTesting

    override fun setIsUnderTest() {
        isUnderAutomationTesting = true
    }

    override val deviceId: String
        get() {
            return if (qaRandomiseDeviceId) {
                uuidGenerator.generateUUID()
            } else {
                var deviceId = getValue(KEY_PRE_IDV_DEVICE_ID, "")
                if (deviceId.isEmpty()) {
                    deviceId = idGenerator.generateId()
                    setValue(KEY_PRE_IDV_DEVICE_ID, deviceId)
                }
                deviceId
            }
        }

    override var pinId: String
        get() = getValue(KEY_PIN_IDENTIFIER) ?: backupStore.getString(KEY_PIN_IDENTIFIER, null) ?: ""
        @SuppressLint("ApplySharedPref")
        set(value) {
            setValue(KEY_PIN_IDENTIFIER, value)
            backupStore.edit().putString(KEY_PIN_IDENTIFIER, value).commit()
            BackupManager.dataChanged(BuildConfig.APPLICATION_ID)
        }

    override var newSwapEnabled: Boolean
        get() = getValue(NEW_SWAP_ENABLED, false)
        set(value) {
            setValue(NEW_SWAP_ENABLED, value)
        }

    override var devicePreIDVCheckFailed: Boolean
        get() = getValue(KEY_PRE_IDV_FAILED, false)
        set(value) = setValue(KEY_PRE_IDV_FAILED, value)

    override var isOnboardingComplete: Boolean
        get() = getValue(PersistentPrefs.KEY_ONBOARDING_COMPLETE, false)
        set(completed) = setValue(PersistentPrefs.KEY_ONBOARDING_COMPLETE, completed)

    override var isCustodialIntroSeen: Boolean
        get() = getValue(KEY_CUSTODIAL_INTRO_SEEN, false)
        set(seen) = setValue(KEY_CUSTODIAL_INTRO_SEEN, seen)

    override var remainingSendsWithoutBackup: Int
        get() = getValue(KEY_REMAINING_SENDS_WITHOUT_BACKUP, MAX_ALLOWED_SENDS)
        set(remaining) = setValue(KEY_REMAINING_SENDS_WITHOUT_BACKUP, remaining)

    override val isLoggedOut: Boolean
        get() = getValue(KEY_LOGGED_OUT, true)

    override var qaRandomiseDeviceId: Boolean
        get() = getValue(KEY_IS_DEVICE_ID_RANDOMISED, false)
        set(value) = setValue(KEY_IS_DEVICE_ID_RANDOMISED, value)

    // SecurityPrefs
    override var disableRootedWarning: Boolean
        get() = getValue(PersistentPrefs.KEY_ROOT_WARNING_DISABLED, false)
        set(v) = setValue(PersistentPrefs.KEY_ROOT_WARNING_DISABLED, v)

    override var trustScreenOverlay: Boolean
        get() = getValue(PersistentPrefs.KEY_OVERLAY_TRUSTED, false)
        set(v) = setValue(PersistentPrefs.KEY_OVERLAY_TRUSTED, v)

    override val areScreenshotsEnabled: Boolean
        get() = getValue(PersistentPrefs.KEY_SCREENSHOTS_ENABLED, false)

    override fun setScreenshotsEnabled(enable: Boolean) =
        setValue(PersistentPrefs.KEY_SCREENSHOTS_ENABLED, enable)

    // From CurrencyPrefs
    override var selectedFiatCurrency: String
        get() = getValue(KEY_SELECTED_FIAT, "")
        set(fiat) {
            // We are seeing some crashes when this is read and is invalid when creating a FiatValue object.
            // So we'll try and catch them when it's written and find the root cause on a future iteration
            // Check the currency is supported and throw a meaningful exception message if it's not
            try {
                Currency.getInstance(fiat)
                setValue(KEY_SELECTED_FIAT, fiat)
            } catch (e: IllegalArgumentException) {
                crashLogger.logAndRethrowException(IllegalArgumentException("Unknown currency id: $fiat"))
            }
        }

    override var selectedCryptoCurrency: CryptoCurrency
        get() =
            try {
                CryptoCurrency.valueOf(getValue(KEY_SELECTED_CRYPTO, DEFAULT_CRYPTO_CURRENCY.name))
            } catch (e: IllegalArgumentException) {
                removeValue(KEY_SELECTED_CRYPTO)
                DEFAULT_CRYPTO_CURRENCY
            }
        set(crypto) = setValue(KEY_SELECTED_CRYPTO, crypto.name)

    override val defaultFiatCurrency: String
        get() = try {
            val localeFiat = Currency.getInstance(Locale.getDefault()).currencyCode
            if (UNIT_FIAT.contains(localeFiat)) localeFiat else DEFAULT_FIAT_CURRENCY
        } catch (e: Exception) {
            DEFAULT_FIAT_CURRENCY
        }

    // From ThePitLinkingPrefs
    override var pitToWalletLinkId: String
        get() = getValue(KEY_PIT_LINKING_LINK_ID, "")
        set(v) = setValue(KEY_PIT_LINKING_LINK_ID, v)

    override fun clearPitToWalletLinkId() {
        removeValue(KEY_PIT_LINKING_LINK_ID)
    }

    override fun simpleBuyState(): String? {
        return getValue(KEY_SIMPLE_BUY_STATE, "").takeIf { it != "" }
    }

    override fun cardState(): String? {
        return getValue(KEY_CARD_STATE, "").takeIf { it != "" }
    }

    override fun updateCardState(cardState: String) {
        setValue(KEY_CARD_STATE, cardState)
    }

    override fun clearCardState() {
        removeValue(KEY_CARD_STATE)
    }

    override fun updateSupportedCards(cardTypes: String) {
        setValue(KEY_SUPPORTED_CARDS_STATE, cardTypes)
    }

    override fun getSupportedCardTypes(): String? =
        getValue(KEY_SUPPORTED_CARDS_STATE, "").takeIf { it != "" }

    override fun updateSimpleBuyState(simpleBuyState: String) {
        setValue(KEY_SIMPLE_BUY_STATE, simpleBuyState)
    }

    override fun clearState() {
        removeValue(KEY_SIMPLE_BUY_STATE)
    }

    override var addCardInfoDismissed: Boolean
        get() = getValue(KEY_ADD_CARD_INFO, false)
        set(dismissed) = setValue(KEY_ADD_CARD_INFO, dismissed)

    override var hasSeenRatingDialog: Boolean
        get() = getValue(HAS_SEEN_RATING, false)
        set(value) = setValue(HAS_SEEN_RATING, value)

    override var preRatingActionCompletedTimes: Int
        get() = getValue(PRE_RATING_ACTION_COMPLETED_TIMES, 0)
        set(value) = setValue(PRE_RATING_ACTION_COMPLETED_TIMES, value)

    // From Onboarding
    override var swapIntroCompleted: Boolean
        get() = getValue(KEY_SWAP_INTRO_COMPLETED, false)
        set(v) = setValue(KEY_SWAP_INTRO_COMPLETED, v)

    override val isTourComplete: Boolean
        get() = getValue(KEY_INTRO_TOUR_COMPLETED, false)

    override val tourStage: String
        get() = getValue(KEY_INTRO_TOUR_CURRENT_STAGE, "")

    override fun setTourComplete() {
        setValue(KEY_INTRO_TOUR_COMPLETED, true)
        removeValue(KEY_INTRO_TOUR_CURRENT_STAGE)
    }

    override fun setTourStage(stageName: String) =
        setValue(KEY_INTRO_TOUR_CURRENT_STAGE, stageName)

    override fun resetTour() {
        removeValue(KEY_INTRO_TOUR_COMPLETED)
        removeValue(KEY_INTRO_TOUR_CURRENT_STAGE)
    }

    // Wallet Status
    override var lastBackupTime: Long
        get() = getValue(BACKUP_DATE_KEY, 0L)
        set(v) = setValue(BACKUP_DATE_KEY, v)

    override val isWalletBackedUp: Boolean
        get() = lastBackupTime != 0L

    override val isWalletFunded: Boolean
        get() = getValue(WALLET_FUNDED_KEY, false)

    override fun setWalletFunded() = setValue(WALLET_FUNDED_KEY, true)

    override var lastSwapTime: Long
        get() = getValue(SWAP_DATE_KEY, 0L)
        set(v) = setValue(SWAP_DATE_KEY, v)

    override val hasSwapped: Boolean
        get() = lastSwapTime != 0L

    override val hasMadeBitPayTransaction: Boolean
        get() = getValue(BITPAY_TRANSACTION_SUCCEEDED, false)

    override fun setBitPaySuccess() = setValue(BITPAY_TRANSACTION_SUCCEEDED, true)

    override fun setFeeTypeForAsset(cryptoCurrency: CryptoCurrency, type: Int) =
        setValue(NETWORK_FEE_PRIORITY_KEY + cryptoCurrency.networkTicker, type)

    override fun getFeeTypeForAsset(cryptoCurrency: CryptoCurrency): Int =
        getValue(NETWORK_FEE_PRIORITY_KEY + cryptoCurrency.networkTicker, -1)

    override val hasSeenSwapPromo: Boolean
        get() = getValue(SWAP_KYC_PROMO, false)

    override fun setSeenSwapPromo() = setValue(SWAP_KYC_PROMO, true)

    override val hasSeenTradingSwapPromo: Boolean
        get() = getValue(SWAP_TRADING_PROMO, false)

    override fun setSeenTradingSwapPromo() = setValue(SWAP_TRADING_PROMO, true)

    override val resendSmsRetries: Int
        get() = getValue(TWO_FA_SMS_RETRIES, MAX_ALLOWED_RETRIES)

    override fun setResendSmsRetries(retries: Int) {
        setValue(TWO_FA_SMS_RETRIES, retries)
    }

    override val isNewUser: Boolean
        get() = getValue(IS_NEW_USER, false)

    override fun setNewUser() {
        setValue(IS_NEW_USER, true)
    }

    // Notification prefs
    override var arePushNotificationsEnabled: Boolean
        get() = getValue(KEY_PUSH_NOTIFICATION_ENABLED, true)
        set(v) = setValue(KEY_PUSH_NOTIFICATION_ENABLED, v)

    override var firebaseToken: String
        get() = getValue(KEY_FIREBASE_TOKEN, "")
        set(v) = setValue(KEY_FIREBASE_TOKEN, v)

    @SuppressLint("ApplySharedPref")
    override fun backupCurrentPrefs(encryptionKey: String, aes: AESUtilWrapper) {
        backupStore.edit()
            .clear()
            .putString(KEY_PIN_IDENTIFIER, getValue(KEY_PIN_IDENTIFIER, ""))
            .putString(PersistentPrefs.KEY_ENCRYPTED_PASSWORD, getValue(PersistentPrefs.KEY_ENCRYPTED_PASSWORD, ""))
            .putString(
                KEY_ENCRYPTED_GUID,
                aes.encrypt(
                    getValue(PersistentPrefs.KEY_WALLET_GUID, ""),
                    encryptionKey,
                    AESUtil.PIN_PBKDF2_ITERATIONS_GUID
                )
            )
            .putString(
                KEY_ENCRYPTED_SHARED_KEY,
                aes.encrypt(
                    getValue(PersistentPrefs.KEY_SHARED_KEY, ""),
                    encryptionKey,
                    AESUtil.PIN_PBKDF2_ITERATIONS_SHAREDKEY
                )
            )
            .commit()

        BackupManager.dataChanged(BuildConfig.APPLICATION_ID)
    }

    override fun restoreFromBackup(decryptionKey: String, aes: AESUtilWrapper) {
        // Pull in the values from the backup, we don't have local state
        setValue(
            KEY_PIN_IDENTIFIER,
            backupStore.getString(KEY_PIN_IDENTIFIER, "") ?: ""
        )
        setValue(
            PersistentPrefs.KEY_ENCRYPTED_PASSWORD,
            backupStore.getString(PersistentPrefs.KEY_ENCRYPTED_PASSWORD, "") ?: ""
        )
        setValue(
            PersistentPrefs.KEY_WALLET_GUID,
            aes.decrypt(
                backupStore.getString(KEY_ENCRYPTED_GUID, ""),
                decryptionKey,
                AESUtil.PIN_PBKDF2_ITERATIONS_GUID
            )
        )
        setValue(
            PersistentPrefs.KEY_SHARED_KEY,
            aes.decrypt(
                backupStore.getString(KEY_ENCRYPTED_SHARED_KEY, ""),
                decryptionKey,
                AESUtil.PIN_PBKDF2_ITERATIONS_SHAREDKEY
            )
        )
    }

    override var backupEnabled: Boolean
        get() = getValue(KEY_CLOUD_BACKUP_ENABLED, true)
        set(value) {
            setValue(KEY_CLOUD_BACKUP_ENABLED, value)
            if (!value) {
                clearBackup()
            }
        }

    override fun hasBackup(): Boolean =
        backupEnabled && backupStore.getString(KEY_ENCRYPTED_GUID, "").isNullOrEmpty().not()

    @SuppressLint("ApplySharedPref")
    override fun clearBackup() {
        // We need to set all the backed values here and not just clear(), since that deletes the
        // prefs files, so there is nothing to back up, so the next restore will return the wallet
        // we just logged out of.
        backupStore.edit()
            .putString(KEY_PIN_IDENTIFIER, "")
            .putString(PersistentPrefs.KEY_ENCRYPTED_PASSWORD, "")
            .putString(KEY_ENCRYPTED_GUID, "")
            .putString(KEY_ENCRYPTED_SHARED_KEY, "")
            .commit()

        BackupManager.dataChanged(BuildConfig.APPLICATION_ID)
    }

    // SwipeToReceive
    override var offlineCacheData: String?
        get() = getValue(OFFLINE_CACHE_KEY)
        set(value) {
            if (value != null) {
                setValue(OFFLINE_CACHE_KEY, value)
            } else {
                clearLegacyCacheData()
                removeValue(OFFLINE_CACHE_KEY)
            }
        }

    override var offlineCacheEnabled: Boolean
        get() = getValue(KEY_SWIPE_TO_RECEIVE_ENABLED, true)
        set(value) = setValue(KEY_SWIPE_TO_RECEIVE_ENABLED, value)

    override var encodedPin: String
        get() = decodeFromBase64ToString(getValue(KEY_ENCRYPTED_PIN_CODE, ""))
        set(value) = setValue(KEY_ENCRYPTED_PIN_CODE, encodeToBase64(value))

    override var biometricsEnabled: Boolean
        get() = getValue(KEY_FINGERPRINT_ENABLED, false)
        set(value) = setValue(KEY_FINGERPRINT_ENABLED, value)

    override val encodedKeyName: String
        get() = KEY_ENCRYPTED_PIN_CODE

    override fun clearEncodedPin() {
        removeValue(KEY_ENCRYPTED_PIN_CODE)
    }

    private fun encodeToBase64(data: String) =
        Base64.encodeToString(data.toByteArray(charset("UTF-8")), Base64.DEFAULT)

    private fun decodeFromBase64ToString(data: String): String =
        String(Base64.decode(data.toByteArray(charset("UTF-8")), Base64.DEFAULT))

    private fun clearLegacyCacheData() {
        removeValue(KEY_SWIPE_RECEIVE_BTC_ADDRESSES)
        removeValue(KEY_SWIPE_RECEIVE_ETH_ADDRESS)
        removeValue(KEY_SWIPE_RECEIVE_BCH_ADDRESSES)
        removeValue(KEY_SWIPE_RECEIVE_XLM_ADDRESS)
        removeValue(KEY_SWIPE_RECEIVE_BTC_ACCOUNT_NAME)
        removeValue(KEY_SWIPE_RECEIVE_BCH_ACCOUNT_NAME)
    }

    // Raw accessors
    override fun getValue(name: String): String? =
        store.getString(name, null)

    override fun getValue(name: String, defaultValue: String): String =
        store.getString(name, defaultValue) ?: ""

    override fun getValue(name: String, defaultValue: Int): Int =
        store.getInt(name, defaultValue)

    override fun getValue(name: String, defaultValue: Long): Long =
        try {
            store.getLong(name, defaultValue)
        } catch (e: Exception) {
            store.getInt(name, defaultValue.toInt()).toLong()
        }

    override fun getValue(name: String, defaultValue: Boolean): Boolean =
        store.getBoolean(name, defaultValue)

    override fun setValue(name: String, value: String) {
        store.edit().putString(name, value).apply()
    }

    override fun setValue(name: String, value: Int) {
        store.edit().putInt(name, if (value < 0) 0 else value).apply()
    }

    override fun setValue(name: String, value: Long) {
        store.edit().putLong(name, if (value < 0L) 0L else value).apply()
    }

    override fun setValue(name: String, value: Boolean) {
        store.edit().putBoolean(name, value).apply()
    }

    override fun has(name: String): Boolean = store.contains(name)

    override fun removeValue(name: String) {
        store.edit().remove(name).apply()
    }

    override fun clear() {
        store.edit().clear().apply()
        clearBackup()
    }

    /**
     * Clears everything but the GUID for logging back in and the deviceId - for pre-IDV checking
     */
    override fun logOut() {
        val guid = getValue(PersistentPrefs.KEY_WALLET_GUID, "")
        val deviceId = getValue(KEY_PRE_IDV_DEVICE_ID, "")

        clear()

        setValue(KEY_LOGGED_OUT, true)
        setValue(PersistentPrefs.KEY_WALLET_GUID, guid)
        setValue(KEY_PRE_IDV_DEVICE_ID, deviceId)
    }

    /**
     * Reset value once user logged in
     */
    override fun logIn() {
        setValue(KEY_LOGGED_OUT, false)
    }

    companion object {
        @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
        const val DEFAULT_FIAT_CURRENCY = "USD"
        val DEFAULT_CRYPTO_CURRENCY = CryptoCurrency.BTC

        const val KEY_PRE_IDV_FAILED = "pre_idv_check_failed"

        @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
        const val KEY_PIN_IDENTIFIER = "pin_kookup_key" // Historical misspelling. DO NOT FIX.

        @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
        const val KEY_SELECTED_FIAT = "ccurrency" // Historical misspelling, don't update

        @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
        const val KEY_PRE_IDV_DEVICE_ID = "pre_idv_device_id"

        @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
        const val KEY_LOGGED_OUT = "logged_out"

        @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
        const val KEY_SELECTED_CRYPTO = "KEY_CURRENCY_CRYPTO_STATE"

        private const val KEY_PIT_LINKING_LINK_ID = "pit_wallet_link_id"
        private const val KEY_SIMPLE_BUY_STATE = "key_simple_buy_state"
        private const val KEY_CARD_STATE = "key_card_state"
        private const val KEY_ADD_CARD_INFO = "key_add_card_info"

        private const val KEY_SUPPORTED_CARDS_STATE = "key_supported_cards"

        private const val KEY_SWAP_INTRO_COMPLETED = "key_swap_intro_completed"
        private const val KEY_INTRO_TOUR_COMPLETED = "key_intro_tour_complete"
        private const val KEY_INTRO_TOUR_CURRENT_STAGE = "key_intro_tour_current_stage"
        private const val KEY_CUSTODIAL_INTRO_SEEN = "key_custodial_balance_intro_seen"
        private const val KEY_REMAINING_SENDS_WITHOUT_BACKUP = "key_remaining_sends_without_backup"
        private const val MAX_ALLOWED_SENDS = 5

        private const val BACKUP_DATE_KEY = "BACKUP_DATE_KEY"
        private const val SWAP_DATE_KEY = "SWAP_DATE_KEY"
        private const val WALLET_FUNDED_KEY = "WALLET_FUNDED_KEY"
        private const val BITPAY_TRANSACTION_SUCCEEDED = "BITPAY_TRANSACTION_SUCCEEDED"
        private const val NETWORK_FEE_PRIORITY_KEY = "fee_type_key_"
        private const val SWAP_KYC_PROMO = "SWAP_KYC_PROMO"
        private const val SWAP_TRADING_PROMO = "SWAP_TRADING_PROMO"
        private const val TWO_FA_SMS_RETRIES = "TWO_FA_SMS_RETRIES"
        private const val IS_NEW_USER = "IS_NEW_USER"
        private const val MAX_ALLOWED_RETRIES = 3

        // For QA:
        @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
        const val KEY_IS_DEVICE_ID_RANDOMISED = "random_device_id"

        const val NEW_SWAP_ENABLED = "swap_v_2_enabled"

        private const val KEY_FIREBASE_TOKEN = "firebase_token"
        private const val KEY_PUSH_NOTIFICATION_ENABLED = "push_notification_enabled"

        // Cloud backup keys
        private const val KEY_ENCRYPTED_GUID = "encrypted_guid"
        private const val KEY_ENCRYPTED_SHARED_KEY = "encrypted_shared_key"
        private const val KEY_CLOUD_BACKUP_ENABLED = "backup_enabled"

        // Rating
        private const val HAS_SEEN_RATING = "has_seen_rating"
        private const val PRE_RATING_ACTION_COMPLETED_TIMES = "pre_rating_action_completed_times"

        // Swipe to receive
        // Legacy keys. Only clear, add new data with new key
        private const val KEY_SWIPE_RECEIVE_BTC_ADDRESSES = "swipe_receive_addresses"
        private const val KEY_SWIPE_RECEIVE_ETH_ADDRESS = "swipe_receive_eth_address"
        private const val KEY_SWIPE_RECEIVE_BCH_ADDRESSES = "swipe_receive_bch_addresses"
        private const val KEY_SWIPE_RECEIVE_XLM_ADDRESS = "key_swipe_receive_xlm_address"
        private const val KEY_SWIPE_RECEIVE_BTC_ACCOUNT_NAME = "swipe_receive_account_name"
        private const val KEY_SWIPE_RECEIVE_BCH_ACCOUNT_NAME = "swipe_receive_bch_account_name"

        // New key
        private const val OFFLINE_CACHE_KEY = "key_offline_address_cache"

        // Auth prefs
        private const val KEY_ENCRYPTED_PIN_CODE = "encrypted_pin_code"
        private const val KEY_FINGERPRINT_ENABLED = "fingerprint_enabled"
    }
}
