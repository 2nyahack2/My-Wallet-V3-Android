package piuk.blockchain.android.ui.transfer.analytics

import com.blockchain.notifications.analytics.AnalyticsEvent
import piuk.blockchain.android.coincore.CryptoAccount
import piuk.blockchain.android.ui.transactionflow.analytics.toCategory

sealed class TransferAnalyticsEvent(
    override val event: String,
    override val params: Map<String, String> = emptyMap()
) : AnalyticsEvent {

    object NoBalanceViewDisplayed : TransferAnalyticsEvent("send_no_balance_seen")
    object NoBalanceCtaClicked : TransferAnalyticsEvent("send_no_balance_buy_clicked")

    data class SourceWalletSelected(
        val wallet: CryptoAccount
    ) : TransferAnalyticsEvent(
        "send_wallet_select",
        mapOf(
            PARAM_ASSET to wallet.asset.networkTicker,
            PARAM_WALLET to wallet.toCategory()
        )
    )

    companion object {
        private const val PARAM_ASSET = "asset"
        private const val PARAM_WALLET = "wallet"
    }
}
