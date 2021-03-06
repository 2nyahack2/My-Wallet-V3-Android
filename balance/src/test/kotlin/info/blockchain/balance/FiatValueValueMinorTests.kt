package info.blockchain.balance

import org.amshove.kluent.`should equal`
import org.junit.Test

class FiatValueValueMinorTests {

    @Test
    fun `value minor gbp`() {
        1.2.gbp().toBigInteger() `should equal` 120.toBigInteger()
    }

    @Test
    fun `value minor gbp 2 dp`() {
        2.21.gbp().toBigInteger() `should equal` 221.toBigInteger()
    }

    @Test
    fun `value minor yen`() {
        543.jpy().toBigInteger() `should equal` 543.toBigInteger()
    }
}
