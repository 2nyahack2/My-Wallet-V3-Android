package com.blockchain.notifications.analytics

sealed class SwapAnalyticsEvents(
    override val event: String,
    override val params: Map<String, String> = mapOf()
) : AnalyticsEvent {

    object SwapTabItemClick : SwapAnalyticsEvents("swap_tab_item_click")
}
