package piuk.blockchain.android.ui.sell

import com.blockchain.notifications.analytics.AnalyticsEvent

enum class SellAnalytics(
    override val event: String,
    override val params: Map<String, String> = emptyMap()
) : AnalyticsEvent {
    SellTabInfo("sell_send_now_clicked"),
    SellIntroCta("sell_now_banner_clicked")
}