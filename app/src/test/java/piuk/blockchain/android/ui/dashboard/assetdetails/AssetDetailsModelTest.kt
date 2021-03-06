package piuk.blockchain.android.ui.dashboard.assetdetails

import com.blockchain.android.testutils.rxInit
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import info.blockchain.wallet.prices.data.PriceDatum
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import piuk.blockchain.android.coincore.AssetFilter
import piuk.blockchain.android.coincore.btc.BtcAsset
import piuk.blockchain.androidcore.data.exchangerate.TimeSpan

class AssetDetailsModelTest {
    private lateinit var model: AssetDetailsModel
    private val defaultState = AssetDetailsState(
        asset = BtcAsset(
            payloadManager = mock(),
            sendDataManager = mock(),
            feeDataManager = mock(),
            custodialManager = mock(),
            exchangeRates = mock(),
            historicRates = mock(),
            currencyPrefs = mock(),
            labels = mock(),
            pitLinking = mock(),
            crashLogger = mock(),
            tiersService = mock(),
            environmentConfig = mock(),
            walletPreferences = mock(),
            offlineAccounts = mock(),
            eligibilityProvider = mock(),
            coinsWebsocket = mock()
        )
    )
    private val interactor: AssetDetailsInteractor = mock()

    @get:Rule
    val rx = rxInit {
        ioTrampoline()
        computationTrampoline()
    }

    @Before
    fun setUp() {
        model =
            AssetDetailsModel(defaultState, Schedulers.io(), interactor)
    }

    @Test
    fun `load details success`() {
        val assetDisplayMap = mapOf(
            AssetFilter.Custodial to AssetDisplayInfo(mock(), mock(), mock(), mock(), emptySet()))

        whenever(interactor.loadAssetDetails(any())).thenReturn(Single.just(assetDisplayMap))

        val testObserver = model.state.test()
        model.process(LoadAssetDisplayDetails)
        testObserver.assertValueAt(0, defaultState)
        testObserver.assertValueAt(1, defaultState.copy(assetDisplayMap = assetDisplayMap))
    }

    @Test
    fun `load price success`() {
        val price = "1000 BTC"
        whenever(interactor.loadExchangeRate(any())).thenReturn(Single.just(price))

        val testObserver = model.state.test()
        model.process(LoadAssetFiatValue)
        testObserver.assertValueAt(0, defaultState)
        testObserver.assertValueAt(1, defaultState.copy(assetFiatPrice = price))
    }

    @Test
    fun `historic price success`() {
        val priceSeries = listOf(PriceDatum())
        whenever(interactor.loadHistoricPrices(any(), any())).thenReturn(Single.just(priceSeries))

        val testObserver = model.state.test()
        model.process(LoadHistoricPrices)
        testObserver.assertValueAt(0, defaultState)
        testObserver.assertValueAt(1, defaultState.copy(chartLoading = true))
        testObserver.assertValueAt(2, defaultState.copy(
            chartData = priceSeries,
            chartLoading = false
        ))
    }

    @Test
    fun `changing timestamp updates historic price`() {
        val priceSeries = listOf(PriceDatum())
        val monthTimeSpan = TimeSpan.MONTH

        whenever(interactor.loadHistoricPrices(any(), any())).thenReturn(Single.just(priceSeries))

        val testObserver = model.state.test()
        model.process(UpdateTimeSpan(monthTimeSpan))

        testObserver.assertValueAt(0, defaultState)
        testObserver.assertValueAt(1, defaultState.copy(timeSpan = monthTimeSpan))
        testObserver.assertValueAt(2,
            defaultState.copy(chartLoading = true, timeSpan = monthTimeSpan))
        testObserver.assertValueAt(3, defaultState.copy(
            chartData = priceSeries,
            timeSpan = monthTimeSpan,
            chartLoading = false
        ))
    }
}