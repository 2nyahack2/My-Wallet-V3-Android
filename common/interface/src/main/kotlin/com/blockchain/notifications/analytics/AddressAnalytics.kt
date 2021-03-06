package com.blockchain.notifications.analytics

sealed class AddressAnalytics(
    override val event: String,
    override val params: Map<String, String> = mapOf()
) : AnalyticsEvent {

    object ImportBTCAddress : AddressAnalytics(IMPORT_BTC_ADDRESS)

    companion object {
        private const val IMPORT_BTC_ADDRESS = "import"
    }
}