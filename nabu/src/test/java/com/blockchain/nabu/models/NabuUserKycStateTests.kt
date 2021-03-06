package com.blockchain.nabu.models

import com.blockchain.nabu.models.responses.nabu.KycState
import com.blockchain.nabu.models.responses.nabu.NabuUser
import com.blockchain.nabu.models.responses.nabu.TierLevels
import com.blockchain.nabu.models.responses.nabu.UserState
import org.amshove.kluent.`should be`
import org.junit.Test

class NabuUserKycStateTests {

    @Test
    fun `zero tiers`() {
        emptyNabuUser().copy(
            kycState = KycState.None,
            tiers = TierLevels(
                current = 0,
                selected = 0,
                next = 0
            )
        ).apply {
            tierInProgress `should be` 0
            tierInProgressOrCurrentTier `should be` 0
        }
    }

    @Test
    fun `null values tiers`() {
        emptyNabuUser().copy(
            kycState = KycState.None,
            tiers = TierLevels(
                current = null,
                selected = null,
                next = null
            )
        ).apply {
            tierInProgress `should be` 0
            tierInProgressOrCurrentTier `should be` 0
        }
    }

    @Test
    fun `null tiers`() {
        emptyNabuUser().copy(
            kycState = KycState.Pending,
            tiers = null
        ).apply {
            tierInProgress `should be` 0
            tierInProgressOrCurrentTier `should be` 0
        }
    }

    @Test
    fun `user going for tier 1`() {
        emptyNabuUser().copy(
            kycState = KycState.None,
            tiers = TierLevels(
                current = 0,
                selected = 1,
                next = 1
            )
        ).apply {
            tierInProgress `should be` 1
            tierInProgressOrCurrentTier `should be` 1
        }
    }

    @Test
    fun `user on tier 1`() {
        emptyNabuUser().copy(
            kycState = KycState.Verified,
            tiers = TierLevels(
                current = 1,
                selected = 1,
                next = 2
            )
        ).apply {
            tierInProgress `should be` 0
            tierInProgressOrCurrentTier `should be` 1
        }
    }

    @Test
    fun `user going for tier 2`() {
        emptyNabuUser().copy(
            kycState = KycState.None,
            tiers = TierLevels(
                current = 1,
                selected = 2,
                next = 2
            )
        ).apply {
            tierInProgress `should be` 2
            tierInProgressOrCurrentTier `should be` 2
        }
    }

    @Test
    fun `user on tier 2`() {
        emptyNabuUser().copy(
            kycState = KycState.Verified,
            tiers = TierLevels(
                current = 2,
                selected = 2,
                next = 2
            )
        ).apply {
            tierInProgress `should be` 0
            tierInProgressOrCurrentTier `should be` 2
        }
    }

    @Test
    fun `user on tier 3`() {
        emptyNabuUser().copy(
            kycState = KycState.Verified,
            tiers = TierLevels(
                current = 3,
                selected = 2,
                next = 2
            )
        ).apply {
            tierInProgress `should be` 0
            tierInProgressOrCurrentTier `should be` 3
        }
    }

    @Test
    fun `user being forced to to go tier 2`() {
        emptyNabuUser().copy(
            kycState = KycState.None,
            tiers = TierLevels(
                current = 0,
                selected = 1,
                next = 2
            )
        ).apply {
            tierInProgress `should be` 2
            tierInProgressOrCurrentTier `should be` 2
        }
    }

    @Test
    fun `user opting for tier 2`() {
        emptyNabuUser().copy(
            kycState = KycState.None,
            tiers = TierLevels(
                current = 0,
                selected = 2,
                next = 1
            )
        ).apply {
            tierInProgress `should be` 2
            tierInProgressOrCurrentTier `should be` 2
        }
    }
}

private fun emptyNabuUser() =
    NabuUser(
        firstName = null,
        lastName = null,
        email = "",
        emailVerified = false,
        dob = null,
        mobile = null,
        mobileVerified = false,
        address = null,
        state = UserState.None,
        kycState = KycState.None,
        insertedAt = null
    )
