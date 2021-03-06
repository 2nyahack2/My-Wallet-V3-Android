package com.blockchain.nabu.models

import com.blockchain.nabu.models.responses.nabu.KycTierLevel
import com.blockchain.nabu.models.responses.nabu.KycTierState
import com.blockchain.nabu.models.responses.nabu.LimitsJson
import com.blockchain.nabu.models.responses.nabu.TierResponse
import com.blockchain.nabu.models.responses.nabu.KycTiers
import junit.framework.Assert.assertTrue
import org.junit.Test

class KycStateCombinerTest {

    @Test
    fun `combinedState when tier 2 is None`() {
        tiers(KycTierState.None,
            KycTierState.None).let {
            assertTrue(it.isInInitialState())
        }
        tiers(KycTierState.Pending,
            KycTierState.None).let {
            assertTrue(it.isPendingFor(KycTierLevel.SILVER))
        }
        tiers(KycTierState.Verified,
            KycTierState.None).let {
            assertTrue(it.isApprovedFor(KycTierLevel.SILVER))
        }
        tiers(KycTierState.Rejected,
            KycTierState.None).let {
            assertTrue(it.isRejectedFor(KycTierLevel.SILVER))
        }
    }

    @Test
    fun `combinedState when tier 2 is Pending`() {
        tiers(KycTierState.None,
            KycTierState.Pending).let {
            assertTrue(it.isPendingFor(KycTierLevel.GOLD))
        }

        tiers(KycTierState.Pending,
            KycTierState.Pending).let {
            assertTrue(it.isPendingFor(KycTierLevel.GOLD))
        }

        tiers(KycTierState.Verified,
            KycTierState.Pending).let {
            assertTrue(it.isPendingFor(KycTierLevel.GOLD))
        }

        tiers(KycTierState.Rejected,
            KycTierState.Pending).let {
            assertTrue(it.isPendingFor(KycTierLevel.GOLD))
        }
    }

    @Test
    fun `combinedState when tier 2 is Approved`() {
        tiers(KycTierState.None,
            KycTierState.Verified).let {
            assertTrue(it.isApprovedFor(KycTierLevel.GOLD))
        }
        tiers(KycTierState.Pending,
            KycTierState.Verified).let {
            assertTrue(it.isApprovedFor(KycTierLevel.GOLD))
        }
        tiers(KycTierState.Verified,
            KycTierState.Verified).let {
            assertTrue(it.isApprovedFor(KycTierLevel.GOLD))
        }
        tiers(KycTierState.Rejected,
            KycTierState.Verified).let {
            assertTrue(it.isApprovedFor(KycTierLevel.GOLD))
        }
    }

    @Test
    fun `combinedState when tier 2 is Rejected`() {
        tiers(KycTierState.None,
            KycTierState.Rejected).let {
            assertTrue(it.isRejectedFor(KycTierLevel.GOLD))
        }
        tiers(KycTierState.Pending,
            KycTierState.Rejected).let {
            assertTrue(it.isRejectedFor(KycTierLevel.GOLD))
        }
        tiers(KycTierState.Verified,
            KycTierState.Rejected).let {
            assertTrue(it.isRejectedFor(KycTierLevel.GOLD))
        }
        tiers(KycTierState.Rejected,
            KycTierState.Rejected).let {
            assertTrue(it.isRejectedFor(KycTierLevel.GOLD))
        }
    }
}

fun tiers(tier1State: KycTierState, tier2State: KycTierState): KycTiers {
    return KycTiers(
        tiersResponse = listOf(
            TierResponse(
                0,
                "Tier 0",
                state = KycTierState.Verified,
                limits = LimitsJson(
                    currency = "USD",
                    daily = null,
                    annual = null
                )
            ),
            TierResponse(
                1,
                "Tier 1",
                state = tier1State,
                limits = LimitsJson(
                    currency = "USD",
                    daily = null,
                    annual = 1000.0.toBigDecimal()
                )
            ),
            TierResponse(
                2,
                "Tier 2",
                state = tier2State,
                limits = LimitsJson(
                    currency = "USD",
                    daily = 25000.0.toBigDecimal(),
                    annual = null
                )
            )
        )
    )
}