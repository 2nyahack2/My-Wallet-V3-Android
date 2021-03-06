package com.blockchain.notifications.analytics

enum class InterestAnalytics(
    override val event: String,
    override val params: Map<String, String> = emptyMap()
) : AnalyticsEvent {
    INTEREST_ANNOUNCEMENT_CTA("earn_banner_clicked"),
    INTEREST_DASHBOARD_KYC("earn_verify_identity_clicked"),
    INTEREST_DASHBOARD_ACTION("earn_interest_clicked"),
    INTEREST_SUMMARY_DEPOSIT_CTA("earn_deposit_clicked")
}