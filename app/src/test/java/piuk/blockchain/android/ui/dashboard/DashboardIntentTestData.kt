package piuk.blockchain.android.ui.dashboard

import info.blockchain.balance.CryptoCurrency
import info.blockchain.balance.CryptoValue
import info.blockchain.balance.ExchangeRate
import org.amshove.kluent.mock
import piuk.blockchain.android.ui.dashboard.announcements.DismissRule
import piuk.blockchain.android.ui.dashboard.announcements.StandardAnnouncementCard

const val FIAT_CURRENCY = "USD"

val TEST_ASSETS = listOf(
    CryptoCurrency.BTC,
    CryptoCurrency.ETHER,
    CryptoCurrency.XLM
)

val initialBtcState = CryptoAssetState(
    currency = CryptoCurrency.BTC,
    balance = CryptoValue.zero(CryptoCurrency.BTC),
    price = ExchangeRate.CryptoToFiat(CryptoCurrency.BTC, FIAT_CURRENCY, 300.toBigDecimal()),
    price24h = ExchangeRate.CryptoToFiat(CryptoCurrency.BTC, FIAT_CURRENCY, 400.toBigDecimal()),
    priceTrend = emptyList()
)

val initialEthState = CryptoAssetState(
    currency = CryptoCurrency.ETHER,
    balance = CryptoValue.zero(CryptoCurrency.ETHER),
    price = ExchangeRate.CryptoToFiat(CryptoCurrency.ETHER, FIAT_CURRENCY, 200.toBigDecimal()),
    price24h = ExchangeRate.CryptoToFiat(CryptoCurrency.ETHER, FIAT_CURRENCY, 100.toBigDecimal()),
    priceTrend = emptyList()
)

val initialXlmState = CryptoAssetState(
    currency = CryptoCurrency.XLM,
    balance = CryptoValue.zero(CryptoCurrency.XLM),
    price = ExchangeRate.CryptoToFiat(CryptoCurrency.XLM, FIAT_CURRENCY, 100.toBigDecimal()),
    price24h = ExchangeRate.CryptoToFiat(CryptoCurrency.XLM, FIAT_CURRENCY, 75.toBigDecimal()),
    priceTrend = emptyList()
)

val testAnnouncementCard_1 = StandardAnnouncementCard(
    name = "test_1",
    dismissRule = DismissRule.CardOneTime,
    dismissEntry = mock()
)

val testAnnouncementCard_2 = StandardAnnouncementCard(
    name = "test_2",
    dismissRule = DismissRule.CardOneTime,
    dismissEntry = mock()
)

val initialState = DashboardState(
    assets = mapOfAssets(
            CryptoCurrency.BTC to initialBtcState,
            CryptoCurrency.ETHER to initialEthState,
            CryptoCurrency.XLM to initialXlmState
        ),
    announcement = null
)
