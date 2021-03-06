package com.blockchain.nabu.models.responses.nabu

import com.blockchain.serialization.JsonSerializable
import com.blockchain.nabu.datamanagers.BillingAddress
import com.squareup.moshi.FromJson
import com.squareup.moshi.Json
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.ToJson
import kotlin.math.max

data class NabuUser(
    val firstName: String?,
    val lastName: String?,
    val email: String = "",
    val emailVerified: Boolean = false,
    val dob: String?,
    val mobile: String?,
    val mobileVerified: Boolean,
    val address: Address?,
    val state: UserState,
    val kycState: KycState,
    private val productsUsed: ProductsUsed? = null,
    private val settings: NabuSettings? = null,
    val resubmission: Any? = null,
    /**
     * ISO-8601 Timestamp w/millis, eg 2018-08-15T17:00:45.129Z
     */
    val insertedAt: String? = null,
    /**
     * ISO-8601 Timestamp w/millis, eg 2018-08-15T17:00:45.129Z
     */
    val updatedAt: String? = null,
    val tags: Map<String, Map<String, Any>>? = null,
    val userName: String? = null,
    val tiers: TierLevels? = null,
    val walletGuid: String? = null
    // val depositAddresses":{  },  // Not sure what format these have yet. TODO: Find out
) : JsonSerializable {
    val tierInProgress
        get() =
            tiers?.let {
                if (kycState == KycState.None) {
                    max(it.selected ?: 0, it.next ?: 0)
                } else {
                    0
                }
            } ?: 0

    val tierInProgressOrCurrentTier
        get() =
            tiers?.let {
                if (kycState == KycState.Verified) {
                    it.current
                } else {
                    max(it.selected ?: 0, it.next ?: 0)
                }
            } ?: 0

    val currentTier
        get() =
            tiers?.let {
                if (kycState == KycState.Verified) {
                    it.current
                } else {
                    0
                }
            } ?: 0

    fun requireCountryCode(): String {
        return address?.countryCode ?: throw IllegalStateException("User has no country code set")
    }

    val isMarkedForResubmission: Boolean
        get() = resubmission != null

    val isStxAirdropRegistered: Boolean
        get() = tags?.get("BLOCKSTACK") != null

    val exchangeEnabled: Boolean
        get() = productsUsed?.exchange ?: settings?.MERCURY_EMAIL_VERIFIED ?: false
}

data class TierLevels(
    val current: Int?,
    val selected: Int?,
    val next: Int?
)

data class Address(
    val line1: String?,
    val line2: String?,
    val city: String?,
    val state: String?,
    val postCode: String,
    @field:Json(name = "country") val countryCode: String?
)

data class AddAddressRequest(
    val address: Address
) {
    companion object {

        fun fromAddressDetails(
            line1: String,
            line2: String?,
            city: String,
            state: String?,
            postCode: String,
            countryCode: String
        ): AddAddressRequest = AddAddressRequest(
            Address(
                line1,
                line2,
                city,
                state,
                postCode,
                countryCode
            )
        )

        fun fromBillingAddress(
            billingAddress: BillingAddress
        ): Address =
            Address(
                line1 = billingAddress.addressLine1,
                line2 = billingAddress.addressLine2,
                city = billingAddress.city,
                countryCode = billingAddress.countryCode,
                postCode = billingAddress.postCode,
                state = billingAddress.state
            )
    }
}

sealed class KycState {
    object None : KycState()
    object Pending : KycState()
    object UnderReview : KycState()
    object Rejected : KycState()
    object Expired : KycState()
    object Verified : KycState()
}

sealed class UserState {
    object None : UserState()
    object Created : UserState()
    object Active : UserState()
    object Blocked : UserState()
}

class KycStateAdapter {

    @FromJson
    fun fromJson(input: String): KycState = when (input) {
        NONE -> KycState.None
        PENDING -> KycState.Pending
        UNDER_REVIEW -> KycState.UnderReview
        REJECTED -> KycState.Rejected
        EXPIRED -> KycState.Expired
        VERIFIED -> KycState.Verified
        else -> throw JsonDataException("Unknown KYC State: $input, unsupported data type")
    }

    @ToJson
    fun toJson(kycState: KycState): String = when (kycState) {
        KycState.None -> NONE
        KycState.Pending -> PENDING
        KycState.UnderReview -> UNDER_REVIEW
        KycState.Rejected -> REJECTED
        KycState.Expired -> EXPIRED
        KycState.Verified -> VERIFIED
    }

    private companion object {
        private const val NONE = "NONE"
        private const val PENDING = "PENDING"
        private const val UNDER_REVIEW = "UNDER_REVIEW"
        private const val REJECTED = "REJECTED"
        private const val EXPIRED = "EXPIRED"
        private const val VERIFIED = "VERIFIED"
    }
}

class UserStateAdapter {

    @FromJson
    fun fromJson(input: String): UserState = when (input) {
        NONE -> UserState.None
        CREATED -> UserState.Created
        ACTIVE -> UserState.Active
        BLOCKED -> UserState.Blocked
        else -> throw JsonDataException("Unknown User State: $input, unsupported data type")
    }

    @ToJson
    fun toJson(userState: UserState): String = when (userState) {
        UserState.None -> NONE
        UserState.Created -> CREATED
        UserState.Active -> ACTIVE
        UserState.Blocked -> BLOCKED
    }

    private companion object {
        private const val NONE = "NONE"
        private const val CREATED = "CREATED"
        private const val ACTIVE = "ACTIVE"
        private const val BLOCKED = "BLOCKED"
    }
}

data class ProductsUsed(
    val exchange: Boolean = false
)

data class NabuSettings(
    val MERCURY_EMAIL_VERIFIED: Boolean
)