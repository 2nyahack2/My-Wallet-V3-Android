package piuk.blockchain.android.simplebuy

import com.blockchain.nabu.datamanagers.BuySellLimits
import com.blockchain.nabu.datamanagers.BuySellPair
import info.blockchain.balance.CryptoCurrency
import info.blockchain.balance.FiatValue
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SimpleBuyStateTest {

    @Test
    fun `amount is valid when entered amount is number and between limits`() {
        val state = SimpleBuyState(supportedPairsAndLimits = listOf(
            BuySellPair("BTC-USD", BuySellLimits(0, 10000),
                sellLimits = BuySellLimits(0, 10000))
        ),
            amount = FiatValue.fromMajor("USD", 99.32.toBigDecimal()),
            fiatCurrency = "USD",
            selectedCryptoCurrency = CryptoCurrency.BTC)
        assertTrue(state.isAmountValid)
    }

    @Test
    fun `amount is not valid when entered amount is not between limits`() {
        val state = SimpleBuyState(supportedPairsAndLimits = listOf(
            BuySellPair("BTC-USD", BuySellLimits(0, 1000),
                sellLimits = BuySellLimits(0, 10000))
        ),
            amount = FiatValue.fromMajor("USD", 101.toBigDecimal()),
            fiatCurrency = "USD",
            selectedCryptoCurrency = CryptoCurrency.BTC)
        assertFalse(state.isAmountValid)
    }

    @Test
    fun `amount is not valid when entered amount is null`() {
        val state = SimpleBuyState(supportedPairsAndLimits = listOf(
            BuySellPair("BTC-USD", BuySellLimits(0, 1000),
                sellLimits = BuySellLimits(0, 10000))
        ), amount = null, fiatCurrency = "USD", selectedCryptoCurrency = CryptoCurrency.BTC)
        assertFalse(state.isAmountValid)
    }
}