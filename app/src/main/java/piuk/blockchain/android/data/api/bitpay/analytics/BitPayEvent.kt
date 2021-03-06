package piuk.blockchain.android.data.api.bitpay.analytics

import com.blockchain.notifications.analytics.AnalyticsEvent
import info.blockchain.balance.CryptoCurrency
import info.blockchain.balance.CryptoValue

sealed class BitPayEvent(
    override val event: String,
    override val params: Map<String, String> = emptyMap()
) : AnalyticsEvent {

    data class TxSuccess(val amount: CryptoValue) :
        BitPayEvent(
            PAYMENT_SUCCESS,
            mapOf(
                PARAM_AMOUNT to amount.toString(),
                PARAM_ASSET to amount.currency.networkTicker
            )
        )

    data class TxFailed(val message: String) :
        BitPayEvent(
            PAYMENT_FAILED,
            mapOf(PARAM_ERROR to message)
        )

    object InvoiceExpired : BitPayEvent(INVOICE_EXPIRED)

    data class QrCodeScanned(val asset: CryptoCurrency) :
        BitPayEvent(
            ADDRESS_SCANNED,
            mapOf(PARAM_ASSET to asset.networkTicker)
        )

    companion object {
        private const val PARAM_ASSET = "currency"
        private const val PARAM_ERROR = "error_message_string"
        private const val PARAM_AMOUNT = "amount"
        private const val ADDRESS_SCANNED = "bitpay_url_scanned"
        private const val INVOICE_EXPIRED = "bitpay_payment_expired"
        private const val PAYMENT_FAILED = "bitpay_payment_failure"
        private const val PAYMENT_SUCCESS = "bitpay_payment_success"
    }
}
