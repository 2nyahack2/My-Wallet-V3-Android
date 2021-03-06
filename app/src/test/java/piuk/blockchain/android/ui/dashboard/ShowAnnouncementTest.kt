package piuk.blockchain.android.ui.dashboard

import info.blockchain.balance.CryptoCurrency
import org.junit.Test
import kotlin.test.assertEquals

class ShowAnnouncementTest {

    val subject = ShowAnnouncement(testAnnouncementCard_1)

    @Test
    fun `showing an announcement, sets announcement and leaves other fields unchanged`() {

        val initialState = DashboardState(
            assets = mapOfAssets(
                CryptoCurrency.BTC to initialBtcState,
                CryptoCurrency.ETHER to initialEthState,
                CryptoCurrency.XLM to initialXlmState
            ),
            announcement = null
        )

        val result = subject.reduce(initialState)

        assertEquals(result.assets, initialState.assets)
        assertEquals(result.announcement, testAnnouncementCard_1)
    }

    @Test
    fun `replacing an announcement, sets announcement and leaves other fields unchanged`() {

        val initialState = DashboardState(
            assets = mapOfAssets(
                CryptoCurrency.BTC to initialBtcState,
                CryptoCurrency.ETHER to initialEthState,
                CryptoCurrency.XLM to initialXlmState
            ),
            announcement = testAnnouncementCard_2
        )

        val result = subject.reduce(initialState)

        assertEquals(result.assets, initialState.assets)
        assertEquals(result.announcement, testAnnouncementCard_1)
    }
}