package piuk.blockchain.android.ui.createwallet

import com.blockchain.notifications.analytics.AnalyticsEvent

sealed class WalletCreationEvent(
    override val event: String,
    override val params: Map<String, String> = emptyMap()
) : AnalyticsEvent {

    class RecoverWalletEvent(success: Boolean) : WalletCreationEvent(
        "Recover Wallet",
        mapOf("Success" to success.toString())
    )
}
