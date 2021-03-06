package piuk.blockchain.android.ui.addresses

import com.blockchain.android.testutils.rxInit
import com.blockchain.notifications.analytics.AddressAnalytics
import com.blockchain.notifications.analytics.Analytics
import com.blockchain.notifications.analytics.WalletAnalytics
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import info.blockchain.balance.CryptoCurrency
import info.blockchain.balance.CryptoValue
import info.blockchain.balance.Money
import info.blockchain.wallet.exceptions.DecryptionException
import info.blockchain.wallet.util.PrivateKeyFactory
import io.reactivex.Completable
import io.reactivex.Single
import org.amshove.kluent.itReturns
import org.amshove.kluent.mock
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.verifyNoMoreInteractions
import piuk.blockchain.android.R
import piuk.blockchain.android.coincore.Coincore
import piuk.blockchain.android.coincore.bch.BchAsset
import piuk.blockchain.android.coincore.btc.BtcAsset
import piuk.blockchain.android.coincore.btc.BtcCryptoWalletAccount
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AccountPresenterTest {

    @get:Rule
    val initSchedulers = rxInit {
        mainTrampoline()
        ioTrampoline()
        computationTrampoline()
    }

    private val activity: AccountView = mock()
    private val privateKeyFactory: PrivateKeyFactory = mock()

    private val btcAsset: BtcAsset = mock()
    private val bchAsset: BchAsset = mock()
    private val coincore: Coincore = mock {
        on { get(CryptoCurrency.BTC) } itReturns btcAsset
        on { get(CryptoCurrency.BCH) } itReturns bchAsset
    }

    private val analytics: Analytics = mock()

    private val subject: AccountPresenter = AccountPresenter(
        privateKeyFactory,
        coincore,
        analytics
    )

    @Before
    fun setUp() {
        subject.attachView(activity)
    }

    @Test
    fun createNewAccountLabelExists() {
        // Arrange
        whenever(coincore.isLabelUnique(any())).thenReturn(Single.just(false))

        // Act
        subject.createNewAccount(NEW_BTC_LABEL)

        // Assert
        verify(activity).showProgressDialog(R.string.please_wait, null)
        verify(activity).dismissProgressDialog()
        verify(activity).showError(R.string.label_name_match)
        verifyNoMoreInteractions(activity)
    }

    @Test
    fun createNewAccountSuccessful() {
        // Arrange
        val newAccount: BtcCryptoWalletAccount = mock {
            on { xpubAddress } itReturns VALID_XPUB
        }
        whenever(btcAsset.createAccount(NEW_BTC_LABEL, null)).thenReturn(Single.just(newAccount))
        whenever(bchAsset.createAccount(VALID_XPUB)).thenReturn(Completable.complete())

        whenever(coincore.isLabelUnique(any())).thenReturn(Single.just(true))

        // Act
        subject.createNewAccount(NEW_BTC_LABEL)

        // Assert
        verify(activity).showProgressDialog(R.string.please_wait, null)
        verify(activity).dismissProgressDialog()
        verify(activity).showSuccess(R.string.remote_save_ok)
        verifyNoMoreInteractions(activity)
        verify(analytics).logEvent(WalletAnalytics.AddNewWallet)
    }

    @Test
    fun createNewAccountIncorrectSecondPassword() {
        // Arrange
        whenever(btcAsset.createAccount(NEW_BTC_LABEL, null))
            .thenReturn(Single.error(DecryptionException()))

        whenever(coincore.isLabelUnique(any())).thenReturn(Single.just(true))

        // Act
        subject.createNewAccount(NEW_BTC_LABEL)

        // Assert
        verify(activity).showProgressDialog(R.string.please_wait, null)
        verify(activity).dismissProgressDialog()
        verify(activity).showError(R.string.double_encryption_password_error)
        verifyNoMoreInteractions(activity)
    }

    @Test
    fun createNewAccountUnknownException() {
        // Arrange
        whenever(btcAsset.createAccount(NEW_BTC_LABEL, null))
            .thenReturn(Single.error(RuntimeException()))

        whenever(coincore.isLabelUnique(any())).thenReturn(Single.just(true))

        // Act
        subject.createNewAccount(NEW_BTC_LABEL)

        // Assert
        verify(activity).showProgressDialog(R.string.please_wait, null)
        verify(activity).dismissProgressDialog()
        verify(activity).showError(R.string.unexpected_error)
        verifyNoMoreInteractions(activity)
    }

    @Test
    fun updateImportedAddressLabelSuccessful() {
        // Arrange
        val cryptoValue: Money = CryptoValue.fromMinor(CryptoCurrency.BCH, 1.toBigInteger())
        val importedAccount: BtcCryptoWalletAccount = mock {
            on { updateLabel(UPDATED_BTC_LABEL) } itReturns Completable.complete()
            on { actionableBalance } itReturns Single.just(cryptoValue)
        }

        // Act
        subject.updateLegacyAddressLabel(UPDATED_BTC_LABEL, importedAccount)

        // Assert
        verify(activity).showSuccess(R.string.remote_save_ok)
        verify(activity).showProgressDialog(R.string.please_wait, null)
        verify(activity).dismissProgressDialog()
    }

    @Test
    fun updateImportedAddressLabelFailed() {
        // Arrange
        val importedAccount: BtcCryptoWalletAccount = mock {
            on { updateLabel(UPDATED_BTC_LABEL) } itReturns Completable.error(RuntimeException())
        }

        // Act
        subject.updateLegacyAddressLabel(UPDATED_BTC_LABEL, importedAccount)

        // Assert
        verify(activity).showError(R.string.remote_save_failed)
        verify(activity).showProgressDialog(R.string.please_wait, null)
        verify(activity).dismissProgressDialog()
    }

    @Test
    fun importedAddressHasBalance() {
        // Arrange
        val sendingAccount: BtcCryptoWalletAccount = mock()

        val cryptoValue = CryptoValue.fromMinor(CryptoCurrency.BTC, 1.toBigInteger())
        whenever(sendingAccount.actionableBalance).thenReturn(Single.just(cryptoValue))

        // Act
        subject.checkBalanceForTransfer(sendingAccount)

        // Assert
        verify(activity).showTransferFunds(sendingAccount)
        verifyNoMoreInteractions(activity)
    }

    @Test
    fun importedAddressHasNoBalance() {
        // Arrange
        val sendingAccount: BtcCryptoWalletAccount = mock()

        val cryptoValue = CryptoValue.ZeroBtc
        whenever(sendingAccount.actionableBalance).thenReturn(Single.just(cryptoValue))

        // Act
        subject.checkBalanceForTransfer(sendingAccount)

        // Assert
        verifyNoMoreInteractions(activity)
    }

    @Test
    fun bip38requiredPassword() {
        // Arrange
        whenever(privateKeyFactory.getFormat(SCANNED_ADDRESS)).thenReturn(PrivateKeyFactory.BIP38)

        // Act
        val result = subject.importRequiresPassword(SCANNED_ADDRESS)

        // Assert
        assertTrue(result)
    }

    fun `public Key Doesn't Require Password`() {
        // Arrange
        whenever(privateKeyFactory.getFormat(SCANNED_ADDRESS)).thenReturn(null)

        // Act
        val result = subject.importRequiresPassword(SCANNED_ADDRESS)

        // Assert
        assertFalse(result)
    }

    fun `non Bip38 Doesn't Require Password`() {
        // Arrange
        whenever(privateKeyFactory.getFormat(SCANNED_ADDRESS)).thenReturn(PrivateKeyFactory.HEX)

        // Act
        val result = subject.importRequiresPassword(SCANNED_ADDRESS)

        // Assert
        assertFalse(result)
    }

    @Test
    fun importBip38AddressWithValidPassword() {
        // Arrange
        whenever(privateKeyFactory.getFormat(SCANNED_ADDRESS))
            .thenReturn(PrivateKeyFactory.BIP38)

        val importedAccount: BtcCryptoWalletAccount = mock()
        whenever(
            btcAsset.importLegacyAddressFromKey(
                SCANNED_ADDRESS,
                PrivateKeyFactory.BIP38,
                BIP38_PASSWORD,
                null
            )
        ).thenReturn(Single.just(importedAccount))

        // Act
        subject.importScannedAddress(SCANNED_ADDRESS, BIP38_PASSWORD, null)

        // Assert
        verify(activity).showProgressDialog(R.string.please_wait, null)
        verify(activity).dismissProgressDialog()
        verify(activity).showSuccess(R.string.private_key_successfully_imported)
        verify(activity).showRenameImportedAddressDialog(importedAccount)
        verifyNoMoreInteractions(activity)
        verify(analytics).logEvent(AddressAnalytics.ImportBTCAddress)
    }

    @Test
    fun importBip38AddressWithIncorrectPassword() {
        // Arrange
        whenever(privateKeyFactory.getFormat(SCANNED_ADDRESS))
            .thenReturn(PrivateKeyFactory.BIP38)

        whenever(
            btcAsset.importLegacyAddressFromKey(
                SCANNED_ADDRESS,
                PrivateKeyFactory.BIP38,
                BIP38_PASSWORD,
                null
            )
        ).thenReturn(Single.error(RuntimeException()))

        // Act
        subject.importScannedAddress(SCANNED_ADDRESS, BIP38_PASSWORD, null)

        // Assert
        verify(activity).showProgressDialog(R.string.please_wait, null)
        verify(activity).dismissProgressDialog()
        verify(activity).showError(R.string.no_private_key)
        verifyNoMoreInteractions(activity)
    }

    @Test
    fun onAddressScannedNonBip38() {
        // Arrange
        whenever(privateKeyFactory.getFormat(SCANNED_ADDRESS))
            .thenReturn(PrivateKeyFactory.HEX)

        val importedAccount: BtcCryptoWalletAccount = mock()
        whenever(
            btcAsset.importLegacyAddressFromKey(
                SCANNED_ADDRESS,
                PrivateKeyFactory.HEX,
                null,
                null
            )
        ).thenReturn(Single.just(importedAccount))

        // Act
        subject.importScannedAddress(SCANNED_ADDRESS, null)

        // Assert
        verify(activity).showProgressDialog(R.string.please_wait, null)
        verify(activity).dismissProgressDialog()
        verify(activity).showSuccess(R.string.private_key_successfully_imported)
        verify(activity).showRenameImportedAddressDialog(importedAccount)
        verifyNoMoreInteractions(activity)
        verify(analytics).logEvent(AddressAnalytics.ImportBTCAddress)
    }

    @Test
    fun onAddressScannedNonBip38Failure() {
        // Arrange
        whenever(privateKeyFactory.getFormat(SCANNED_ADDRESS))
            .thenReturn(PrivateKeyFactory.HEX)

        whenever(
            btcAsset.importLegacyAddressFromKey(
                SCANNED_ADDRESS,
                PrivateKeyFactory.HEX,
                null,
                null
            )
        ).thenReturn(Single.error(RuntimeException()))

        // Act
        subject.importScannedAddress(SCANNED_ADDRESS, null)

        // Assert
        verify(activity).showProgressDialog(R.string.please_wait, null)
        verify(activity).dismissProgressDialog()
        verify(activity).showError(R.string.no_private_key)
        verifyNoMoreInteractions(activity)
    }

    @Test
    fun onAddressScannedWatchOnlyInvalidAddress() {
        // Arrange
        whenever(privateKeyFactory.getFormat(SCANNED_ADDRESS)).thenReturn(null)
        whenever(btcAsset.isValidAddress(SCANNED_ADDRESS)).thenReturn(true)

        // Act
        subject.importScannedAddress(SCANNED_ADDRESS, null)

        // Assert
        verify(activity).showError(R.string.watch_only_not_supported)
        verifyNoMoreInteractions(activity)
    }

    @Test
    fun onAddressScannedUnknownFormat() {
        // Arrange
        whenever(privateKeyFactory.getFormat(SCANNED_ADDRESS)).thenReturn(null)
        whenever(btcAsset.isValidAddress(SCANNED_ADDRESS)).thenReturn(false)

        // Act
        subject.importScannedAddress(SCANNED_ADDRESS, null)

        // Assert
        verify(activity).showError(R.string.privkey_error)
        verifyNoMoreInteractions(activity)
    }

    companion object {
        private const val VALID_XPUB = "hsdjseoihefsihdfsihefsohifes"
        private const val NEW_BTC_LABEL = "New BTC Account"
        private const val UPDATED_BTC_LABEL = "Updated Label"
        private const val SCANNED_ADDRESS = "6PRJmkckxBct8jUwn6UcJbickdrnXBiPP9JkNW83g4VyFNsfEuxas39pSS"
        private const val BIP38_PASSWORD = "verysecurepassword"
    }
}