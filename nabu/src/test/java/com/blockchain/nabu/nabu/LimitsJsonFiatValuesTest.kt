package com.blockchain.nabu.nabu

import com.blockchain.nabu.models.responses.nabu.Limits
import com.blockchain.nabu.models.responses.nabu.LimitsJson
import com.blockchain.testutils.gbp
import com.blockchain.testutils.usd
import org.amshove.kluent.`should be`
import org.amshove.kluent.`should equal`
import org.junit.Test

class LimitsJsonFiatValuesTest {

    @Test
    fun `null daily fiat`() {
        Limits(LimitsJson(
            currency = "USD",
            daily = null,
            annual = 100.toBigDecimal()
        )).dailyFiat `should be` null
    }

    @Test
    fun `null annual fiat`() {
        Limits(LimitsJson(
            currency = "USD",
            daily = 100.toBigDecimal(),
            annual = null
        )).annualFiat `should be` null
    }

    @Test
    fun `can get daily fiat`() {
        Limits(LimitsJson(
            currency = "USD",
            daily = 100.toBigDecimal(),
            annual = null
        )).dailyFiat `should equal` 100.usd()
    }

    @Test
    fun `can get annual fiat`() {
        Limits(LimitsJson(
            currency = "GBP",
            daily = 100.toBigDecimal(),
            annual = 50.12.toBigDecimal()
        )).annualFiat `should equal` 50.12.gbp()
    }
}
