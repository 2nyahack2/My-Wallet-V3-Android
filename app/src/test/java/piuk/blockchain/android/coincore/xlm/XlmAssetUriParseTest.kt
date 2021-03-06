package piuk.blockchain.android.coincore.xlm

import com.blockchain.android.testutils.rxInit
import com.blockchain.logging.CrashLogger
import com.blockchain.preferences.CurrencyPrefs
import com.blockchain.preferences.WalletStatus
import com.blockchain.sunriver.XlmDataManager
import com.blockchain.sunriver.XlmFeesFetcher
import com.blockchain.nabu.datamanagers.CustodialWalletManager
import com.blockchain.nabu.datamanagers.EligibilityProvider
import com.blockchain.nabu.service.TierService
import com.blockchain.wallet.DefaultLabels
import org.amshove.kluent.mock
import org.junit.Rule
import org.junit.Test
import piuk.blockchain.android.coincore.impl.OfflineAccountUpdater
import piuk.blockchain.android.thepit.PitLinking
import piuk.blockchain.androidcore.data.api.EnvironmentConfig
import piuk.blockchain.androidcore.data.exchangerate.ExchangeRateDataManager
import piuk.blockchain.androidcore.data.exchangerate.ExchangeRateService
import piuk.blockchain.androidcore.data.payload.PayloadDataManager
import piuk.blockchain.androidcore.data.walletoptions.WalletOptionsDataManager

class XlmAssetUriParseTest {

    @get:Rule
    val rxSchedulers = rxInit {
        mainTrampoline()
        ioTrampoline()
        computationTrampoline()
    }

    private val payloadManager: PayloadDataManager = mock()
    private val xlmDataManager: XlmDataManager = mock()
    private val xlmFeesFetcher: XlmFeesFetcher = mock()
    private val walletOptionsDataManager: WalletOptionsDataManager = mock()
    private val custodialManager: CustodialWalletManager = mock()
    private val exchangeRates: ExchangeRateDataManager = mock()
    private val historicRates: ExchangeRateService = mock()
    private val currencyPrefs: CurrencyPrefs = mock()
    private val labels: DefaultLabels = mock()
    private val pitLinking: PitLinking = mock()
    private val crashLogger: CrashLogger = mock()
    private val tiersService: TierService = mock()
    private val environmentConfig: EnvironmentConfig = mock()
    private val walletPreferences: WalletStatus = mock()
    private val eligibilityProvider: EligibilityProvider = mock()
    private val offlineCache: OfflineAccountUpdater = mock()

    private val subject = XlmAsset(
        payloadManager = payloadManager,
        xlmDataManager = xlmDataManager,
        xlmFeesFetcher = xlmFeesFetcher,
        walletOptionsDataManager = walletOptionsDataManager,
        custodialManager = custodialManager,
        exchangeRates = exchangeRates,
        historicRates = historicRates,
        currencyPrefs = currencyPrefs,
        labels = labels,
        pitLinking = pitLinking,
        crashLogger = crashLogger,
        tiersService = tiersService,
        environmentConfig = environmentConfig,
        walletPreferences = walletPreferences,
        offlineAccounts = offlineCache,
        eligibilityProvider = eligibilityProvider
    )

    @Test
    fun parseValidAddress() {

        val expectedResult = XlmAddress(
            _address = VALID_SCAN_URI,
            _label = VALID_SCAN_URI
        )

        subject.parseAddress(VALID_SCAN_URI)
            .test()
            .assertNoErrors()
            .assertResult(expectedResult)
    }

    @Test
    fun parseInvalidAddress() {

        subject.parseAddress(INVALID_SCAN_URI)
            .test()
            .assertNoErrors()
            .assertComplete()
    }

    companion object {
        private const val VALID_SCAN_URI = "GDYULVJK2T6G7HFUC76LIBKZEMXPKGINSG6566EPWJKCLXTYVWJ7XPY4"
        private const val INVALID_SCAN_URI = "bitcoin:GDY6LIBKZEMXPKGINSG6566EPWJKCLXTYVWJ7XPY4"
    }
}
