package piuk.blockchain.android.thepit

import com.blockchain.android.testutils.rxInit
import com.blockchain.annotations.CommonCode
import com.blockchain.sunriver.XlmDataManager
import com.blockchain.nabu.NabuToken
import com.blockchain.nabu.datamanagers.NabuDataManager
import com.blockchain.nabu.models.responses.nabu.NabuUser
import com.blockchain.nabu.models.responses.tokenresponse.NabuOfflineTokenResponse
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.argumentCaptor
import com.nhaarman.mockito_kotlin.doAnswer
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.times
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.verifyNoMoreInteractions
import com.nhaarman.mockito_kotlin.whenever
import info.blockchain.balance.AccountReference
import info.blockchain.wallet.payload.data.Account
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import junit.framework.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import piuk.blockchain.androidcore.data.bitcoincash.BchDataManager
import piuk.blockchain.androidcore.data.ethereum.EthDataManager
import piuk.blockchain.androidcore.data.payload.PayloadDataManager

@CommonCode("Also exists in nabu/test/TestHelper.kt")
val validOfflineToken
    get() = NabuOfflineTokenResponse("userId", "lifetimeToken")

class PitLinkingImplTest {

    private val nabu: NabuDataManager = mock()
    private val nabuToken: NabuToken = mock()
    private val nabuUser: NabuUser = mock()
    private val payloadDataManager: PayloadDataManager = mock()
    private val ethDataManager: EthDataManager = mock()
    private val bchDataManager: BchDataManager = mock()
    private val xlmDataManager: XlmDataManager = mock()

    private lateinit var subject: PitLinkingImpl

    @get:Rule
    val rxSchedulers = rxInit {
        mainTrampoline()
        computationTrampoline()
        ioTrampoline()
    }

    @Before
    fun setup() {
        whenever(nabuToken.fetchNabuToken()).thenReturn(Single.just(validOfflineToken))

        subject = PitLinkingImpl(
            nabu = nabu,
            nabuToken = nabuToken,
            payloadDataManager = payloadDataManager,
            ethDataManager = ethDataManager,
            bchDataManager = bchDataManager,
            xlmDataManager = xlmDataManager
        )
    }

    @Test
    fun `fetch user data on subscribe, user is linked`() {
        // Arrange
        whenever(nabuUser.exchangeEnabled).thenReturn(true)
        whenever(nabu.getUser(validOfflineToken)).thenReturn(Single.just(nabuUser))

        // Act
        val test = subject.state.test()

        // Assert
        verify(nabu).getUser(validOfflineToken)

        test.assertValue { it.isLinked }
        test.assertNoErrors()
        test.assertNotComplete()
    }

    @Test
    fun `fetch user data on subscribe, user is not linked`() {
        // Arrange
        whenever(nabuUser.userName).thenReturn(null)
        whenever(nabuUser.exchangeEnabled).thenReturn(false)
        whenever(nabu.getUser(validOfflineToken)).thenReturn(Single.just(nabuUser))

        // Act
        val test = subject.state.test()

        // Assert
        verify(nabu).getUser(validOfflineToken)

        test.assertValue { !it.isLinked }
        test.assertNoErrors()
        test.assertNotComplete()
    }

    @Test
    fun `two subscriptions with isPitLinked() helper function`() {
        // Arrange
        whenever(nabuUser.exchangeEnabled).thenReturn(true)
        whenever(nabu.getUser(validOfflineToken)).thenReturn(Single.just(nabuUser))

        // Act
        val test1 = subject.state.test()
        val test2 = subject.isPitLinked().test()

        // Assert
        verify(nabu, times(2)).getUser(validOfflineToken)

        with(test1) {
            assertValueAt(0) { it.isLinked }
            assertValueAt(1) { it.isLinked }
            assertNoErrors()
            assertNotComplete()
        }

        with(test2) {
            assertResult(true)
        }
    }

//    @Test
//    fun `isPitLinked continues to work after a first call fails`() {
//      TODO: Write me
//    }

//    @Test
//    fun `isPitLinked() defaults to false when an error occurs`() {
//      TODO: Write me
//    }

    @Test
    fun `upload pit addresses correctly formats address map`() {
        // Arrange
        btcManagerReturnsGoodAddress()
        bchManagerReturnsGoodAddress()
        ethManagerReturnsGoodAddress()
        xlmManagerReturnsGoodAddress()
        // PAX Addresses are the same as ETH

        whenever(nabu.shareWalletAddressesWithThePit(any(), any())).thenReturn(Completable.complete())

        // Act
        subject.sendWalletAddressToThePit()

        // Assert
        val tokenCapture = argumentCaptor<NabuOfflineTokenResponse>()
        val mapCapture = argumentCaptor<Map<String, String>>()

        verify(nabu).shareWalletAddressesWithThePit(tokenCapture.capture(), mapCapture.capture())

        assertEquals(tokenCapture.firstValue, validOfflineToken)

        val map = mapCapture.firstValue
        assertEquals(map.size, 5)
        assertEquals(map["BTC"], BTC_ACCOUNT_ADDRESS)
        assertEquals(map["BCH"], BCH_ACCOUNT_ADDRESS)
        assertEquals(map["ETH"], ETH_ACCOUNT_ADDRESS)
        assertEquals(map["XLM"], XLM_ACCOUNT_ADDRESS)
        assertEquals(map["PAX"], ETH_ACCOUNT_ADDRESS)

        verifyNoMoreInteractions(nabu)
    }

    @Test
    fun `upload pit addresses correctly formats address map if an address fetch fails`() {
        // Arrange
        btcManagerReturnsGoodAddress()
        bchManagerFailsWhenReturningAddress()
        ethManagerReturnsGoodAddress()
        xlmManagerReturnsGoodAddress()
        // PAX Addresses are the same as ETH

        whenever(nabu.shareWalletAddressesWithThePit(any(), any())).thenReturn(Completable.complete())

        // Act
        subject.sendWalletAddressToThePit()

        // Assert
        val tokenCapture = argumentCaptor<NabuOfflineTokenResponse>()
        val mapCapture = argumentCaptor<Map<String, String>>()

        verify(nabu).shareWalletAddressesWithThePit(tokenCapture.capture(), mapCapture.capture())

        assertEquals(tokenCapture.firstValue, validOfflineToken)

        val map = mapCapture.firstValue
        assertEquals(map.size, 4)
        assertEquals(map["BTC"], BTC_ACCOUNT_ADDRESS)
        assertEquals(map["BCH"], null)
        assertEquals(map["ETH"], ETH_ACCOUNT_ADDRESS)
        assertEquals(map["XLM"], XLM_ACCOUNT_ADDRESS)
        assertEquals(map["PAX"], ETH_ACCOUNT_ADDRESS)

        verifyNoMoreInteractions(nabu)
    }

    @Test
    fun `upload pit addresses formats empty address map if all address fetches fail`() {
        // Arrange
        btcManagerFailsWhenReturningAddress()
        bchManagerFailsWhenReturningAddress()
        ethManagerFailsWhenReturningAddress()
        xlmManagerFailsWhenReturningAddress()
        // PAX Addresses are the same as ETH

        whenever(nabu.shareWalletAddressesWithThePit(any(), any())).thenReturn(Completable.complete())

        // Act
        subject.sendWalletAddressToThePit()

        // Assert
        val tokenCapture = argumentCaptor<NabuOfflineTokenResponse>()
        val mapCapture = argumentCaptor<Map<String, String>>()

        verify(nabu).shareWalletAddressesWithThePit(tokenCapture.capture(), mapCapture.capture())

        assertEquals(tokenCapture.firstValue, validOfflineToken)

        val map = mapCapture.firstValue
        assertEquals(map.size, 0)

        verifyNoMoreInteractions(nabu)
    }

    @Test
    fun `upload pit addresses formats empty address map if all address are empty`() {
        // Arrange
        btcManagerReturnsEmptyAddress()
        bchManagerReturnsEmptyAddress()
        ethManagerReturnsEmptyAddress()
        xlmManagerReturnsEmptyAddress()
        // PAX Addresses are the same as ETH

        whenever(nabu.shareWalletAddressesWithThePit(any(), any())).thenReturn(Completable.complete())

        // Act
        subject.sendWalletAddressToThePit()

        // Assert
        val tokenCapture = argumentCaptor<NabuOfflineTokenResponse>()
        val mapCapture = argumentCaptor<Map<String, String>>()

        verify(nabu).shareWalletAddressesWithThePit(tokenCapture.capture(), mapCapture.capture())

        assertEquals(tokenCapture.firstValue, validOfflineToken)

        val map = mapCapture.firstValue
        assertEquals(map.size, 0)

        verifyNoMoreInteractions(nabu)
    }

    private fun btcManagerReturnsGoodAddress() {
        val mockAccount: Account = mock()
        whenever(payloadDataManager.getReceiveAddressAtPosition(mockAccount, 1))
            .thenReturn(BTC_ACCOUNT_ADDRESS)
        whenever(payloadDataManager.defaultAccount).thenReturn(mockAccount)
    }

    private fun bchManagerReturnsGoodAddress() {
        whenever(bchDataManager.getDefaultAccountPosition()).thenReturn(0)
        whenever(bchDataManager.getNextCashReceiveAddress(0))
            .thenReturn(Observable.just(BCH_ACCOUNT_ADDRESS))
    }

    private fun ethManagerReturnsGoodAddress() {
        whenever(ethDataManager.getDefaultEthAddress()).thenReturn(Single.just(ETH_ACCOUNT_ADDRESS))
    }

    private fun xlmManagerReturnsGoodAddress() {
        val mockXlmAccount: AccountReference.Xlm = mock()
        whenever(mockXlmAccount.accountId).thenReturn(XLM_ACCOUNT_ADDRESS)
        whenever(xlmDataManager.defaultAccount()).thenReturn(Single.just(mockXlmAccount))
    }

    private fun btcManagerFailsWhenReturningAddress() {
        val mockAccount: Account = mock()
        whenever(payloadDataManager.getReceiveAddressAtPosition(mockAccount, 1))
            .thenReturn(BTC_ACCOUNT_ADDRESS)

        doAnswer { throw Throwable() }.`when`(payloadDataManager).defaultAccount
    }

    private fun bchManagerFailsWhenReturningAddress() {
        whenever(bchDataManager.getDefaultAccountPosition()).thenReturn(0)
        whenever(bchDataManager.getNextCashReceiveAddress(0)).thenReturn(Observable.error(Throwable("Uh-huh")))
    }

    private fun ethManagerFailsWhenReturningAddress() {
        whenever(ethDataManager.getDefaultEthAddress()).thenReturn(Single.error(Throwable("ooooopsie")))
    }

    private fun xlmManagerFailsWhenReturningAddress() {
        val mockXlmAccount: AccountReference.Xlm = mock()
        whenever(mockXlmAccount.accountId).thenReturn(XLM_ACCOUNT_ADDRESS)
        whenever(xlmDataManager.defaultAccount()).thenReturn(Single.error(Throwable("surprise!")))
    }

    private fun btcManagerReturnsEmptyAddress() {
        val mockAccount: Account = mock()
        whenever(payloadDataManager.getReceiveAddressAtPosition(mockAccount, 1))
            .thenReturn("")
        whenever(payloadDataManager.defaultAccount).thenReturn(mockAccount)
    }

    private fun bchManagerReturnsEmptyAddress() {
        whenever(bchDataManager.getDefaultAccountPosition()).thenReturn(0)
        whenever(bchDataManager.getNextCashReceiveAddress(0)).thenReturn(Observable.just(""))
    }

    private fun ethManagerReturnsEmptyAddress() {
        whenever(ethDataManager.getDefaultEthAddress()).thenReturn(Single.just(""))
    }

    private fun xlmManagerReturnsEmptyAddress() {
        val mockXlmAccount: AccountReference.Xlm = mock()
        whenever(mockXlmAccount.accountId).thenReturn("")
        whenever(xlmDataManager.defaultAccount()).thenReturn(Single.just(mockXlmAccount))
    }

    companion object {
        private const val USER_NAME = "Smaug"

        private const val BTC_ACCOUNT_ADDRESS = "btc_account_address"
        private const val BCH_ACCOUNT_ADDRESS = "bch_account_address"
        private const val ETH_ACCOUNT_ADDRESS = "eth_account_address"
        private const val XLM_ACCOUNT_ADDRESS = "xlm_account_address"
    }
}
