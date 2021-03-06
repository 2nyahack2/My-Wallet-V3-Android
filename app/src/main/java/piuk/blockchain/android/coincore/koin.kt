package piuk.blockchain.android.coincore

import com.blockchain.koin.payloadScopeQualifier
import com.blockchain.koin.dgldFeatureFlag
import info.blockchain.balance.CryptoCurrency
import org.koin.dsl.bind
import org.koin.dsl.module
import piuk.blockchain.android.coincore.alg.AlgoAsset
import piuk.blockchain.android.coincore.bch.BchAsset
import piuk.blockchain.android.coincore.btc.BtcAsset
import piuk.blockchain.android.coincore.erc20.dgld.DgldAsset
import piuk.blockchain.android.coincore.erc20.pax.PaxAsset
import piuk.blockchain.android.coincore.erc20.usdt.UsdtAsset
import piuk.blockchain.android.coincore.eth.EthAsset
import piuk.blockchain.android.coincore.fiat.FiatAsset
import piuk.blockchain.android.coincore.impl.OfflineAccountUpdater
import piuk.blockchain.android.coincore.impl.TxProcessorFactory
import piuk.blockchain.android.coincore.impl.txEngine.TransferQuotesEngine
import piuk.blockchain.android.coincore.stx.StxAsset
import piuk.blockchain.android.coincore.xlm.XlmAsset
import piuk.blockchain.android.repositories.AssetActivityRepository

val coincoreModule = module {

    scope(payloadScopeQualifier) {

        scoped {
            StxAsset(
                payloadManager = get(),
                exchangeRates = get(),
                historicRates = get(),
                currencyPrefs = get(),
                custodialManager = get(),
                crashLogger = get(),
                pitLinking = get(),
                labels = get(),
                tiersService = get(),
                environmentConfig = get(),
                eligibilityProvider = get(),
                offlineAccounts = get()
            )
        }

        scoped {
            BtcAsset(
                exchangeRates = get(),
                sendDataManager = get(),
                feeDataManager = get(),
                environmentConfig = get(),
                historicRates = get(),
                currencyPrefs = get(),
                payloadManager = get(),
                custodialManager = get(),
                pitLinking = get(),
                crashLogger = get(),
                labels = get(),
                tiersService = get(),
                walletPreferences = get(),
                eligibilityProvider = get(),
                offlineAccounts = get(),
                coinsWebsocket = get()
            )
        }

        scoped {
            BchAsset(
                payloadManager = get(),
                bchDataManager = get(),
                exchangeRates = get(),
                historicRates = get(),
                currencyPrefs = get(),
                crashLogger = get(),
                custodialManager = get(),
                environmentSettings = get(),
                feeDataManager = get(),
                sendDataManager = get(),
                pitLinking = get(),
                labels = get(),
                tiersService = get(),
                walletPreferences = get(),
                offlineAccounts = get(),
                eligibilityProvider = get()
            )
        }

        scoped {
            XlmAsset(
                payloadManager = get(),
                xlmDataManager = get(),
                xlmFeesFetcher = get(),
                walletOptionsDataManager = get(),
                exchangeRates = get(),
                historicRates = get(),
                currencyPrefs = get(),
                custodialManager = get(),
                pitLinking = get(),
                crashLogger = get(),
                labels = get(),
                tiersService = get(),
                environmentConfig = get(),
                walletPreferences = get(),
                offlineAccounts = get(),
                eligibilityProvider = get()
            )
        }

        scoped {
            EthAsset(
                payloadManager = get(),
                ethDataManager = get(),
                feeDataManager = get(),
                exchangeRates = get(),
                historicRates = get(),
                currencyPrefs = get(),
                walletPrefs = get(),
                crashLogger = get(),
                custodialManager = get(),
                pitLinking = get(),
                labels = get(),
                tiersService = get(),
                offlineAccounts = get(),
                environmentConfig = get(),
                eligibilityProvider = get()
            )
        }

        scoped {
            PaxAsset(
                payloadManager = get(),
                ethDataManager = get(),
                feeDataManager = get(),
                exchangeRates = get(),
                historicRates = get(),
                currencyPrefs = get(),
                custodialManager = get(),
                pitLinking = get(),
                crashLogger = get(),
                labels = get(),
                tiersService = get(),
                offlineAccounts = get(),
                environmentConfig = get(),
                walletPreferences = get(),
                eligibilityProvider = get()
            )
        }

        scoped {
            AlgoAsset(
                payloadManager = get(),
                exchangeRates = get(),
                historicRates = get(),
                currencyPrefs = get(),
                custodialManager = get(),
                pitLinking = get(),
                crashLogger = get(),
                labels = get(),
                tiersService = get(),
                environmentConfig = get(),
                eligibilityProvider = get(),
                offlineAccounts = get()
            )
        }

        scoped {
            FiatAsset(
                labels = get(),
                assetBalancesRepository = get(),
                exchangeRateDataManager = get(),
                custodialWalletManager = get(),
                tierService = get(),
                currencyPrefs = get()
            )
        }

        scoped {
            UsdtAsset(
                payloadManager = get(),
                ethDataManager = get(),
                feeDataManager = get(),
                exchangeRates = get(),
                historicRates = get(),
                currencyPrefs = get(),
                custodialManager = get(),
                crashLogger = get(),
                labels = get(),
                pitLinking = get(),
                tierService = get(),
                offlineAccounts = get(),
                environmentConfig = get(),
                walletPreferences = get(),
                eligibilityProvider = get()
            )
        }

        scoped {
            DgldAsset(
                payloadManager = get(),
                ethDataManager = get(),
                feeDataManager = get(),
                exchangeRates = get(),
                historicRates = get(),
                currencyPrefs = get(),
                custodialManager = get(),
                crashLogger = get(),
                labels = get(),
                pitLinking = get(),
                environmentConfig = get(),
                walletPreferences = get(),
                eligibilityProvider = get(),
                offlineAccounts = get(),
                tiersService = get(),
                wDgldFeatureFlag = get(dgldFeatureFlag)
            )
        }

        scoped {
            Coincore(
                payloadManager = get(),
                fiatAsset = get<FiatAsset>(),
                assetMap = mapOf(
                    CryptoCurrency.BTC to get<BtcAsset>(),
                    CryptoCurrency.BCH to get<BchAsset>(),
                    CryptoCurrency.ETHER to get<EthAsset>(),
                    CryptoCurrency.XLM to get<XlmAsset>(),
                    CryptoCurrency.PAX to get<PaxAsset>(),
                    CryptoCurrency.STX to get<StxAsset>(),
                    CryptoCurrency.ALGO to get<AlgoAsset>(),
                    CryptoCurrency.USDT to get<UsdtAsset>(),
                    CryptoCurrency.DGLD to get<DgldAsset>()
                ),
                txProcessorFactory = get(),
                defaultLabels = get(),
                crashLogger = get()
            )
        }

        scoped {
            TxProcessorFactory(
                bitPayManager = get(),
                exchangeRates = get(),
                walletManager = get(),
                walletPrefs = get(),
                quotesEngine = get(),
                analytics = get(),
                kycTierService = get(),
                environmentConfig = get()
            )
        }

        scoped {
            AssetActivityRepository(
                coincore = get(),
                rxBus = get()
            )
        }

        scoped {
            AddressFactoryImpl(
                coincore = get()
            )
        }.bind(AddressFactory::class)

        scoped {
            OfflineAccountUpdater(
                localCache = get(),
                payloadManager = get(),
                walletApi = get()
            )
        }

        factory {
            TransferQuotesEngine(quotesProvider = get())
        }
    }
}
