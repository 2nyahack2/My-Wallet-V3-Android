package piuk.blockchain.android.ui.swipetoreceive

import android.graphics.Bitmap
import com.blockchain.android.testutils.rxInit
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import info.blockchain.balance.CryptoCurrency
import io.reactivex.Maybe
import io.reactivex.Single
import org.amshove.kluent.itReturns
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import piuk.blockchain.android.coincore.CachedAddress
import piuk.blockchain.android.coincore.OfflineCachedAccount
import piuk.blockchain.android.coincore.impl.OfflineBalanceCall
import piuk.blockchain.android.scan.QrCodeDataManager
import piuk.blockchain.android.ui.swipetoreceive.SwipeToReceivePresenter.Companion.DIMENSION_QR_CODE
import piuk.blockchain.androidcoreui.ui.base.UiState

class SwipeToReceivePresenterTest {

    @get:Rule
    val initSchedulers = rxInit {
        mainTrampoline()
        ioTrampoline()
        computationTrampoline()
    }

    private val view: SwipeToReceiveView = mock()
    private val qrGenerator: QrCodeDataManager = mock()
    private val offlineCache: LocalOfflineAccountCache = mock()
    private val offlineBalance: OfflineBalanceCall = mock()
    private val subject = SwipeToReceivePresenter(qrGenerator, offlineCache, offlineBalance)

    @Before
    fun setUp() {
        subject.initView(view)
    }

    @Test
    fun `onViewReady - nothing in cache`() {
        // Arrange
        whenever(offlineCache.availableAssets()).thenReturn(Single.just(emptyList()))

        // Act
        subject.onViewReady()

        // Assert
        verify(view).initPager(emptyList())
        verifyNoMoreInteractions(view)
    }

    @Test
    fun `onViewReady - cache loaded`() {
        // Arrange
        val availableAssets = listOf(CryptoCurrency.BTC, CryptoCurrency.PAX)
        val availableTicker = availableAssets.map { it.networkTicker }
        whenever(offlineCache.availableAssets())
            .thenReturn(
                Single.just(availableTicker)
            )

        // Act
        subject.onViewReady()

        // Assert
        verify(view).initPager(availableAssets)
        verifyNoMoreInteractions(view)
    }

    @Test
    fun `onCurrencySelected - asset in cache`() {
        // Arrange
        val cachedAddress: CachedAddress = mock {
            on { address } itReturns (TEST_ADDRESS)
            on { addressUri } itReturns (TEST_ADDRESS_URI)
        }

        val cacheItem: OfflineCachedAccount = mock {
            on { networkTicker } itReturns (TEST_ASSET_TICKER)
            on { accountLabel } itReturns (TEST_ACCOUNT_LABEL)
            on { nextAddress(offlineBalance) } itReturns (Maybe.just(cachedAddress))
        }

        val mockBitmap: Bitmap = mock()

        whenever(offlineCache.getCacheForAsset(TEST_ASSET_TICKER))
            .thenReturn(cacheItem)

        whenever(qrGenerator.generateQrCode(TEST_ADDRESS_URI, DIMENSION_QR_CODE))
            .thenReturn(Single.just(mockBitmap))

        // Act
        subject.onCurrencySelected(CryptoCurrency.PAX)

        // Assert
        verify(view).displayAsset(TEST_ASSET)
        verify(view).setUiState(UiState.LOADING)
        verify(view).displayReceiveAccount(TEST_ACCOUNT_LABEL)
        verify(view).displayReceiveAddress(TEST_ADDRESS)
        verify(view).displayQrCode(mockBitmap)
        verify(view).setUiState(UiState.CONTENT)

        verify(offlineCache).getCacheForAsset(TEST_ASSET_TICKER)
        verify(qrGenerator).generateQrCode(TEST_ADDRESS_URI, DIMENSION_QR_CODE)

        verifyNoMoreInteractions(view)
        verifyNoMoreInteractions(qrGenerator)
        verifyNoMoreInteractions(offlineCache)
    }

    @Test
    fun `onCurrencySelected - asset NOT in cache`() {
        // Arrange
        whenever(offlineCache.getCacheForAsset(TEST_ASSET_TICKER))
            .thenReturn(null)

        // Act
        subject.onCurrencySelected(CryptoCurrency.PAX)

        // Assert
        verify(view).displayAsset(TEST_ASSET)
        verify(view).setUiState(UiState.LOADING)
        verify(view).setUiState(UiState.EMPTY)

        verify(offlineCache).getCacheForAsset(TEST_ASSET_TICKER)

        verifyNoMoreInteractions(view)
        verifyNoMoreInteractions(qrGenerator)
        verifyNoMoreInteractions(offlineCache)
    }

    @Test
    fun `onCurrencySelected - QR Generation failed`() {
        // Arrange
        val cachedAddress: CachedAddress = mock {
            on { address } itReturns (TEST_ADDRESS)
            on { addressUri } itReturns (TEST_ADDRESS_URI)
        }

        val cacheItem: OfflineCachedAccount = mock {
            on { networkTicker } itReturns (TEST_ASSET_TICKER)
            on { accountLabel } itReturns (TEST_ACCOUNT_LABEL)
            on { nextAddress(offlineBalance) } itReturns (Maybe.just(cachedAddress))
        }

        whenever(offlineCache.getCacheForAsset(TEST_ASSET_TICKER))
            .thenReturn(cacheItem)

        whenever(qrGenerator.generateQrCode(TEST_ADDRESS_URI, DIMENSION_QR_CODE))
            .thenReturn(Single.error<Bitmap>(Throwable()))

        // Act
        subject.onCurrencySelected(CryptoCurrency.PAX)

        // Assert
        verify(view).displayAsset(TEST_ASSET)
        verify(view).setUiState(UiState.LOADING)
        verify(view).displayReceiveAccount(TEST_ACCOUNT_LABEL)
        verify(view).displayReceiveAddress(TEST_ADDRESS)
        verify(view).setUiState(UiState.EMPTY)

        verify(offlineCache).getCacheForAsset(TEST_ASSET_TICKER)
        verify(qrGenerator).generateQrCode(TEST_ADDRESS_URI, DIMENSION_QR_CODE)

        verifyNoMoreInteractions(view)
        verifyNoMoreInteractions(qrGenerator)
        verifyNoMoreInteractions(offlineCache)
    }

    companion object {
        private val TEST_ASSET = CryptoCurrency.PAX
        private const val TEST_ASSET_TICKER = "PAX"
        private const val TEST_ACCOUNT_LABEL = "A Pax Account"
        private const val TEST_ADDRESS = "ThisIsAPaxAddress"
        private const val TEST_ADDRESS_URI = "ThisIsAPaxAddressUri"
    }
}