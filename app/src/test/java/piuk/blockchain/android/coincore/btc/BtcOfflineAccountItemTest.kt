package piuk.blockchain.android.coincore.btc

import com.blockchain.android.testutils.rxInit
import com.nhaarman.mockito_kotlin.mock
import info.blockchain.balance.CryptoCurrency
import info.blockchain.balance.CryptoValue
import org.junit.Rule
import org.junit.Test
import org.amshove.kluent.itReturns
import piuk.blockchain.android.coincore.impl.OfflineBalanceCall
import piuk.blockchain.android.coincore.CachedAddress
import io.reactivex.Single

class BtcOfflineAccountItemTest {

    @get:Rule
    val initSchedulers = rxInit {
        mainTrampoline()
        ioTrampoline()
        computationTrampoline()
    }

    private val subject = BtcOfflineAccountItem(
        "BTC Account",
        ADDRESS_LIST.map {
            CachedAddress(address = it, addressUri = "bitcoin:$it")
        }
    )

    @Test
    fun getNextIsAvailable() {
        // Arrange
        val balanceMap = mapOf(
            ADDRESS_1 to CryptoValue.ZeroBtc,
            ADDRESS_2 to CryptoValue.ZeroBtc,
            ADDRESS_3 to CryptoValue.ZeroBtc,
            ADDRESS_4 to CryptoValue.ZeroBtc,
            ADDRESS_5 to CryptoValue.ZeroBtc
        )

        val balanceCall: OfflineBalanceCall = mock {
            on { getBalanceOfAddresses(CryptoCurrency.BTC, ADDRESS_LIST) } itReturns Single.just(balanceMap)
        }
        // Act
        subject.nextAddress(balanceCall)
            .test()
            .assertValue { it.address == ADDRESS_1 }
    }

    @Test
    fun noAddressesAvailable() {
        // Arrange
        val balanceMap = mapOf<String, CryptoValue>(
            ADDRESS_1 to CryptoValue.fromMinor(CryptoCurrency.BTC, 1.toBigDecimal()),
            ADDRESS_2 to CryptoValue.fromMinor(CryptoCurrency.BTC, 1.toBigDecimal()),
            ADDRESS_3 to CryptoValue.fromMinor(CryptoCurrency.BTC, 1.toBigDecimal()),
            ADDRESS_4 to CryptoValue.fromMinor(CryptoCurrency.BTC, 1.toBigDecimal()),
            ADDRESS_5 to CryptoValue.fromMinor(CryptoCurrency.BTC, 1.toBigDecimal())
        )

        val balanceCall: OfflineBalanceCall = mock {
            on { getBalanceOfAddresses(CryptoCurrency.BTC, ADDRESS_LIST) } itReturns Single.just(balanceMap)
        }
        // Act
        subject.nextAddress(balanceCall)
            .test()
            .assertComplete()
    }

    companion object {
        private const val ADDRESS_1 = "1BpEi6DfDAUFd7GtittLSdBeYJvcoaVggu"
        private const val ADDRESS_2 = "1KXrWXciRDZUpQwQmuM1DbwsKDLYAYsVLR"
        private const val ADDRESS_3 = "16w1D5WRVKJuZUsSRzdLp9w3YGcgoxDXb"
        private const val ADDRESS_4 = "1DJk1Feuabguw5CW9CGQRQ3U1pp5Pbn3HK"
        private const val ADDRESS_5 = "1ATy3ktyaYjzZZQQnhvPsuBVheUDYcUP7V"

        private val ADDRESS_LIST = listOf(
            ADDRESS_1, ADDRESS_2, ADDRESS_3, ADDRESS_4, ADDRESS_5
        )
    }
}
