package com.blockchain.nabu

import org.junit.Test

class DisabledFeatureFlagTest {

    @Test
    fun `enabled returns false`() {
        DisabledFeatureFlag()
            .enabled
            .test()
            .assertValue(false)
    }
}