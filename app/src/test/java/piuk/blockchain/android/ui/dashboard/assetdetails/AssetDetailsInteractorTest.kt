package piuk.blockchain.android.ui.dashboard.assetdetails

import com.blockchain.remoteconfig.FeatureFlag
import com.blockchain.testutils.rxInit
import com.nhaarman.mockito_kotlin.whenever
import info.blockchain.balance.CryptoCurrency
import info.blockchain.balance.CryptoValue
import info.blockchain.balance.ExchangeRate
import info.blockchain.balance.FiatValue
import info.blockchain.wallet.prices.TimeInterval
import info.blockchain.wallet.prices.data.PriceDatum
import io.reactivex.Maybe
import io.reactivex.Single
import junit.framework.Assert.assertEquals
import org.amshove.kluent.mock
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import piuk.blockchain.android.coincore.AccountGroup
import piuk.blockchain.android.coincore.AssetFilter
import piuk.blockchain.android.coincore.CryptoAsset
import piuk.blockchain.androidcore.data.exchangerate.TimeSpan
import java.util.Locale

class AssetDetailsInteractorTest {

    @get:Rule
    val rx = rxInit {
        ioTrampoline()
        computationTrampoline()
    }

    private lateinit var interactor: AssetDetailsInteractor

    private val totalGroup: AccountGroup = mock()
    private val nonCustodialGroup: AccountGroup = mock()
    private val custodialGroup: AccountGroup = mock()
    private val interestGroup: AccountGroup = mock()
    private val interestRate: Double = 5.0
    private val interestEnabled: Boolean = true
    private val asset: CryptoAsset = mock()
    private val featureFlagMock: FeatureFlag = mock()

    @Before
    fun setUp() {
        interactor = AssetDetailsInteractor(featureFlagMock, mock(), mock())

        whenever(asset.accountGroup(AssetFilter.All)).thenReturn(Maybe.just(totalGroup))
        whenever(asset.accountGroup(AssetFilter.NonCustodial)).thenReturn(Maybe.just(nonCustodialGroup))
        whenever(asset.accountGroup(AssetFilter.Custodial)).thenReturn(Maybe.just(custodialGroup))
        whenever(asset.accountGroup(AssetFilter.Interest)).thenReturn(Maybe.just(interestGroup))
        whenever(featureFlagMock.enabled).thenReturn(Single.just(interestEnabled))

        Locale.setDefault(Locale.US)
    }

    @Test
    fun `cryptoBalance,fiatBalance & interestBalance return the right values`() {

        val price = ExchangeRate.CryptoToFiat(CryptoCurrency.BTC, "USD", 56478.99.toBigDecimal())

        val walletCrypto = CryptoValue(CryptoCurrency.BTC, 548621.toBigInteger())
        val custodialCrypto = CryptoValue.ZeroBtc
        val interestCrypto = CryptoValue.ZeroBtc
        val pendingCrypto = CryptoValue.ZeroBtc

        val walletFiat = FiatValue.fromMinor("USD", 30985)
        val custodialFiat = FiatValue.fromMinor("USD", 0)
        val interestFiat = FiatValue.fromMinor("USD", 0)

        val expectedResult = mapOf(
            AssetFilter.NonCustodial to AssetDisplayInfo(nonCustodialGroup,
                walletCrypto,
                pendingCrypto,
                walletFiat,
                emptySet()),
            AssetFilter.Custodial to AssetDisplayInfo(
                custodialGroup,
                custodialCrypto,
                pendingCrypto,
                custodialFiat,
                emptySet()),
            AssetFilter.Interest to AssetDisplayInfo(
                interestGroup, interestCrypto, pendingCrypto, interestFiat, emptySet(), interestRate
            )
        )

        whenever(asset.exchangeRate()).thenReturn(Single.just(price))

        whenever(nonCustodialGroup.accountBalance).thenReturn(Single.just(walletCrypto))
        whenever(nonCustodialGroup.pendingBalance).thenReturn(Single.just(pendingCrypto))
        whenever(nonCustodialGroup.isEnabled).thenReturn(Single.just(true))
        whenever(nonCustodialGroup.actions).thenReturn(Single.just(emptySet()))
        whenever(custodialGroup.accountBalance).thenReturn(Single.just(custodialCrypto))
        whenever(custodialGroup.pendingBalance).thenReturn(Single.just(pendingCrypto))
        whenever(custodialGroup.actions).thenReturn(Single.just(emptySet()))
        whenever(custodialGroup.isEnabled).thenReturn(Single.just(true))
        whenever(interestGroup.accountBalance).thenReturn(Single.just(interestCrypto))
        whenever(interestGroup.pendingBalance).thenReturn(Single.just(pendingCrypto))
        whenever(interestGroup.isEnabled).thenReturn(Single.just(true))
        whenever(interestGroup.actions).thenReturn(Single.just(emptySet()))
        whenever(asset.interestRate()).thenReturn(Single.just(interestRate))

        whenever(custodialGroup.accounts).thenReturn(listOf(mock()))
        whenever(nonCustodialGroup.accounts).thenReturn(listOf(mock()))
        whenever(custodialGroup.isFunded).thenReturn(true)
        whenever(nonCustodialGroup.isFunded).thenReturn(true)

        whenever(interestGroup.accounts).thenReturn(listOf(mock()))
        whenever(interestGroup.isFunded).thenReturn(true)

        val v = interactor.loadAssetDetails(asset)
            .test()
            .values()

        // Using assertResult(expectedResult) instead of fetching the values and checking them results in
        // an 'AssertionException Not completed' result. I have no clue why; changing the matchers to not use all
        // three possible enum values changes the failure into an expected 'Failed, not equal' result (hence the
        // doAnswer() nonsense instead of eq() etc - I tried many things)
        // All very strange.
        assertEquals(expectedResult, v[0])
    }

    @Test
    fun `cryptoBalance, fiatBalance & interestBalance are never returned if exchange rate fails`() {
        whenever(asset.exchangeRate()).thenReturn(Single.error(Throwable()))

        val walletCrypto = CryptoValue(CryptoCurrency.BTC, 548621.toBigInteger())
        val custodialCrypto = CryptoValue.ZeroBtc
        val interestCrypto = CryptoValue.ZeroBtc

        whenever(nonCustodialGroup.accountBalance).thenReturn(Single.just(walletCrypto))
        whenever(custodialGroup.accountBalance).thenReturn(Single.just(custodialCrypto))
        whenever(interestGroup.accountBalance).thenReturn(Single.just(interestCrypto))
        whenever(asset.interestRate()).thenReturn(Single.just(interestRate))

        val testObserver = interactor.loadAssetDetails(asset)
            .test()

        testObserver.assertNoValues()
    }

    @Test
    fun `cryptoBalance & fiatBalance never return if interest fails`() {
        val walletCrypto = CryptoValue(CryptoCurrency.BTC, 548621.toBigInteger())
        val custodialCrypto = CryptoValue.ZeroBtc

        val price = ExchangeRate.CryptoToFiat(CryptoCurrency.BTC, "USD", 5647899.toBigDecimal())

        whenever(asset.exchangeRate()).thenReturn(Single.just(price))
        whenever(asset.accountGroup(AssetFilter.Interest)).thenReturn(Maybe.error(Throwable()))

        whenever(nonCustodialGroup.accountBalance).thenReturn(Single.just(walletCrypto))
        whenever(custodialGroup.accountBalance).thenReturn(Single.just(custodialCrypto))
        whenever(asset.interestRate()).thenReturn(Single.just(interestRate))

        val testObserver = interactor.loadAssetDetails(asset)
            .test()
        testObserver.assertNoValues()
    }

    @Test
    fun `exchange rate is the right one`() {
        val price = ExchangeRate.CryptoToFiat(CryptoCurrency.BTC, "USD", 56478.99.toBigDecimal())

        whenever(asset.exchangeRate()).thenReturn(Single.just(price))

        val testObserver = interactor.loadExchangeRate(asset).test()
        testObserver.assertValue("$56,478.99").assertValueCount(1)
    }

    @Test
    fun `historic prices are returned properly`() {
        whenever(asset.historicRateSeries(TimeSpan.DAY, TimeInterval.FIFTEEN_MINUTES))
            .thenReturn(Single.just(listOf(
                PriceDatum(5556, 2.toDouble(), volume24h = 0.toDouble()),
                PriceDatum(587, 22.toDouble(), volume24h = 0.toDouble()),
                PriceDatum(6981, 23.toDouble(), volume24h = 4.toDouble())
            )))

        val testObserver = interactor.loadHistoricPrices(asset, TimeSpan.DAY).test()

        testObserver.assertValue(listOf(
            PriceDatum(5556, 2.toDouble(), volume24h = 0.toDouble()),
            PriceDatum(587, 22.toDouble(), volume24h = 0.toDouble()),
            PriceDatum(6981, 23.toDouble(), volume24h = 4.toDouble())
        )).assertValueCount(1)
    }

    @Test
    fun `when historic prices api returns error, empty list should be returned`() {
        whenever(asset.historicRateSeries(TimeSpan.DAY, TimeInterval.FIFTEEN_MINUTES))
            .thenReturn(Single.error(Throwable()))

        val testObserver = interactor.loadHistoricPrices(asset, TimeSpan.DAY).test()

        testObserver.assertValue(emptyList()).assertValueCount(1)
    }
}