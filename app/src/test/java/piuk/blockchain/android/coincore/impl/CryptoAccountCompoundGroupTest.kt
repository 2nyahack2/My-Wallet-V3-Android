package piuk.blockchain.android.coincore.impl

import com.blockchain.android.testutils.rxInit
import com.blockchain.testutils.bitcoin
import com.nhaarman.mockito_kotlin.mock
import info.blockchain.balance.CryptoCurrency
import info.blockchain.balance.Money
import io.reactivex.Single
import org.amshove.kluent.itReturns
import org.junit.Rule
import org.junit.Test
import piuk.blockchain.android.coincore.AssetAction
import piuk.blockchain.android.coincore.CryptoAccount

class CryptoAccountCompoundGroupTest {

    @get:Rule
    val rx = rxInit {
        mainTrampoline()
        ioTrampoline()
        computationTrampoline()
    }

    @Test
    fun `group with single account returns single account balance`() {
        // Arrange
        val account: CryptoAccount = mock {
            on { accountBalance } itReturns Single.just(100.bitcoin() as Money)
        }

        val subject = CryptoAccountNonCustodialGroup(
            asset = CryptoCurrency.BTC,
            label = "group label",
            accounts = listOf(account)
        )

        // Act
        subject.accountBalance.test()
            .assertComplete()
            .assertValue(100.bitcoin())
    }

    @Test
    fun `group with two accounts returns the sum of the account balance`() {
        // Arrange
        val account1: CryptoAccount = mock {
            on { accountBalance } itReturns Single.just(100.bitcoin() as Money)
        }

        val account2: CryptoAccount = mock {
            on { accountBalance } itReturns Single.just(150.bitcoin() as Money)
        }

        val subject = CryptoAccountNonCustodialGroup(
            asset = CryptoCurrency.BTC,
            label = "group label",
            accounts = listOf(account1, account2)
        )

        // Act
        subject.accountBalance.test()
            .assertComplete()
            .assertValue(250.bitcoin())
    }

    @Test
    fun `group with single account returns single account actions`() {
        // Arrange
        val accountActions = setOf(AssetAction.Send, AssetAction.Receive)

        val account: CryptoAccount = mock {
            on { actions } itReturns Single.just(accountActions)
        }

        val subject = CryptoAccountNonCustodialGroup(
            asset = CryptoCurrency.BTC,
            label = "group label",
            accounts = listOf(account)
        )

        // Act
        val r = subject.actions.test()

        // Assert
        r.assertValue(setOf(AssetAction.Send, AssetAction.Receive))
    }

    @Test
    fun `group with three accounts returns the union of possible actions`() {
        // Arrange
        val accountActions1 = Single.just(setOf(
            AssetAction.Send,
            AssetAction.Receive
        ))

        val accountActions2 = Single.just(setOf(
            AssetAction.Send,
            AssetAction.Swap
        ))

        val accountActions3 = Single.just(setOf(
            AssetAction.Send,
            AssetAction.Receive
        ))

        val expectedResult = setOf(
            AssetAction.Send,
            AssetAction.Swap,
            AssetAction.Receive
        )

        val account1: CryptoAccount = mock {
            on { actions } itReturns accountActions1
        }

        val account2: CryptoAccount = mock {
            on { actions } itReturns accountActions2
        }

        val account3: CryptoAccount = mock {
            on { actions } itReturns accountActions3
        }

        val subject = CryptoAccountNonCustodialGroup(
            asset = CryptoCurrency.BTC,
            label = "group label",
            accounts = listOf(account1, account2, account3)
        )

        // Act
        val r = subject.actions.test()

        // Assert
        r.assertValue(expectedResult)
    }
}