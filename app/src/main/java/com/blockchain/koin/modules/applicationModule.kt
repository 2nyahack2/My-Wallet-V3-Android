package com.blockchain.koin.modules

import android.content.Context
import com.blockchain.accounts.AccountList
import com.blockchain.koin.bch
import com.blockchain.koin.btc
import com.blockchain.koin.eth
import com.blockchain.koin.eur
import com.blockchain.koin.explorerRetrofit
import com.blockchain.koin.gbp
import com.blockchain.koin.interestAccountFeatureFlag
import com.blockchain.koin.moshiExplorerRetrofit
import com.blockchain.koin.pax
import com.blockchain.koin.payloadScope
import com.blockchain.koin.payloadScopeQualifier
import com.blockchain.koin.usdt
import com.blockchain.logging.DigitalTrust
import com.blockchain.nabu.datamanagers.custodialwalletimpl.PaymentAccountMapper
import com.blockchain.network.websocket.Options
import com.blockchain.network.websocket.autoRetry
import com.blockchain.network.websocket.debugLog
import com.blockchain.network.websocket.newBlockchainWebSocket
import com.blockchain.ui.password.SecondPasswordHandler
import com.blockchain.wallet.DefaultLabels
import com.google.gson.GsonBuilder
import info.blockchain.wallet.metadata.MetadataDerivation
import io.reactivex.android.schedulers.AndroidSchedulers
import okhttp3.OkHttpClient
import org.bitcoinj.params.BitcoinMainNetParams
import org.koin.dsl.bind
import org.koin.dsl.module
import piuk.blockchain.android.BuildConfig
import piuk.blockchain.android.accounts.BchAccountListAdapter
import piuk.blockchain.android.accounts.BtcAccountListAdapter
import piuk.blockchain.android.accounts.EthAccountListAdapter
import piuk.blockchain.android.accounts.PaxAccountListAdapter
import piuk.blockchain.android.accounts.UsdtAccountListAdapter
import piuk.blockchain.android.cards.CardModel
import piuk.blockchain.android.cards.partners.EverypayCardActivator
import piuk.blockchain.android.coincore.AssetOrdering
import piuk.blockchain.android.coincore.OfflineAccountCache
import piuk.blockchain.android.coincore.impl.OfflineBalanceCall
import piuk.blockchain.android.data.api.bitpay.BitPayDataManager
import piuk.blockchain.android.data.api.bitpay.BitPayService
import piuk.blockchain.android.data.biometrics.BiometricAuth
import piuk.blockchain.android.data.biometrics.BiometricsController
import piuk.blockchain.android.data.biometrics.CryptographyManager
import piuk.blockchain.android.data.biometrics.CryptographyManagerImpl
import piuk.blockchain.android.data.coinswebsocket.service.CoinsWebSocketService
import piuk.blockchain.android.data.coinswebsocket.strategy.CoinsWebSocketStrategy
import piuk.blockchain.android.deeplink.DeepLinkProcessor
import piuk.blockchain.android.deeplink.EmailVerificationDeepLinkHelper
import piuk.blockchain.android.identity.SiftDigitalTrust
import piuk.blockchain.android.kyc.KycDeepLinkHelper
import piuk.blockchain.android.remoteconfig.AssetOrderingRemoteConfig
import piuk.blockchain.android.scan.QrCodeDataManager
import piuk.blockchain.android.scan.QrScanResultProcessor
import piuk.blockchain.android.simplebuy.EURPaymentAccountMapper
import piuk.blockchain.android.simplebuy.GBPPaymentAccountMapper
import piuk.blockchain.android.simplebuy.SimpleBuyFlowNavigator
import piuk.blockchain.android.simplebuy.SimpleBuyInflateAdapter
import piuk.blockchain.android.simplebuy.SimpleBuyInteractor
import piuk.blockchain.android.simplebuy.SimpleBuyModel
import piuk.blockchain.android.simplebuy.SimpleBuyState
import piuk.blockchain.android.simplebuy.SimpleBuySyncFactory
import piuk.blockchain.android.sunriver.SunriverDeepLinkHelper
import piuk.blockchain.android.thepit.PitLinking
import piuk.blockchain.android.thepit.PitLinkingImpl
import piuk.blockchain.android.thepit.ThePitDeepLinkParser
import piuk.blockchain.android.ui.addresses.AccountPresenter
import piuk.blockchain.android.ui.airdrops.AirdropCentrePresenter
import piuk.blockchain.android.ui.auth.FirebaseMobileNoticeRemoteConfig
import piuk.blockchain.android.ui.auth.MobileNoticeRemoteConfig
import piuk.blockchain.android.ui.auth.PinEntryPresenter
import piuk.blockchain.android.ui.backup.completed.BackupWalletCompletedPresenter
import piuk.blockchain.android.ui.backup.start.BackupWalletStartingPresenter
import piuk.blockchain.android.ui.backup.verify.BackupVerifyPresenter
import piuk.blockchain.android.ui.backup.wordlist.BackupWalletWordListPresenter
import piuk.blockchain.android.ui.createwallet.CreateWalletPresenter
import piuk.blockchain.android.ui.customviews.SecondPasswordDialog
import piuk.blockchain.android.ui.customviews.SwapTrendingPairsProvider
import piuk.blockchain.android.ui.customviews.TrendingPairsProvider
import piuk.blockchain.android.ui.customviews.dialogs.OverlayDetection
import piuk.blockchain.android.ui.dashboard.BalanceAnalyticsReporter
import piuk.blockchain.android.ui.dashboard.DashboardInteractor
import piuk.blockchain.android.ui.dashboard.DashboardModel
import piuk.blockchain.android.ui.dashboard.DashboardState
import piuk.blockchain.android.ui.dashboard.assetdetails.AssetDetailsInteractor
import piuk.blockchain.android.ui.dashboard.assetdetails.AssetDetailsModel
import piuk.blockchain.android.ui.dashboard.assetdetails.AssetDetailsState
import piuk.blockchain.android.ui.home.CredentialsWiper
import piuk.blockchain.android.ui.home.MainPresenter
import piuk.blockchain.android.ui.kyc.settings.KycStatusHelper
import piuk.blockchain.android.ui.launcher.DeepLinkPersistence
import piuk.blockchain.android.ui.launcher.LauncherPresenter
import piuk.blockchain.android.ui.launcher.Prerequisites
import piuk.blockchain.android.ui.lockbox.LockboxLandingPresenter
import piuk.blockchain.android.ui.onboarding.OnboardingPresenter
import piuk.blockchain.android.ui.pairingcode.PairingCodePresenter
import piuk.blockchain.android.ui.recover.RecoverFundsPresenter
import piuk.blockchain.android.ui.sell.BuySellFlowNavigator
import piuk.blockchain.android.ui.settings.SettingsPresenter
import piuk.blockchain.android.ui.shortcuts.receive.ReceiveQrPresenter
import piuk.blockchain.android.ui.ssl.SSLVerifyPresenter
import piuk.blockchain.android.ui.swipetoreceive.LocalOfflineAccountCache
import piuk.blockchain.android.ui.swipetoreceive.SwipeToReceivePresenter
import piuk.blockchain.android.ui.thepit.PitPermissionsPresenter
import piuk.blockchain.android.ui.thepit.PitVerifyEmailPresenter
import piuk.blockchain.android.ui.transfer.AccountsSorting
import piuk.blockchain.android.ui.transfer.DefaultAccountsSorting
import piuk.blockchain.android.ui.transfer.receive.activity.ReceivePresenter
import piuk.blockchain.android.ui.upgrade.UpgradeWalletPresenter
import piuk.blockchain.android.util.AppUtil
import piuk.blockchain.android.util.AssetResourceFactory
import piuk.blockchain.android.util.BackupWalletUtil
import piuk.blockchain.android.util.CurrentContextAccess
import piuk.blockchain.android.util.OSUtil
import piuk.blockchain.android.util.PrngHelper
import piuk.blockchain.android.util.ResourceDefaultLabels
import piuk.blockchain.android.util.RootUtil
import piuk.blockchain.android.util.StringUtils
import piuk.blockchain.android.util.lifecycle.LifecycleInterestedComponent
import piuk.blockchain.android.withdraw.mvi.WithdrawInteractor
import piuk.blockchain.android.withdraw.mvi.WithdrawModel
import piuk.blockchain.android.withdraw.mvi.WithdrawStatePersistence
import piuk.blockchain.androidcore.data.api.ConnectionApi
import piuk.blockchain.androidcore.data.auth.metadata.WalletCredentialsMetadataUpdater
import piuk.blockchain.androidcore.data.bitcoincash.BchDataManager
import piuk.blockchain.androidcore.data.ethereum.EthDataManager
import piuk.blockchain.androidcore.utils.PrngFixer
import piuk.blockchain.androidcore.utils.SSLVerifyUtil

val applicationModule = module {

    factory { OSUtil(get()) }

    factory { StringUtils(get()) }

    single {
        AppUtil(
            context = get(),
            payloadManager = get(),
            accessState = get(),
            prefs = get()
        )
    }

    factory { RootUtil() }

    single {
        CoinsWebSocketService(
            applicationContext = get()
        )
    }

    factory { get<Context>().resources }

    single { CurrentContextAccess() }

    single { LifecycleInterestedComponent() }

    single {
        SiftDigitalTrust(
            accountId = BuildConfig.SIFT_ACCOUNT_ID,
            beaconKey = BuildConfig.SIFT_BEACON_KEY
        )
    }.bind(DigitalTrust::class)

    scope(payloadScopeQualifier) {
        factory {
            EthDataManager(
                payloadDataManager = get(),
                ethAccountApi = get(),
                ethDataStore = get(),
                erc20DataStore = get(),
                walletOptionsDataManager = get(),
                metadataManager = get(),
                environmentSettings = get(),
                lastTxUpdater = get(),
                rxBus = get()
            )
        }

        factory {
            BchDataManager(
                payloadDataManager = get(),
                bchDataStore = get(),
                environmentSettings = get(),
                blockExplorer = get(),
                defaultLabels = get(),
                metadataManager = get(),
                rxBus = get()
            )
        }

        factory {
            SecondPasswordDialog(contextAccess = get(), payloadManager = get())
        }.bind(SecondPasswordHandler::class)

        factory { KycStatusHelper(get(), get(), get(), get()) }

        scoped {
            CredentialsWiper(
                payloadManagerWiper = get(),
                accessState = get(),
                appUtil = get(),
                ethDataManager = get(),
                bchDataManager = get(),
                metadataManager = get(),
                walletOptionsState = get(),
                nabuDataManager = get()
            )
        }

        factory {
            MainPresenter(
                prefs = get(),
                accessState = get(),
                credentialsWiper = get(),
                payloadDataManager = get(),
                exchangeRateFactory = get(),
                qrProcessor = get(),
                environmentSettings = get(),
                kycStatusHelper = get(),
                lockboxDataManager = get(),
                deepLinkProcessor = get(),
                sunriverCampaignRegistration = get(),
                xlmDataManager = get(),
                pitLinking = get(),
                nabuDataManager = get(),
                nabuToken = get(),
                simpleBuySync = get(),
                crashLogger = get(),
                analytics = get()
            )
        }

        factory(gbp) {
            GBPPaymentAccountMapper(stringUtils = get())
        }.bind(PaymentAccountMapper::class)

        factory(eur) {
            EURPaymentAccountMapper(stringUtils = get())
        }.bind(PaymentAccountMapper::class)

        scoped {
            CoinsWebSocketStrategy(
                coinsWebSocket = get(),
                ethDataManager = get(),
                stringUtils = get(),
                gson = get(),
                payloadDataManager = get(),
                bchDataManager = get(),
                rxBus = get(),
                prefs = get(),
                appUtil = get(),
                accessState = get(),
                assetResources = get()
            )
        }

        factory {
            GsonBuilder().create()
        }

        factory {
            OkHttpClient()
                .newBlockchainWebSocket(options = Options(url = BuildConfig.COINS_WEBSOCKET_URL))
                .autoRetry().debugLog("COIN_SOCKET")
        }

        factory {
            UpgradeWalletPresenter(
                prefs = get(),
                appUtil = get(),
                accessState = get(),
                stringUtils = get(),
                authDataManager = get(),
                payloadDataManager = get(),
                crashLogger = get()
            )
        }

        factory {
            PairingCodePresenter(
                qrCodeDataManager = get(),
                stringUtils = get(),
                payloadDataManager = get(),
                authDataManager = get()
            )
        }

        factory {
            CreateWalletPresenter(
                payloadDataManager = get(),
                prefs = get(),
                appUtil = get(),
                accessState = get(),
                prngFixer = get(),
                analytics = get(),
                walletPrefs = get(),
                environmentConfig = get()
            )
        }

        factory {
            RecoverFundsPresenter(
                payloadDataManager = get(),
                prefs = get(),
                metadataInteractor = get(),
                metadataDerivation = MetadataDerivation(BitcoinMainNetParams.get()),
                moshi = get(),
                analytics = get()
            )
        }

        factory {
            BackupWalletStartingPresenter()
        }

        factory {
            BackupWalletWordListPresenter(
                backupWalletUtil = get()
            )
        }

        factory {
            BackupWalletUtil(
                payloadDataManager = get(),
                environmentConfig = get()
            )
        }

        factory {
            BackupVerifyPresenter(
                payloadDataManager = get(),
                backupWalletUtil = get(),
                walletStatus = get()
            )
        }

        factory {
            SunriverDeepLinkHelper(
                linkHandler = get()
            )
        }

        factory {
            KycDeepLinkHelper(
                linkHandler = get()
            )
        }

        factory {
            ThePitDeepLinkParser()
        }

        factory { EmailVerificationDeepLinkHelper() }

        factory {
            DeepLinkProcessor(
                linkHandler = get(),
                kycDeepLinkHelper = get(),
                sunriverDeepLinkHelper = get(),
                emailVerifiedLinkHelper = get(),
                thePitDeepLinkParser = get()
            )
        }

        scoped {
            QrScanResultProcessor(
                bitPayDataManager = get()
            )
        }

        factory {
            AccountPresenter(
                privateKeyFactory = get(),
                analytics = get(),
                coincore = get()
            )
        }

        factory {
            ReceiveQrPresenter(
                payloadDataManager = get(),
                qrCodeDataManager = get()
            )
        }

        factory { DeepLinkPersistence(get()) }

        factory {
            DashboardModel(
                initialState = DashboardState(),
                mainScheduler = AndroidSchedulers.mainThread(),
                interactor = get()
            )
        }

        factory {
            DashboardInteractor(
                coincore = get(),
                payloadManager = get(),
                exchangeRates = get(),
                currencyPrefs = get(),
                custodialWalletManager = get(),
                simpleBuyPrefs = get(),
                analytics = get(),
                crashLogger = get(),
                assetOrdering = get()
            )
        }

        scoped {
            AssetDetailsModel(
                initialState = AssetDetailsState(),
                mainScheduler = AndroidSchedulers.mainThread(),
                interactor = get()
            )
        }

        factory {
            AssetDetailsInteractor(
                interestFeatureFlag = get(interestAccountFeatureFlag),
                dashboardPrefs = get(),
                coincore = get()
            )
        }

        factory {
            SimpleBuyInteractor(
                withdrawLocksRepository = get(),
                tierService = get(),
                custodialWalletManager = get(),
                appUtil = get(),
                coincore = get(),
                eligibilityProvider = get()
            )
        }

        factory {
            SimpleBuyModel(
                interactor = get(),
                scheduler = AndroidSchedulers.mainThread(),
                initialState = SimpleBuyState(),
                ratingPrefs = get(),
                prefs = get(),
                gson = get(),
                cardActivators = listOf(
                    EverypayCardActivator(get(), get())
                )
            )
        }

        scoped {
            WithdrawStatePersistence()
        }

        factory {
            WithdrawInteractor(
                assetBalancesRepository = get(),
                custodialWalletManager = get()
            )
        }

        factory {
            WithdrawModel(
                withdrawStatePersistence = get(),
                mainScheduler = AndroidSchedulers.mainThread(),
                withdrawInteractor = get()
            )
        }

        factory {
            CardModel(
                interactor = get(),
                currencyPrefs = get(),
                scheduler = AndroidSchedulers.mainThread(),
                cardActivators = listOf(
                    EverypayCardActivator(get(), get())
                ),
                gson = get(),
                prefs = get()
            )
        }

        factory {
            SimpleBuyFlowNavigator(
                simpleBuyModel = get(),
                tierService = get(),
                currencyPrefs = get(),
                custodialWalletManager = get()
            )
        }

        factory {
            BuySellFlowNavigator(
                simpleBuyModel = get(),
                custodialWalletManager = get(),
                currencyPrefs = get(),
                tierService = get(),
                eligibilityProvider = get()
            )
        }

        scoped {
            val inflateAdapter = SimpleBuyInflateAdapter(
                prefs = get(),
                gson = get()
            )

            SimpleBuySyncFactory(
                custodialWallet = get(),
                localStateAdapter = inflateAdapter
            )
        }

        factory {
            BalanceAnalyticsReporter(
                analytics = get()
            )
        }

        factory {
            ReceivePresenter(
                prefs = get(),
                qrCodeDataManager = get(),
                exchangeRates = get()
            )
        }

        factory {
            SettingsPresenter(
                authDataManager = get(),
                settingsDataManager = get(),
                emailUpdater = get(),
                payloadManager = get(),
                payloadDataManager = get(),
                prefs = get(),
                accessState = get(),
                custodialWalletManager = get(),
                notificationTokenManager = get(),
                exchangeRateDataManager = get(),
                kycStatusHelper = get(),
                pitLinking = get(),
                analytics = get(),
                biometricsController = get()
            )
        }

        factory {
            LockboxLandingPresenter(
                lockboxDataManager = get(),
                walletOptionsDataManager = get()
            )
        }

        factory {
            PinEntryPresenter(
                authDataManager = get(),
                appUtil = get(),
                prefs = get(),
                payloadDataManager = get(),
                stringUtils = get(),
                accessState = get(),
                walletOptionsDataManager = get(),
                environmentSettings = get(),
                prngFixer = get(),
                mobileNoticeRemoteConfig = get(),
                crashLogger = get(),
                analytics = get(),
                config = get(),
                credentialsWiper = get(),
                biometricsController = get()
            )
        }

        scoped {
            PitLinkingImpl(
                nabu = get(),
                nabuToken = get(),
                payloadDataManager = get(),
                ethDataManager = get(),
                bchDataManager = get(),
                xlmDataManager = get()
            )
        }.bind(PitLinking::class)

        factory {
            BitPayDataManager(
                bitPayService = get()
            )
        }

        factory {
            BitPayService(
                environmentConfig = get(),
                retrofit = get(moshiExplorerRetrofit),
                rxBus = get()
            )
        }

        factory {
            PitPermissionsPresenter(
                nabu = get(),
                nabuToken = get(),
                pitLinking = get(),
                analytics = get(),
                prefs = get()
            )
        }

        factory {
            PitVerifyEmailPresenter(
                nabuToken = get(),
                nabu = get(),
                emailSyncUpdater = get()
            )
        }

        factory {
            BackupWalletCompletedPresenter(
                walletStatus = get()
            )
        }

        factory {
            OnboardingPresenter(
                biometricsController = get(),
                accessState = get(),
                settingsDataManager = get()
            )
        }

        factory {
            LauncherPresenter(
                appUtil = get(),
                payloadDataManager = get(),
                prefs = get(),
                deepLinkPersistence = get(),
                accessState = get(),
                settingsDataManager = get(),
                notificationTokenManager = get(),
                envSettings = get(),
                currencyPrefs = get(),
                analytics = get(),
                crashLogger = get(),
                prerequisites = get()
            )
        }

        factory {
            Prerequisites(
                metadataManager = get(),
                settingsDataManager = get(),
                coincore = get(),
                crashLogger = get(),
                simpleBuySync = get(),
                rxBus = get(),
                walletCredentialsUpdater = get()
            )
        }

        factory {
            WalletCredentialsMetadataUpdater(
                payloadDataManager = get(),
                metadataRepository = get()
            )
        }

        factory {
            AirdropCentrePresenter(
                nabuToken = get(),
                nabu = get(),
                crashLogger = get()
            )
        }

        factory(btc) { BtcAccountListAdapter(get()) }.bind(AccountList::class)
        factory(bch) { BchAccountListAdapter(get()) }.bind(AccountList::class)
        factory(eth) { EthAccountListAdapter(get()) }.bind(AccountList::class)
        factory(pax) { PaxAccountListAdapter(get(), get()) }.bind(AccountList::class)
        factory(usdt) { UsdtAccountListAdapter(get(), get()) }.bind(AccountList::class)

        factory {
            BiometricsController(
                applicationContext = get(),
                prefs = get(),
                accessState = get(),
                cryptographyManager = get()
            )
        }.bind(BiometricAuth::class)

        factory {
            CryptographyManagerImpl()
        }.bind(CryptographyManager::class)
    }

    factory {
        FirebaseMobileNoticeRemoteConfig(remoteConfig = get())
    }.bind(MobileNoticeRemoteConfig::class)

    factory {
        SwapTrendingPairsProvider(
            coincore = payloadScope.get(),
            eligibilityProvider = payloadScope.get()
        )
    }.bind(TrendingPairsProvider::class)

    factory {
        OfflineBalanceCall(
            blockExplorer = get()
        )
    }

    factory {
        SwipeToReceivePresenter(
            qrGenerator = get(),
            addressCache = get(),
            offlineBalance = get()
        )
    }

    single {
        LocalOfflineAccountCache(
            prefs = get(),
            ordering = get()
        )
    }.bind(OfflineAccountCache::class)

    factory {
        QrCodeDataManager()
    }

    single {
        PrngHelper(
            context = get(),
            accessState = get()
        )
    }.bind(PrngFixer::class)

    single {
        ConnectionApi(retrofit = get(explorerRetrofit))
    }

    single {
        SSLVerifyUtil(rxBus = get(), connectionApi = get())
    }

    factory {
        SSLVerifyPresenter(
            sslVerifyUtil = get()
        )
    }

    factory { ResourceDefaultLabels(get()) }.bind(DefaultLabels::class)

    factory { DefaultAccountsSorting(get()) }.bind(AccountsSorting::class)

    factory {
        AssetOrderingRemoteConfig(
            config = get(),
            crashLogger = get()
        )
    }.bind(AssetOrdering::class)

    single {
        OverlayDetection(get())
    }

    single {
        AssetResourceFactory(
            resources = get()
        )
    }
}
