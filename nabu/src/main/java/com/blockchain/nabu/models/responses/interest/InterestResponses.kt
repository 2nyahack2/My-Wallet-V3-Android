package com.blockchain.nabu.models.responses.interest

data class InterestRateResponse(
    val rate: Double
)

data class InterestAccountDetailsResponse(
    val balance: String,
    val pendingInterest: String,
    val pendingDeposit: String,
    val totalInterest: String
)

data class InterestAddressResponse(
    val accountRef: String
)

data class InterestActivityResponse(
    val items: List<InterestActivityItemResponse>
)

data class InterestActivityItemResponse(
    val amount: InterestAmount,
    val amountMinor: String,
    val extraAttributes: InterestAttributes?,
    val id: String,
    val insertedAt: String,
    val state: String,
    val type: String
) {
    companion object {
        const val FAILED = "FAILED"
        const val REJECTED = "REJECTED"
        const val PROCESSING = "PROCESSING"
        const val COMPLETE = "COMPLETE"
        const val PENDING = "PENDING"
        const val MANUAL_REVIEW = "MANUAL_REVIEW"
        const val CLEARED = "CLEARED"
        const val REFUNDED = "REFUNDED"
        const val DEPOSIT = "DEPOSIT"
        const val WITHDRAWAL = "WITHDRAWAL"
        const val INTEREST_OUTGOING = "INTEREST_OUTGOING"
    }
}

data class InterestAmount(
    val symbol: String,
    val value: String
)

data class InterestAttributes(
    val address: String,
    val confirmations: Int,
    val hash: String,
    val id: String,
    val txHash: String,
    val beneficiary: InterestBeneficiary?
)

data class InterestBeneficiary(
    val user: String,
    val accountRef: String
)

data class InterestLimitsFullResponse(
    val limits: AssetLimitsResponse
)

data class AssetLimitsResponse(
    val assetMap: Map<String, InterestLimitsResponse>
)

data class InterestLimitsResponse(
    val currency: String,
    val lockUpDuration: Int,
    val maxWithdrawalAmount: String,
    val minDepositAmount: String
)

data class InterestEnabledResponse(
    val instruments: List<String>
)

data class InterestEligibilityFullResponse(
    val eligibleList: Map<String, InterestEligibilityResponse>
)

data class InterestEligibilityResponse(
    val eligible: Boolean,
    val ineligibilityReason: DisabledReason
)

enum class DisabledReason {
    REGION,
    KYC_TIER,
    BLOCKED,
    OTHER,
    NONE
}