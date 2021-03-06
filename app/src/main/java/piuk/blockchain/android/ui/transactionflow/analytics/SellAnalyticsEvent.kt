package piuk.blockchain.android.ui.transactionflow.analytics

import com.blockchain.notifications.analytics.AnalyticsEvent
import info.blockchain.balance.CryptoCurrency
import piuk.blockchain.android.ui.transactionflow.analytics.TxFlowAnalytics.Companion.constructMap

class SellAnalyticsEvent(
    override val event: String,
    override val params: Map<String, String> = emptyMap()
) : AnalyticsEvent {

    constructor(event: SellAnalytics, asset: CryptoCurrency, source: String) : this(event.value, constructMap(
        asset = asset, source = source, target = WALLET_TYPE_CUSTODIAL
    ))
}

enum class SellAnalytics(internal val value: String) {
    ConfirmationsDisplayed("sell_checkout_shown"),
    ConfirmTransaction("send_summary_confirm"),
    CancelTransaction("sell_checkout_cancel"),
    TransactionFailed("sell_checkout_error"),
    TransactionSuccess("sell_checkout_success"),
    EnterAmountCtaClick("sell_amount_confirm_click")
}
