package info.blockchain.balance

import org.amshove.kluent.`should be`
import org.amshove.kluent.`should equal`
import org.junit.Test
import java.math.BigDecimal
import java.math.BigInteger

class CryptoValueTests {

    @Test
    fun `zero btc`() {
        CryptoValue.zero(CryptoCurrency.BTC) `should equal` CryptoValue(CryptoCurrency.BTC, BigInteger.ZERO)
    }

    @Test
    fun `zero bch`() {
        CryptoValue.zero(CryptoCurrency.BCH) `should equal` CryptoValue(CryptoCurrency.BCH, BigInteger.ZERO)
    }

    @Test
    fun `zero bch function`() {
        CryptoValue.zero(CryptoCurrency.BCH) `should equal` CryptoValue.ZeroBch
    }

    @Test
    fun `zero eth`() {
        CryptoValue.zero(CryptoCurrency.ETHER) `should equal` CryptoValue(CryptoCurrency.ETHER, BigInteger.ZERO)
    }

    @Test
    fun `zero pax function`() {
        CryptoValue.zero(CryptoCurrency.PAX) `should equal` CryptoValue(CryptoCurrency.PAX, BigInteger.ZERO)
    }

    @Test
    fun `toBigDecimal BTC`() {
        CryptoValue.fromMinor(
            CryptoCurrency.BTC, 12345678901.toBigInteger()
        ).toBigDecimal() `should equal` BigDecimal("123.45678901")
    }

    @Test
    fun `toBigDecimal BCH`() {
        CryptoValue.fromMinor(
            CryptoCurrency.BCH,
            234.toBigInteger()
        ).toBigDecimal() `should equal` BigDecimal("0.00000234")
    }

    @Test
    fun `toBigDecimal ETH`() {
        CryptoValue(
            CryptoCurrency.ETHER,
            234L.toBigInteger()
        ).toBigDecimal() `should equal` BigDecimal("0.000000000000000234")
    }

    @Test
    fun `toBigDecimal keeps all trailing 0s`() {
        CryptoValue(
            CryptoCurrency.BTC,
            10000000000L.toBigInteger()
        ).toBigDecimal() `should equal` BigDecimal("100.00000000")
    }

    @Test
    fun `toMajorUnit Double`() {
        CryptoValue(CryptoCurrency.BTC, 12300001234L.toBigInteger()).toMajorUnitDouble() `should equal` 123.00001234
    }

    @Test
    fun `zero is not positive`() {
        CryptoValue.zero(CryptoCurrency.BTC).isPositive `should be` false
    }

    @Test
    fun `1 Satoshi is positive`() {
        CryptoValue.fromMinor(CryptoCurrency.BTC, 1.toBigInteger()).isPositive `should be` true
    }

    @Test
    fun `2 Satoshis is positive`() {
        CryptoValue.fromMinor(CryptoCurrency.BTC, 2.toBigInteger()).isPositive `should be` true
    }

    @Test
    fun `-1 Satoshi is not positive`() {
        CryptoValue.fromMinor(CryptoCurrency.BTC, (-1).toBigInteger()).isPositive `should be` false
    }

    @Test
    fun `zero isZero`() {
        CryptoValue.zero(CryptoCurrency.BTC).isZero `should be` true
    }

    @Test
    fun `1 satoshi is not isZero`() {
        CryptoValue.fromMinor(CryptoCurrency.BTC, 1.toBigInteger()).isZero `should be` false
    }

    @Test
    fun `1 wei is not isZero`() {
        CryptoValue.fromMinor(CryptoCurrency.ETHER, BigInteger.ONE).isZero `should be` false
    }

    @Test
    fun `0 wei is isZero`() {
        CryptoValue.fromMinor(CryptoCurrency.ETHER, BigInteger.ZERO).isZero `should be` true
    }

    @Test
    fun `amount is the minor part of the currency`() {
        CryptoValue(CryptoCurrency.BTC, 1234.toBigInteger()).toBigInteger() `should equal` 1234L.toBigInteger()
    }

    @Test
    fun `amount is the total minor part of the currency`() {
        CryptoValue.fromMajor(CryptoCurrency.ETHER, 2L.toBigDecimal())
            .toBigInteger() `should equal` 2e18.toBigDecimal().toBigIntegerExact()
    }

    @Test
    fun `amount when created from satoshis`() {
        CryptoValue.fromMinor(CryptoCurrency.BTC, 4567L.toBigInteger()).apply {
            currency `should equal` CryptoCurrency.BTC
            toBigInteger() `should equal` 4567.toBigInteger()
        }
    }

    @Test
    fun `amount when created from satoshis big integer`() {
        CryptoValue.fromMinor(CryptoCurrency.BTC, 4567.toBigInteger()).apply {
            currency `should equal` CryptoCurrency.BTC
            toBigInteger() `should equal` 4567.toBigInteger()
        }
    }

    @Test
    fun `amount of Cash when created from satoshis`() {
        CryptoValue.fromMinor(CryptoCurrency.BCH, 45678.toBigInteger()).apply {
            currency `should equal` CryptoCurrency.BCH
            toBigInteger() `should equal` 45678.toBigInteger()
        }
    }
}
