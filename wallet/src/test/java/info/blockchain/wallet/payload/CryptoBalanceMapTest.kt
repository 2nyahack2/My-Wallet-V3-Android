package info.blockchain.wallet.payload

import com.blockchain.testutils.satoshi
import com.blockchain.testutils.satoshiCash
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import info.blockchain.balance.CryptoCurrency
import info.blockchain.balance.CryptoValue
import org.amshove.kluent.`it returns`
import org.amshove.kluent.`should equal`
import org.amshove.kluent.`should throw the Exception`
import org.amshove.kluent.`with message`
import org.junit.Test
import java.math.BigInteger

class CryptoBalanceMapTest {

    @Test
    fun `empty values`() {
        calculateCryptoBalanceMap(
            CryptoCurrency.BTC,
            { emptyMap<String, Long>() }.toBalanceQuery(),
            emptySet(),
            emptySet()
        ).apply {
            totalSpendable `should equal` CryptoValue.ZeroBtc
            totalSpendableLegacy `should equal` CryptoValue.ZeroBtc
        }
    }

    @Test
    fun `XPub appears in total balance - alternative currency`() {
        calculateCryptoBalanceMap(
            CryptoCurrency.ETHER,
            { mapOf("A" to 123L) }.toBalanceQuery(),
            xpubs = setOf("A"),
            legacy = emptySet()
        ).apply {
            totalSpendable `should equal` CryptoValue(CryptoCurrency.ETHER, 123L.toBigInteger())
            totalSpendableLegacy `should equal` CryptoValue.ZeroEth
        }
    }

    @Test
    fun `XPub appears in total balance`() {
        calculateCryptoBalanceMap(
            CryptoCurrency.BTC,
            { mapOf("A" to 123L) }.toBalanceQuery(),
            xpubs = setOf("A"),
            legacy = emptySet()
        ).apply {
            totalSpendable `should equal` 123.satoshi()
            totalSpendableLegacy `should equal` CryptoValue.ZeroBtc
        }
    }

    @Test
    fun `two XPubs appear summed in total balance`() {
        calculateCryptoBalanceMap(
            CryptoCurrency.BTC,
            { mapOf("A" to 123L, "B" to 456L) }.toBalanceQuery(),
            xpubs = setOf("A", "B"),
            legacy = emptySet()
        ).apply {
            totalSpendable `should equal` 579.satoshi()
            totalSpendableLegacy `should equal` CryptoValue.ZeroBtc
        }
    }

    @Test
    fun `spendable legacy appears in spendable total and total`() {
        calculateCryptoBalanceMap(
            CryptoCurrency.BTC,
            { mapOf("A" to 123L) }.toBalanceQuery(),
            xpubs = emptySet(),
            legacy = setOf("A")
        ).apply {
            totalSpendable `should equal` 123L.satoshi()
            totalSpendableLegacy `should equal` 123L.satoshi()
        }
    }

    @Test
    fun `watch only legacy appears in watch only total but not total`() {
        calculateCryptoBalanceMap(
            CryptoCurrency.BTC,
            { mapOf("A" to 123L) }.toBalanceQuery(),
            xpubs = emptySet(),
            legacy = emptySet()
        ).apply {
            totalSpendable `should equal` CryptoValue.ZeroBtc
            totalSpendableLegacy `should equal` CryptoValue.ZeroBtc
        }
    }

    @Test
    fun `if address appears in watch only, it is not in either spendable total`() {
        calculateCryptoBalanceMap(
            CryptoCurrency.BTC,
            { mapOf("A" to 10L, "B" to 20L, "C" to 30L) }.toBalanceQuery(),
            xpubs = setOf("A", "B"),
            legacy = setOf("A", "C")
        ).apply {
            totalSpendable `should equal` 60.satoshi()
            totalSpendableLegacy `should equal` 40.satoshi()
        }
    }

    @Test
    fun `all addresses are queried`() {
        val getBalances: BalanceQuery = mock {
            on { getBalancesFor(any()) } `it returns` emptyMap()
        }
        calculateCryptoBalanceMap(
            CryptoCurrency.BTC,
            getBalances,
            xpubs = setOf("A", "B"),
            legacy = setOf("C", "D")
        ).apply {
            totalSpendable `should equal` CryptoValue.ZeroBtc
            totalSpendableLegacy `should equal` CryptoValue.ZeroBtc
        }
        verify(getBalances).getBalancesFor(setOf("A", "B", "C", "D"))
    }

    @Test
    fun `can look up individual balances`() {
        calculateCryptoBalanceMap(
            CryptoCurrency.BCH,
            { mapOf("A" to 100L, "B" to 200L, "Not listed" to 400L) }.toBalanceQuery(),
            xpubs = setOf("A"),
            legacy = setOf("B")
        ).apply {
            get("A") `should equal` 100.satoshiCash()
            get("B") `should equal` 200.satoshiCash()
            get("Not listed") `should equal` 400.satoshiCash()
            get("Missing") `should equal` CryptoValue.ZeroBch
        }
    }

    @Test
    fun `can adjust an xpub balance`() {
        calculateCryptoBalanceMap(
            CryptoCurrency.BTC,
            { mapOf("A" to 100L, "B" to 200L) }.toBalanceQuery(),
            xpubs = setOf("A"),
            legacy = setOf("B")
        ).apply {
            totalSpendable `should equal` 300L.satoshi()
            totalSpendableLegacy `should equal` 200L.satoshi()
        }.run {
            subtractAmountFromAddress("A", 30L.satoshi())
        }.apply {
            totalSpendable `should equal` 270L.satoshi()
            totalSpendableLegacy `should equal` 200L.satoshi()
            get("A") `should equal` 70L.satoshi()
            get("B") `should equal` 200L.satoshi()
        }
    }

    @Test
    fun `can adjust a legacy balance`() {
        calculateCryptoBalanceMap(
            CryptoCurrency.BCH,
            { mapOf("A" to 100L, "B" to 200L) }.toBalanceQuery(),
            xpubs = setOf("A"),
            legacy = setOf("B")
        ).apply {
            totalSpendable `should equal` 300.satoshiCash()
            totalSpendableLegacy `should equal` 200.satoshiCash()
        }.run {
            subtractAmountFromAddress("B", 50.satoshi())
        }.apply {
            totalSpendable `should equal` 250.satoshiCash()
            totalSpendableLegacy `should equal` 150.satoshiCash()
            get("A") `should equal` 100.satoshiCash()
            get("B") `should equal` 150.satoshiCash()
        }
    }

    @Test
    fun `can adjust a watch only balance`() {
        calculateCryptoBalanceMap(
            CryptoCurrency.BCH,
            { mapOf("A" to 100L, "B" to 200L) }.toBalanceQuery(),
            xpubs = setOf("A"),
            legacy = setOf("B")
        ).apply {
            totalSpendable `should equal` 300.satoshiCash()
            totalSpendableLegacy `should equal` 200.satoshiCash()
        }.apply {
            totalSpendable `should equal` 300.satoshiCash()
            totalSpendableLegacy `should equal` 200.satoshiCash()
            get("A") `should equal` 100.satoshiCash()
            get("B") `should equal` 200.satoshiCash()
        }
    }

    @Test
    fun `can't adjust a missing balance`() {
        calculateCryptoBalanceMap(
            CryptoCurrency.BCH,
            { mapOf("A" to 100L, "B" to 200L) }.toBalanceQuery(),
            xpubs = setOf("A"),
            legacy = setOf("B")
        ).apply {
            {
                subtractAmountFromAddress("Missing", 500L.satoshi())
            } `should throw the Exception` Exception::class `with message`
                "No info for this address. updateAllBalances should be called first."
        }
    }
}

private fun (() -> Map<String, Long>).toBalanceQuery() =
    object : BalanceQuery {
        override fun getBalancesFor(addressesAndXpubs: Set<String>): Map<String, BigInteger> {
            return this@toBalanceQuery().toBigIntegerMap()
        }
    }

private fun <K> Map<K, Long>.toBigIntegerMap() =
    map { (k, v) -> k to v.toBigInteger() }.toMap()
