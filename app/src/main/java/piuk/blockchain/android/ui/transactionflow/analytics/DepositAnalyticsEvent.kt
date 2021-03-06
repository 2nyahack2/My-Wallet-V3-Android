package piuk.blockchain.android.ui.transactionflow.analytics

import com.blockchain.notifications.analytics.AnalyticsEvent
import info.blockchain.balance.CryptoCurrency

sealed class DepositAnalyticsEvent(
    override val event: String,
    override val params: Map<String, String> = emptyMap()
) : AnalyticsEvent {
    object EnterAmountSeen : DepositAnalyticsEvent("earn_amount_screen_seen")
    object ConfirmationsSeen : DepositAnalyticsEvent("earn_checkout_shown")
    object CancelTransaction : DepositAnalyticsEvent("earn_checkout_cancel")

    data class ConfirmationsCtaClick(
        val asset: CryptoCurrency
    ) : DepositAnalyticsEvent("earn_deposit_confirm_click", params = mapOf("asset" to asset.networkTicker))

    data class EnterAmountCtaClick(
        val asset: CryptoCurrency
    ) : DepositAnalyticsEvent("earn_amount_screen_confirm", params = mapOf("asset" to asset.networkTicker))

    data class TransactionSuccess(
        val asset: CryptoCurrency
    ) : DepositAnalyticsEvent("earn_checkout_success", params = mapOf("asset" to asset.networkTicker))

    data class TransactionFailed(
        val asset: CryptoCurrency
    ) : DepositAnalyticsEvent("earn_checkout_error", params = mapOf("asset" to asset.networkTicker))
}
