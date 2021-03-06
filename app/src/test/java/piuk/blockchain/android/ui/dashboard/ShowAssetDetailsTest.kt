package piuk.blockchain.android.ui.dashboard

import com.nhaarman.mockito_kotlin.mock
import info.blockchain.balance.CryptoCurrency
import org.junit.Test
import piuk.blockchain.android.ui.dashboard.assetdetails.AssetDetailsFlow
import kotlin.test.assertEquals

class ShowAssetDetailsTest {

    private val flow = AssetDetailsFlow(CryptoCurrency.ETHER, mock())
    private val subject = UpdateLaunchDialogFlow(flow)

    @Test
    fun `showing asset details, sets asset type and leaves other fields unchanged`() {

        val initialState = DashboardState(
            assets = mapOfAssets(
                CryptoCurrency.BTC to initialBtcState,
                CryptoCurrency.ETHER to initialEthState,
                CryptoCurrency.XLM to initialXlmState
            ),
            activeFlow = null,
            announcement = testAnnouncementCard_1
        )

        val result = subject.reduce(initialState)

        assertEquals(result.assets, initialState.assets)
        assertEquals(result.activeFlow, flow)
        assertEquals(result.announcement, testAnnouncementCard_1)
    }

    @Test
    fun `replacing asset details type, sets asset and leaves other fields unchanged`() {

        val initialState = DashboardState(
            assets = mapOfAssets(
                CryptoCurrency.BTC to initialBtcState,
                CryptoCurrency.ETHER to initialEthState,
                CryptoCurrency.XLM to initialXlmState
            ),
            activeFlow = null,
            announcement = testAnnouncementCard_1
        )

        val result = subject.reduce(initialState)

        assertEquals(result.assets, initialState.assets)
        assertEquals(result.activeFlow, flow)
        assertEquals(result.announcement, testAnnouncementCard_1)
    }

    @Test
    fun `replacing an asset details type with the same type has no effect`() {
        val initialState = DashboardState(
            assets = mapOfAssets(
                CryptoCurrency.BTC to initialBtcState,
                CryptoCurrency.ETHER to initialEthState,
                CryptoCurrency.XLM to initialXlmState
            ),
            activeFlow = flow,
            announcement = testAnnouncementCard_1
        )

        val result = subject.reduce(initialState)

        assertEquals(result, initialState)
    }
}