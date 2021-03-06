package piuk.blockchain.android.ui.transactionflow.analytics

import com.blockchain.extensions.withoutNullValues
import com.blockchain.notifications.analytics.AnalyticsEvent
import info.blockchain.balance.CryptoCurrency
import piuk.blockchain.android.coincore.FeeLevel
import piuk.blockchain.android.ui.transactionflow.analytics.TxFlowAnalytics.Companion.FEE_SCHEDULE
import piuk.blockchain.android.ui.transactionflow.analytics.TxFlowAnalytics.Companion.PARAM_ASSET
import piuk.blockchain.android.ui.transactionflow.analytics.TxFlowAnalytics.Companion.PARAM_ERROR
import piuk.blockchain.android.ui.transactionflow.analytics.TxFlowAnalytics.Companion.PARAM_NEW_FEE
import piuk.blockchain.android.ui.transactionflow.analytics.TxFlowAnalytics.Companion.PARAM_OLD_FEE
import piuk.blockchain.android.ui.transactionflow.analytics.TxFlowAnalytics.Companion.PARAM_SOURCE
import piuk.blockchain.android.ui.transactionflow.analytics.TxFlowAnalytics.Companion.PARAM_TARGET

sealed class SendAnalyticsEvent(
    override val event: String,
    override val params: Map<String, String> = emptyMap()
) : AnalyticsEvent {

    object EnterAddressDisplayed : SendAnalyticsEvent("send_address_screen_seen")
    object QrCodeScanned : SendAnalyticsEvent("send_form_qr_button_click")
    object EnterAddressCtaClick : SendAnalyticsEvent("send_address_click_confirm")
    object EnterAmountDisplayed : SendAnalyticsEvent("send_enter_amount_seen")
    object SendMaxClicked : SendAnalyticsEvent("send_max_amount_clicked")
    object EnterAmountCtaClick : SendAnalyticsEvent("send_enter_amount_confirm")
    object ConfirmationsDisplayed : SendAnalyticsEvent("send_summary_shown")
    object CancelTransaction : SendAnalyticsEvent("send_summary_cancel")

    data class ConfirmTransaction(
        val asset: CryptoCurrency,
        val source: String,
        val target: String,
        val feeLevel: String
    ) : SendAnalyticsEvent(
        "send_summary_confirm",
        mapOf(
            PARAM_ASSET to asset.networkTicker,
            PARAM_SOURCE to source,
            PARAM_TARGET to target,
            FEE_SCHEDULE to feeLevel
        )
    )

    data class TransactionSuccess(val asset: CryptoCurrency, val target: String, val source: String) :
        SendAnalyticsEvent(
            "send_confirm_success",
            mapOf(
                PARAM_ASSET to asset.networkTicker,
                PARAM_TARGET to target,
                PARAM_SOURCE to source
            )
        )

    data class TransactionFailure(
        val asset: CryptoCurrency,
        val target: String?,
        val source: String?,
        val error: String
    ) : SendAnalyticsEvent(
        "send_confirm_error",
        mapOf(
            PARAM_ASSET to asset.networkTicker,
            PARAM_TARGET to target,
            PARAM_SOURCE to source,
            PARAM_ERROR to error
        ).withoutNullValues()
    )

    data class FeeChanged(val oldFee: FeeLevel, val newFee: FeeLevel) :
        SendAnalyticsEvent(
            "send_change_fee_click",
            mapOf(
                PARAM_OLD_FEE to oldFee.name,
                PARAM_NEW_FEE to newFee.name
            )
        )
}
