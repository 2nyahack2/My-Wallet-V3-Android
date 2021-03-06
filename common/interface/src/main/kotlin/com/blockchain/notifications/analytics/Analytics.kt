package com.blockchain.notifications.analytics

interface Analytics {
    fun logEvent(analyticsEvent: AnalyticsEvent)
    fun logEventOnce(analyticsEvent: AnalyticsEvent)
    fun logEventOnceForSession(analyticsEvent: AnalyticsEvent)
}

interface UserAnalytics {
    fun logUserProperty(userPropery: UserProperty)
    fun logUserId(userId: String)

    companion object {
        const val KYC_LEVEL = "kyc_level"
        const val KYC_UPDATED_DATE = "kyc_updated_date"
        const val WALLET_ID = "wallet_id"
        const val KYC_CREATION_DATE = "kyc_creation_date"
        const val EMAIL_VERIFIED = "email_verified"
        const val TWOFA_ENABLED = "two_fa_enabled"
        const val FUNDED_COINS = "funded_coins"
        const val USD_BALANCE = "usd_balance"
    }
}

interface AnalyticsEvent {
    val event: String
    val params: Map<String, String>
}

sealed class NotificationAnalytics(
    override val event: String,
    override val params: Map<String, String> = mapOf()
) : AnalyticsEvent
object NotificationReceived : NotificationAnalytics("pn_notification_received")
object NotificationAppOpened : NotificationAnalytics("pn_app_opened")

data class UserProperty(val property: String, val value: String) {
    companion object {
        const val MAX_VALUE_LEN = 36
    }
}
