package piuk.blockchain.android.coincore.btc

import com.blockchain.android.testutils.rxInit
import com.blockchain.logging.CrashLogger
import com.blockchain.preferences.CurrencyPrefs
import com.blockchain.preferences.WalletStatus
import com.blockchain.nabu.datamanagers.CustodialWalletManager
import com.blockchain.nabu.datamanagers.EligibilityProvider
import com.blockchain.nabu.service.TierService
import com.blockchain.wallet.DefaultLabels
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.verifyNoMoreInteractions
import com.nhaarman.mockito_kotlin.whenever
import info.blockchain.wallet.payload.data.Account
import info.blockchain.wallet.payload.data.LegacyAddress
import info.blockchain.wallet.util.PrivateKeyFactory
import io.reactivex.Observable
import io.reactivex.Single
import org.amshove.kluent.itReturns
import org.bitcoinj.core.ECKey
import org.bitcoinj.core.NetworkParameters
import org.bitcoinj.crypto.BIP38PrivateKey.BadPassphraseException
import org.junit.Rule
import org.junit.Test
import piuk.blockchain.android.coincore.impl.OfflineAccountUpdater
import piuk.blockchain.android.data.coinswebsocket.strategy.CoinsWebSocketStrategy
import piuk.blockchain.android.thepit.PitLinking
import piuk.blockchain.androidcore.data.api.EnvironmentConfig
import piuk.blockchain.androidcore.data.exchangerate.ExchangeRateDataManager
import piuk.blockchain.androidcore.data.exchangerate.ExchangeRateService
import piuk.blockchain.androidcore.data.fees.FeeDataManager
import piuk.blockchain.androidcore.data.payload.PayloadDataManager
import piuk.blockchain.androidcore.data.payments.SendDataManager

class BtcAssetTest {

    @get:Rule
    val initSchedulers = rxInit {
        mainTrampoline()
        ioTrampoline()
        computationTrampoline()
    }

    private val payloadManager: PayloadDataManager = mock()
    private val sendDataManager: SendDataManager = mock()
    private val feeDataManager: FeeDataManager = mock()
    private val coinsWebsocket: CoinsWebSocketStrategy = mock()
    private val custodialManager: CustodialWalletManager = mock()
    private val exchangeRates: ExchangeRateDataManager = mock()
    private val historicRates: ExchangeRateService = mock()
    private val currencyPrefs: CurrencyPrefs = mock()
    private val labels: DefaultLabels = mock()
    private val pitLinking: PitLinking = mock()
    private val crashLogger: CrashLogger = mock()
    private val tiersService: TierService = mock()
    private val btcParams: NetworkParameters = mock()
    private val environmentConfig: EnvironmentConfig = mock {
        on { bitcoinNetworkParameters } itReturns btcParams
    }
    private val walletPreferences: WalletStatus = mock()
    private val eligibilityProvider: EligibilityProvider = mock()
    private val offlineCache: OfflineAccountUpdater = mock()

    private val subject = BtcAsset(
        payloadManager = payloadManager,
        sendDataManager = sendDataManager,
        feeDataManager = feeDataManager,
        coinsWebsocket = coinsWebsocket,
        custodialManager = custodialManager,
        exchangeRates = exchangeRates,
        historicRates = historicRates,
        currencyPrefs = currencyPrefs,
        labels = labels,
        pitLinking = pitLinking,
        crashLogger = crashLogger,
        tiersService = tiersService,
        environmentConfig = environmentConfig,
        offlineAccounts = offlineCache,
        walletPreferences = walletPreferences,
        eligibilityProvider = eligibilityProvider
    )

    @Test
    fun createAccountSuccessNoSecondPassword() {
        val mockInternalAccount: Account = mock {
            on { xpub } itReturns NEW_XPUB
        }

        whenever(payloadManager.createNewAccount(TEST_LABEL, null)).thenReturn(
            Observable.just(mockInternalAccount)
        )
        whenever(payloadManager.accountCount).thenReturn(NUM_ACCOUNTS)

        subject.createAccount(TEST_LABEL, null)
            .test()
            .assertValue {
                it.isHDAccount &&
                !it.isArchived &&
                it.xpubAddress == NEW_XPUB
            }.assertComplete()

        verify(coinsWebsocket).subscribeToXpubBtc(NEW_XPUB)
    }

    @Test
    fun createAccountFailed() {
        whenever(payloadManager.createNewAccount(TEST_LABEL, null)).thenReturn(
            Observable.error(Exception("Something went wrong"))
        )

        subject.createAccount(TEST_LABEL, null)
            .test()
            .assertError(Exception::class.java)

        verifyNoMoreInteractions(coinsWebsocket)
    }

    @Test
    fun importNonBip38Success() {
        val ecKey: ECKey = mock {
            on { hasPrivKey() } itReturns true
        }

        val internalAccount: LegacyAddress = mock {
            on { address } itReturns IMPORTED_ADDRESS
        }

        whenever(payloadManager.getKeyFromImportedData(NON_BIP38_FORMAT, KEY_DATA))
            .thenReturn(Single.just(ecKey))
        whenever(payloadManager.addLegacyAddressFromKey(ecKey, null))
            .thenReturn(Single.just(internalAccount))

        subject.importLegacyAddressFromKey(KEY_DATA, NON_BIP38_FORMAT, null, null)
            .test()
            .assertValue {
                !it.isHDAccount &&
                !it.isArchived &&
                it.xpubAddress == IMPORTED_ADDRESS
            }.assertComplete()

        verify(coinsWebsocket).subscribeToExtraBtcAddress(IMPORTED_ADDRESS)
    }

    @Test
    fun importNonBip38NoPrivateKey() {
        val ecKey: ECKey = mock {
            on { hasPrivKey() } itReturns true
        }

        whenever(payloadManager.getKeyFromImportedData(NON_BIP38_FORMAT, KEY_DATA))
            .thenReturn(Single.just(ecKey))

        subject.importLegacyAddressFromKey(KEY_DATA, NON_BIP38_FORMAT, null, null)
            .test()
            .assertError(Exception::class.java)

        verifyNoMoreInteractions(coinsWebsocket)
    }

    @Test
    fun importNonBip38InvalidFormat() {
        whenever(payloadManager.getKeyFromImportedData(NON_BIP38_FORMAT, KEY_DATA))
            .thenReturn(Single.error(Exception()))

        subject.importLegacyAddressFromKey(KEY_DATA, NON_BIP38_FORMAT, null, null)
            .test()
            .assertError(Exception::class.java)

        verifyNoMoreInteractions(coinsWebsocket)
    }

    @Test
    fun importBip38Success() {
        val ecKey: ECKey = mock {
            on { hasPrivKey() } itReturns true
        }

        val internalAccount: LegacyAddress = mock {
            on { address } itReturns IMPORTED_ADDRESS
        }

        whenever(payloadManager.getBip38KeyFromImportedData(KEY_DATA, KEY_PASSWORD))
            .thenReturn(Single.just(ecKey))
        whenever(payloadManager.addLegacyAddressFromKey(ecKey, null))
            .thenReturn(Single.just(internalAccount))

        subject.importLegacyAddressFromKey(KEY_DATA, BIP38_FORMAT, KEY_PASSWORD, null)
            .test()
            .assertValue {
                !it.isHDAccount &&
                !it.isArchived &&
                it.xpubAddress == IMPORTED_ADDRESS
            }.assertComplete()

        verify(coinsWebsocket).subscribeToExtraBtcAddress(IMPORTED_ADDRESS)
    }

    @Test
    fun importBip38BadPassword() {
        whenever(payloadManager.getBip38KeyFromImportedData(KEY_DATA, KEY_PASSWORD))
            .thenReturn(Single.error(BadPassphraseException()))

        subject.importLegacyAddressFromKey(KEY_DATA, BIP38_FORMAT, KEY_PASSWORD, null)
            .test()
            .assertError(BadPassphraseException::class.java)

        verifyNoMoreInteractions(coinsWebsocket)
    }

    companion object {
        private const val TEST_LABEL = "TestLabel"
        private const val NEW_XPUB = "jaohaeoufoaehfoiuaehfiuhaefiuaeifuhaeifuh"
        private const val NUM_ACCOUNTS = 5

        private const val KEY_DATA = "aefouaoefkajdfsnkajsbkjasbdfkjbaskjbasfkj"
        private const val NON_BIP38_FORMAT = PrivateKeyFactory.BASE64
        private const val BIP38_FORMAT = PrivateKeyFactory.BIP38
        private const val KEY_PASSWORD = "SuperSecurePassword"
        private const val IMPORTED_ADDRESS = "aeoiawfohiawiawiohawdfoihawdhioadwfohiafwoih"
    }
}
