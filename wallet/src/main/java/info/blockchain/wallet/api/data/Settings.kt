package info.blockchain.wallet.api.data

import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY,
    getterVisibility = JsonAutoDetect.Visibility.NONE,
    setterVisibility = JsonAutoDetect.Visibility.NONE,
    creatorVisibility = JsonAutoDetect.Visibility.NONE,
    isGetterVisibility = JsonAutoDetect.Visibility.NONE)
data class Settings(

    @JsonProperty("btc_currency")
    private val btcCurrency: String = "",
    @JsonProperty("notifications_type")
    val notificationsType: List<Int> = emptyList(),
    @JsonProperty("language")
    private val language: String = "",
    @JsonProperty("notifications_on")
    private val notificationsOn: Int = 0,
    @JsonProperty("ip_lock_on")
    private val ipLockOn: Int = 0,
    @JsonProperty("dial_code")
    private val dialCode: String = "",

    @JsonProperty("block_tor_ips")
    private val blockTorIps: Int = 0,
    @JsonProperty("currency")
    val currency: String = "",

    @JsonProperty("notifications_confirmations")
    private val notificationsConfirmations: Int = 0,
    @JsonProperty("auto_email_backup")
    private val autoEmailBackup: Int = 0,

    @JsonProperty("never_save_auth_type")
    private val neverSaveAuthType: Int = 0,

    @JsonProperty("email")
    val email: String = "",

    @JsonProperty("sms_number")
    private val _smsNumber: String? = null,

    @JsonProperty("sms_verified")
    private val smsVerified: Int = 0,

    @JsonProperty("is_api_access_enabled")
    private val isApiAccessEnabled: Int = 0,

    @JsonProperty("auth_type")
    val authType: Int = 0,

    @JsonProperty("my_ip")
    private val myIp: String = "",

    @JsonProperty("email_verified")
    private val emailVerified: Int = 0,

    @JsonProperty("password_hint1")
    private val passwordHint1: String = "",

    @JsonProperty("country_code")
    val countryCode: String = "",

    @JsonProperty("state")
    private val state: String = "",

    @JsonProperty("logging_level")
    private val loggingLevel: Int = 0,

    @JsonProperty("guid")
    val guid: String = "",

    @JsonProperty("invited")
    private val invited: HashMap<String, Boolean>? = null

) {

    val isEmailVerified: Boolean
        get() = emailVerified.toBoolean()

    val isSmsVerified: Boolean
        get() = smsVerified.toBoolean()

    val isNotificationsOn: Boolean
        get() = notificationsOn.toBoolean()

    val isBlockTorIps: Boolean
        get() = blockTorIps.toBoolean()

    private fun Int.toBoolean(): Boolean {
        return this != 0
    }

    val smsNumber: String
        get() = _smsNumber ?: ""

    companion object {
        @JsonIgnore
        const val NOTIFICATION_ON = 2

        @JsonIgnore
        const val NOTIFICATION_OFF = 0

        @JsonIgnore
        const val NOTIFICATION_TYPE_NONE = 0

        @JsonIgnore
        const val NOTIFICATION_TYPE_EMAIL = 1

        @JsonIgnore
        const val NOTIFICATION_TYPE_SMS = 32

        @JsonIgnore
        const val NOTIFICATION_TYPE_ALL = 33

        @JsonIgnore
        const val AUTH_TYPE_OFF = 0

        @JsonIgnore
        const val AUTH_TYPE_YUBI_KEY = 1

        @JsonIgnore
        const val AUTH_TYPE_EMAIL = 2

        @JsonIgnore
        const val AUTH_TYPE_GOOGLE_AUTHENTICATOR = 4

        @JsonIgnore
        const val AUTH_TYPE_SMS = 5

        @JsonIgnore
        const val UNIT_BTC = "BTC"

        @JsonIgnore
        const val UNIT_MBC = "MBC"

        @JsonIgnore
        const val UNIT_UBC = "UBC"

        @JsonIgnore
        val UNIT_FIAT = arrayOf(
            "AUD", "BRL", "CAD", "CHF", "CLP", "CNY", "DKK", "EUR", "GBP", "HKD",
            "ISK", "JPY", "KRW", "NZD", "PLN", "RUB", "SEK", "SGD", "THB", "TWD", "USD"
        )
    }
}