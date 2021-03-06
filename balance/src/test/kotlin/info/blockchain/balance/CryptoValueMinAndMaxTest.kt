package info.blockchain.balance

import info.blockchain.balance.Money.Companion.max
import info.blockchain.balance.Money.Companion.min
import org.amshove.kluent.`should be`
import org.amshove.kluent.`should throw the Exception`
import org.amshove.kluent.`with message`
import org.junit.Test

class CryptoValueMinAndMaxTest {

    @Test
    fun `max of two`() {
        val a = 1.satoshiCash()
        val b = 2.satoshiCash()
        max(a, b) `should be` b
    }

    @Test
    fun `max of two reversed`() {
        val a = 1.satoshiCash()
        val b = 2.satoshiCash()
        max(b, a) `should be` b
    }

    @Test
    fun `max of two the same`() {
        val a = 1.satoshiCash()
        val b = 1.satoshiCash()
        max(a, b) `should be` a
    }

    @Test
    fun `min of two`() {
        val a = 1.satoshiCash()
        val b = 2.satoshiCash()
        min(a, b) `should be` a
    }

    @Test
    fun `min of two reversed`() {
        val a = 1.satoshiCash()
        val b = 2.satoshiCash()
        min(b, a) `should be` a
    }

    @Test
    fun `min of two the same`() {
        val a = 1.satoshiCash()
        val b = 1.satoshiCash()
        min(a, b) `should be` a
    }

    @Test
    fun `max of two with different currencies`() {
        val a = 1.satoshi()
        val b = 2.satoshiCash();
        {
            max(a, b)
        } `should throw the Exception` Exception::class `with message` "Can't compare BTC and BCH"
    }

    @Test
    fun `min of two with different currencies`() {
        val a = 1.ether()
        val b = 2.satoshi();
        {
            min(a, b)
        } `should throw the Exception` Exception::class `with message` "Can't compare ETH and BTC"
    }
}
