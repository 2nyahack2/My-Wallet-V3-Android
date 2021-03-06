package com.blockchain.nabu.models.responses.nabu

import com.blockchain.serialization.JsonSerializable
import com.squareup.moshi.Json
import info.blockchain.balance.FiatValue
import piuk.blockchain.androidcore.utils.helperfunctions.unsafeLazy

data class KycTiers(
    @field:Json(name = "tiers") private val tiersResponse: List<TierResponse>
) : JsonSerializable {

    private val tiers: Tiers
        get() = KycTierLevel.values().map {
            it to Tier(tiersResponse[it.ordinal].state, Limits(tiersResponse[it.ordinal].limits))
        }.toTiersMap()

    fun isApprovedFor(level: KycTierLevel) = tiers[level].state == KycTierState.Verified
    fun isPendingFor(level: KycTierLevel) = tiers[level].state == KycTierState.Pending
    fun isUnderReviewFor(level: KycTierLevel) = tiers[level].state == KycTierState.UnderReview
    fun isPendingOrUnderReviewFor(level: KycTierLevel) = isUnderReviewFor(level) || isPendingFor(level)
    fun isRejectedFor(level: KycTierLevel) = tiers[level].state == KycTierState.Rejected
    fun isNotInitialisedFor(level: KycTierLevel) = tiers[level].state == KycTierState.None
    fun isInitialisedFor(level: KycTierLevel) = tiers[level].state != KycTierState.None
    fun isInitialised() = tiers[KycTierLevel.BRONZE].state != KycTierState.None
    fun isInInitialState() = tiers[KycTierLevel.SILVER].state == KycTierState.None
    fun tierForIndex(index: Int) = tiers[KycTierLevel.values()[index]]
    fun tierForLevel(level: KycTierLevel) = tiers[level]
    fun tierCompletedForLevel(level: KycTierLevel) =
        isApprovedFor(level) || isRejectedFor(level) || isPendingFor(level)

    fun highestActiveLevelState(): KycTierState =
        tiers.entries.reversed().firstOrNull {
            it.value.state != KycTierState.None
        }?.value?.state ?: KycTierState.None

    fun isVerified() = isApprovedFor(KycTierLevel.SILVER) || isApprovedFor(KycTierLevel.GOLD)

    companion object {
        fun default() =
            KycTiers(
                (0 until 3).map {
                    TierResponse(it, "", KycTierState.None, null)
                }
            )
    }
}

private fun List<Pair<KycTierLevel, Tier>>.toTiersMap(): Tiers = Tiers(this.toMap())

data class Limits(private val limits: LimitsJson?) {
    val dailyFiat: FiatValue? by unsafeLazy {
        limits?.daily?.let { FiatValue.fromMajor(limits.currency, it) }
    }

    val annualFiat: FiatValue? by unsafeLazy {
        limits?.annual?.let { FiatValue.fromMajor(limits.currency, it) }
    }
}

data class Tier(
    val state: KycTierState,
    val limits: Limits?
)

enum class KycTierState {
    None,
    Rejected,
    Pending,
    Verified,
    UnderReview,
    Expired
}

enum class KycTierLevel {
    BRONZE, SILVER, GOLD
}

class Tiers(private val map: Map<KycTierLevel, Tier>) : Map<KycTierLevel, Tier> by map {
    override operator fun get(key: KycTierLevel): Tier {
        return map.getOrElse(key) { throw IllegalArgumentException("$key is not a known KycTierLevel") }
    }
}