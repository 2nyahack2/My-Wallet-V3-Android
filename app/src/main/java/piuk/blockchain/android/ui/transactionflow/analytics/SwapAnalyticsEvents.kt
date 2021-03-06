package piuk.blockchain.android.ui.transactionflow.analytics

import com.blockchain.notifications.analytics.AnalyticsEvent
import info.blockchain.balance.CryptoCurrency
import piuk.blockchain.android.ui.transactionflow.analytics.TxFlowAnalytics.Companion.constructMap

sealed class SwapAnalyticsEvents(
    override val event: String,
    override val params: Map<String, String> = emptyMap()
) : AnalyticsEvent {

    object VerifyNowClicked : SwapAnalyticsEvents("swap_kyc_verify_clicked")
    object TrendingPairClicked : SwapAnalyticsEvents("swap_suggested_pair_clicked")
    object NewSwapClicked : SwapAnalyticsEvents("swap_new_clicked")
    object FromPickerSeen : SwapAnalyticsEvents("swap_from_picker_seen")
    object FromAccountSelected : SwapAnalyticsEvents("swap_from_account_clicked")
    object ToPickerSeen : SwapAnalyticsEvents("swap_to_picker_seen")
    object SwapTargetAddressSheet : SwapAnalyticsEvents("swap_pair_locked_seen")
    object SwapEnterAmount : SwapAnalyticsEvents("swap_amount_screen_seen")
    object SwapConfirmSeen : SwapAnalyticsEvents("swap_checkout_shown")
    object SwapSilverLimitSheet : SwapAnalyticsEvents("swap_silver_limit_screen_seen")
    object SwapSilverLimitSheetCta : SwapAnalyticsEvents("swap_silver_limit_upgrade_click")
    object CancelTransaction : SwapAnalyticsEvents("swap_checkout_cancel")

    data class SwapConfirmPair(
        val asset: CryptoCurrency,
        val target: String
    ) : SwapAnalyticsEvents("swap_pair_locked_confirm", params = constructMap(asset = asset, target = target))

    data class EnterAmountCtaClick(
        val source: CryptoCurrency,
        val target: String
    ) : SwapAnalyticsEvents("swap_amount_screen_confirm", params = constructMap(source, target))

    data class SwapConfirmCta(
        val source: CryptoCurrency,
        val target: String
    ) : SwapAnalyticsEvents("swap_checkout_confirm", params = constructMap(source, target))

    data class TransactionSuccess(
        val asset: CryptoCurrency,
        val source: String,
        val target: String
    ) : SwapAnalyticsEvents("swap_checkout_success", params = constructMap(
        asset = asset,
        target = target,
        source = source
    ))

    data class TransactionFailed(
        val asset: CryptoCurrency,
        val target: String?,
        val source: String?,
        val error: String
    ) : SwapAnalyticsEvents("swap_checkout_error", params = constructMap(
        asset = asset,
        target = target,
        source = source,
        error = error
    ))
}
