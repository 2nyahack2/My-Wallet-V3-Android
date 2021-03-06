package piuk.blockchain.android.coincore.impl

import com.blockchain.android.testutils.rxInit
import com.blockchain.nabu.datamanagers.CustodialWalletManager
import com.nhaarman.mockito_kotlin.times
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.verifyNoMoreInteractions
import com.nhaarman.mockito_kotlin.whenever
import info.blockchain.balance.CryptoCurrency
import io.reactivex.Single
import org.amshove.kluent.mock
import org.junit.Rule
import org.junit.Test
import piuk.blockchain.android.coincore.CryptoAccount
import piuk.blockchain.android.coincore.SingleAccountList

class ActiveAccountListTest {

    @get:Rule
    val rxSchedulers = rxInit {
        mainTrampoline()
        ioTrampoline()
        computationTrampoline()
    }

    private val custodialManager: CustodialWalletManager = mock()
    private val subject = ActiveAccountList(
        asset = TEST_ASSET,
        custodialManager = custodialManager
    )

    @Test
    fun noAccountsFound() {

        whenever(custodialManager.getInterestAvailabilityForAsset(TEST_ASSET))
            .thenReturn(Single.just(false))

        subject.fetchAccountList(::loadEmptyAccountList)
            .test()
            .assertComplete()
            .assertValue { it.isEmpty() }

        verify(custodialManager).getInterestAvailabilityForAsset(TEST_ASSET)
        verifyNoMoreInteractions(custodialManager)
    }

    @Test
    fun allAccountsLoaded() {

        whenever(custodialManager.getInterestAvailabilityForAsset(TEST_ASSET))
            .thenReturn(Single.just(false))

        subject.fetchAccountList(::loadFourAccountList)
            .test()
            .assertComplete()
            .assertValue { it.size == 4 }

        verify(custodialManager).getInterestAvailabilityForAsset(TEST_ASSET)
        verifyNoMoreInteractions(custodialManager)
    }

    @Test
    fun noReloadIfUnchanged() {

        whenever(custodialManager.getInterestAvailabilityForAsset(TEST_ASSET))
            .thenReturn(Single.just(false))
            .thenReturn(Single.just(false))

        subject.fetchAccountList(::loadTwoAccountList)
            .test()
            .assertComplete()
            .assertValue { it.size == 2 }

        subject.fetchAccountList(::loadThreeAccountList)
            .test()
            .assertComplete()
            .assertValue { it.size == 2 }

        verify(custodialManager, times(2)).getInterestAvailabilityForAsset(TEST_ASSET)
        verifyNoMoreInteractions(custodialManager)
    }

    @Test
    fun reloadIfInterestStateChanged() {

        whenever(custodialManager.getInterestAvailabilityForAsset(TEST_ASSET))
            .thenReturn(Single.just(false))
            .thenReturn(Single.just(true))

        subject.fetchAccountList(::loadTwoAccountList)
            .test()
            .assertComplete()
            .assertValue { it.size == 2 }

        subject.fetchAccountList(::loadThreeAccountList)
            .test()
            .assertComplete()
            .assertValue { it.size == 3 }

        verify(custodialManager, times(2)).getInterestAvailabilityForAsset(TEST_ASSET)
        verifyNoMoreInteractions(custodialManager)
    }

    @Test
    fun reloadIfForced() {

        whenever(custodialManager.getInterestAvailabilityForAsset(TEST_ASSET))
            .thenReturn(Single.just(false))
            .thenReturn(Single.just(false))

        subject.fetchAccountList(::loadTwoAccountList)
            .test()
            .assertComplete()
            .assertValue { it.size == 2 }

        subject.setForceRefresh()

        subject.fetchAccountList(::loadThreeAccountList)
            .test()
            .assertComplete()
            .assertValue { it.size == 3 }

        verify(custodialManager, times(2)).getInterestAvailabilityForAsset(TEST_ASSET)
        verifyNoMoreInteractions(custodialManager)
    }

    @Test
    fun forceLoadResets() {

        whenever(custodialManager.getInterestAvailabilityForAsset(TEST_ASSET))
            .thenReturn(Single.just(false))
            .thenReturn(Single.just(false))
            .thenReturn(Single.just(false))

        subject.fetchAccountList(::loadTwoAccountList)
            .test()
            .assertComplete()
            .assertValue { it.size == 2 }

        subject.setForceRefresh()

        subject.fetchAccountList(::loadThreeAccountList)
            .test()
            .assertComplete()
            .assertValue { it.size == 3 }

        subject.fetchAccountList(::loadFourAccountList)
            .test()
            .assertComplete()
            .assertValue { it.size == 3 }

        verify(custodialManager, times(3)).getInterestAvailabilityForAsset(TEST_ASSET)
        verifyNoMoreInteractions(custodialManager)
    }

    @Test
    fun accountsAreRemoved() {

        whenever(custodialManager.getInterestAvailabilityForAsset(TEST_ASSET))
            .thenReturn(Single.just(false))
            .thenReturn(Single.just(false))

        subject.fetchAccountList(::loadFourAccountList)
            .test()
            .assertComplete()
            .assertValue { it.size == 4 }

        subject.setForceRefresh()

        subject.fetchAccountList(::loadThreeAccountList)
            .test()
            .assertComplete()
            .assertValue { it.size == 3 }
            .assertValue { !it.contains(mockAccountD) }

        verify(custodialManager, times(2)).getInterestAvailabilityForAsset(TEST_ASSET)
        verifyNoMoreInteractions(custodialManager)
    }

    @Test
    fun errorsArePropagatedFromLoad() {

        whenever(custodialManager.getInterestAvailabilityForAsset(TEST_ASSET))
            .thenReturn(Single.just(false))

        subject.fetchAccountList(::loadAccountListFailed)
            .test()
            .assertError(Throwable::class.java)

        verify(custodialManager).getInterestAvailabilityForAsset(TEST_ASSET)
        verifyNoMoreInteractions(custodialManager)
    }

    @Test
    fun noRefreshIfWalletManagerFails() {

        whenever(custodialManager.getInterestAvailabilityForAsset(TEST_ASSET))
            .thenReturn(Single.error<Boolean>(Throwable("Not today, chum")))

        subject.fetchAccountList(::loadOneAccountList)
            .test()
            .assertComplete()
            .assertValue { it.isEmpty() }

        verify(custodialManager).getInterestAvailabilityForAsset(TEST_ASSET)
        verifyNoMoreInteractions(custodialManager)
    }

    private val mockAccountA: CryptoAccount = mock()
    private val mockAccountB: CryptoAccount = mock()
    private val mockAccountC: CryptoAccount = mock()
    private val mockAccountD: CryptoAccount = mock()

    private fun loadEmptyAccountList(): Single<SingleAccountList> =
        Single.just(listOf())

    private fun loadOneAccountList(): Single<SingleAccountList> =
        Single.just(
            listOf(
                mockAccountA
            )
        )

    private fun loadTwoAccountList(): Single<SingleAccountList> =
        Single.just(
            listOf(
                mockAccountA,
                mockAccountB
            )
        )

    private fun loadThreeAccountList(): Single<SingleAccountList> =
        Single.just(
            listOf(
                mockAccountA,
                mockAccountB,
                mockAccountC
            )
        )

    private fun loadFourAccountList(): Single<SingleAccountList> =
        Single.just(
            listOf(
                mockAccountA,
                mockAccountB,
                mockAccountC,
                mockAccountD
            )
        )

    private fun loadAccountListFailed(): Single<SingleAccountList> =
        Single.error(Throwable("Something went wrong"))

    companion object {
        private val TEST_ASSET = CryptoCurrency.BTC
    }
}
