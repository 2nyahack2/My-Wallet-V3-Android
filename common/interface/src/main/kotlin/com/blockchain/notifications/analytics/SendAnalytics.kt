package com.blockchain.notifications.analytics

sealed class SendAnalytics(override val event: String, override val params: Map<String, String> = mapOf()) :
    AnalyticsEvent {
    object QRButtonClicked : SendAnalytics("send_form_qr_button_click")
}