package com.blockchain.nabu.models.responses.swap

@Suppress("unused")
class QuoteRequest(
    private val product: String,
    private val direction: String,
    private val pair: String
)

class QuoteResponse(
    val id: String,
    val product: String,
    val pair: String,
    val quote: Quote,
    val networkFee: String,
    val staticFee: String,
    val createdAt: String,
    val sampleDepositAddress: String,
    val expiresAt: String
)

data class CustodialOrderResponse(
    val id: String,
    val state: String,
    val quote: OrderQuote,
    val kind: OrderKind,
    val pair: String,
    val priceFunnel: PriceFunnel,
    val createdAt: String,
    val updatedAt: String,
    val fiatValue: String,
    val fiatCurrency: String
) {
    companion object {
        const val CREATED = "CREATED"
        const val PENDING_CONFIRMATION = "PENDING_CONFIRMATION"
        const val PENDING_EXECUTION = "PENDING_EXECUTION"
        const val PENDING_DEPOSIT = "PENDING_DEPOSIT"
        const val PENDING_LEDGER = "PENDING_LEDGER"
        const val FINISH_DEPOSIT = "FINISH_DEPOSIT"
        const val PENDING_WITHDRAWAL = "PENDING_WITHDRAWAL"
        const val FAILED = "FAILED"
        const val FINISHED = "FINISHED"
        const val EXPIRED = "EXPIRED"
        const val CANCELED = "CANCELED"
    }
}

data class OrderQuote(
    val pair: String,
    val networkFee: String,
    val staticFee: String
)

data class OrderKind(
    val direction: String,
    val depositAddress: String?,
    val depositTxHash: String?,
    val withdrawalAddress: String?
)

class Quote(val priceTiers: List<InterpolationPrices>)

data class InterpolationPrices(
    val volume: String,
    val price: String,
    val marginPrice: String
)

class PriceFunnel(
    val inputMoney: String,
    val price: String,
    val networkFee: String,
    val staticFee: String,
    val outputMoney: String
)

class CreateOrderRequest(
    private val direction: String,
    private val quoteId: String,
    private val volume: String,
    private val destinationAddress: String? = null,
    private val refundAddress: String? = null
)

class UpdateSwapOrderBody(
    private val action: String
) {
    companion object {
        fun newInstance(success: Boolean): UpdateSwapOrderBody =
            if (success) {
                UpdateSwapOrderBody("DEPOSIT_SENT")
            } else {
                UpdateSwapOrderBody("CANCEL")
            }
    }
}

data class SwapLimitsResponse(
    val currency: String? = null,
    val minOrder: String? = null,
    val maxOrder: String? = null,
    val maxPossibleOrder: String? = null,
    val daily: TimeLimitsResponse? = null,
    val weekly: TimeLimitsResponse? = null,
    val annual: TimeLimitsResponse? = null
)

data class TimeLimitsResponse(
    val limit: String,
    val available: String,
    val used: String
)