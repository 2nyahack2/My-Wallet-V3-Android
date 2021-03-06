package piuk.blockchain.android.ui.dashboard

import info.blockchain.balance.CryptoCurrency
import info.blockchain.balance.FiatValue
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DashboardStateTest {

    @Test
    fun `if assets are zero, balance is zero`() {
        val subject = DashboardState(
            assets = mapOfAssets(
                CryptoCurrency.BTC to initialBtcState,
                CryptoCurrency.ETHER to initialEthState,
                CryptoCurrency.XLM to initialXlmState
            ),
            announcement = null
        )

        assertEquals(subject.fiatBalance, FiatValue.zero(FIAT_CURRENCY))
    }

    @Test
    fun `if only one asset loaded, and is zero, then total is zero`() {
        val subject = DashboardState(
            assets = mapOfAssets(
                CryptoCurrency.BTC to initialBtcState,
                CryptoCurrency.ETHER to CryptoAssetState(CryptoCurrency.ETHER),
                CryptoCurrency.XLM to CryptoAssetState(CryptoCurrency.XLM)
            ),
            announcement = null
        )

        assertEquals(subject.fiatBalance, FiatValue.zero(FIAT_CURRENCY))
    }

    @Test
    fun `if no assets are loaded, total balance is null`() {
        val subject = DashboardState(
            assets = mapOfAssets(
                CryptoCurrency.BTC to CryptoAssetState(CryptoCurrency.BTC),
                CryptoCurrency.ETHER to CryptoAssetState(CryptoCurrency.ETHER),
                CryptoCurrency.XLM to CryptoAssetState(CryptoCurrency.XLM)
            ),
            announcement = null
        )

        assertNull(subject.fiatBalance)
    }
}
