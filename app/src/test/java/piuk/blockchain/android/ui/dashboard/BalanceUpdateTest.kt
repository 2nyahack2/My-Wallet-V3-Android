package piuk.blockchain.android.ui.dashboard

import com.blockchain.testutils.bitcoin
import com.blockchain.testutils.bitcoinCash
import com.blockchain.testutils.ether
import info.blockchain.balance.CryptoCurrency
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals

class BalanceUpdateTest {

    @Test(expected = IllegalStateException::class)
    fun `Updating a mismatched currency throws an exception`() {

        val initialState = DashboardState(
            assets = mapOfAssets(
                CryptoCurrency.BTC to initialBtcState,
                CryptoCurrency.ETHER to initialEthState,
                CryptoCurrency.XLM to initialXlmState
            ),
            announcement = testAnnouncementCard_1
        )

        val subject = BalanceUpdate(
            CryptoCurrency.BTC,
            1.bitcoinCash()
        )

        subject.reduce(initialState)
    }

    @Test
    fun `update changes effects correct asset`() {
        val initialState = DashboardState(
            assets = mapOfAssets(
                CryptoCurrency.BTC to initialBtcState,
                CryptoCurrency.ETHER to initialEthState,
                CryptoCurrency.XLM to initialXlmState
            ),
            announcement = testAnnouncementCard_1
        )

        val subject = BalanceUpdate(
            CryptoCurrency.BTC,
            1.bitcoin()
        )

        val result = subject.reduce(initialState)

        assertNotEquals(result.assets, initialState.assets)
        assertNotEquals(result[CryptoCurrency.BTC], initialState[CryptoCurrency.BTC])
        assertEquals(result[CryptoCurrency.ETHER], initialState[CryptoCurrency.ETHER])
        assertEquals(result[CryptoCurrency.XLM], initialState[CryptoCurrency.XLM])

        assertEquals(result.announcement, initialState.announcement)
    }

    @Test
    fun `receiving a valid balance update clears any balance errors`() {
        val initialState = DashboardState(
            assets = mapOfAssets(
                CryptoCurrency.BTC to initialBtcState,
                CryptoCurrency.ETHER to initialEthState.copy(hasBalanceError = true),
                CryptoCurrency.XLM to initialXlmState
            ),
            announcement = testAnnouncementCard_1
        )

        val subject = BalanceUpdate(
            CryptoCurrency.ETHER,
            1.ether()
        )

        val result = subject.reduce(initialState)

        assertFalse(result[CryptoCurrency.ETHER].hasBalanceError)

        assertNotEquals(result.assets, initialState.assets)
        assertEquals(result[CryptoCurrency.BTC], initialState[CryptoCurrency.BTC])
        assertEquals(result[CryptoCurrency.XLM], initialState[CryptoCurrency.XLM])

        assertEquals(result.announcement, initialState.announcement)
    }
}
