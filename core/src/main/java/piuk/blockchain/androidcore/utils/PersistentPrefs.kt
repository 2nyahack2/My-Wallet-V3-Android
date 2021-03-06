package piuk.blockchain.androidcore.utils

import com.blockchain.preferences.AuthPrefs
import com.blockchain.preferences.CurrencyPrefs
import com.blockchain.preferences.DashboardPrefs
import com.blockchain.preferences.NotificationPrefs
import com.blockchain.preferences.OfflineCachePrefs
import com.blockchain.preferences.RatingPrefs
import com.blockchain.preferences.SecurityPrefs
import com.blockchain.preferences.SimpleBuyPrefs
import com.blockchain.preferences.ThePitLinkingPrefs
import com.blockchain.preferences.WalletStatus

interface PersistentPrefs :
    CurrencyPrefs,
    NotificationPrefs,
    DashboardPrefs,
    SecurityPrefs,
    ThePitLinkingPrefs,
    SimpleBuyPrefs,
    RatingPrefs,
    WalletStatus,
    OfflineCachePrefs,
    EncryptedPrefs,
    AuthPrefs {

    val isLoggedOut: Boolean

    val deviceId: String // Pre-IDV device identifier
    var devicePreIDVCheckFailed: Boolean // Pre-IDV check has failed! Don't show 'gold' announce cards etc

    var pinId: String

    var newSwapEnabled: Boolean

    fun getValue(name: String): String?
    fun getValue(name: String, defaultValue: String): String
    fun getValue(name: String, defaultValue: Int): Int
    fun getValue(name: String, defaultValue: Long): Long
    fun getValue(name: String, defaultValue: Boolean): Boolean

    fun setValue(name: String, value: String)
    fun setValue(name: String, value: Int)
    fun setValue(name: String, value: Long)
    fun setValue(name: String, value: Boolean)

    fun has(name: String): Boolean
    fun removeValue(name: String)

    fun clear()

    fun logOut()

    fun logIn()

    // Allow QA to randomise device ids when testing kyc
    var qaRandomiseDeviceId: Boolean

    companion object {
        const val KEY_ENCRYPTED_PASSWORD = "encrypted_password"
        const val KEY_WALLET_GUID = "guid"
        const val KEY_SHARED_KEY = "sharedKey"
        const val KEY_PIN_FAILS = "pin_fails"

        const val KEY_EMAIL = "email"
        const val KEY_EMAIL_VERIFIED = "code_verified"
        const val KEY_SCHEME_URL = "scheme_url"
        const val KEY_METADATA_URI = "metadata_uri"
        const val KEY_NEWLY_CREATED_WALLET = "newly_created_wallet"
        const val KEY_RESTORED_WALLET = "restored_wallet"
        const val KEY_RECEIVE_SHORTCUTS_ENABLED = "receive_shortcuts_enabled"
        const val KEY_SCREENSHOTS_ENABLED = "screenshots_enabled"
        const val KEY_ONBOARDING_COMPLETE = "onboarding_complete_1"
        const val KEY_OVERLAY_TRUSTED = "overlay_trusted"

        const val KEY_ROOT_WARNING_DISABLED = "disable_root_warning"

        // Swipe To Receive
        const val KEY_SWIPE_TO_RECEIVE_ENABLED = "swipe_to_receive_enabled"
    }
}
