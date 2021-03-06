package com.blockchain.notifications.analytics

sealed class TransactionsAnalyticsEvents(
    override val event: String,
    override val params: Map<String, String> = mapOf()
) : AnalyticsEvent {

    object TabItemClick : TransactionsAnalyticsEvents("transactions_tab_item_click")
}