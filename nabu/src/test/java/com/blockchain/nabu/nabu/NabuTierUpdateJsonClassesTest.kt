package com.blockchain.nabu.nabu

import com.blockchain.serialization.JsonSerializable
import com.blockchain.nabu.models.responses.nabu.TierUpdateJson
import com.blockchain.testutils.`should be assignable from`
import org.junit.Test

class NabuTierUpdateJsonClassesTest {

    @Test
    fun `ensure TierUpdateJson is JsonSerializable for proguard`() {
        JsonSerializable::class `should be assignable from` TierUpdateJson::class
    }
}
