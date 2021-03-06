package com.blockchain.koin

import com.blockchain.nabu.Authenticator
import com.blockchain.nabu.CreateNabuToken
import com.blockchain.nabu.NabuToken
import com.blockchain.nabu.NabuUserSync
import com.blockchain.nabu.api.nabu.Nabu
import com.blockchain.nabu.datamanagers.AnalyticsNabuUserReporterImpl
import com.blockchain.nabu.datamanagers.AnalyticsWalletReporter
import com.blockchain.nabu.datamanagers.BalanceProviderImpl
import com.blockchain.nabu.datamanagers.BalancesProvider
import com.blockchain.nabu.datamanagers.CreateNabuTokenAdapter
import com.blockchain.nabu.datamanagers.CustodialWalletManager
import com.blockchain.nabu.datamanagers.EligibilityProvider
import com.blockchain.nabu.datamanagers.ExtraAttributesProvider
import com.blockchain.nabu.datamanagers.ExtraAttributesProviderImpl
import com.blockchain.nabu.datamanagers.NabuAuthenticator
import com.blockchain.nabu.datamanagers.NabuCachedEligibilityProvider
import com.blockchain.nabu.datamanagers.NabuDataManager
import com.blockchain.nabu.datamanagers.NabuDataManagerImpl
import com.blockchain.nabu.datamanagers.NabuDataUserProvider
import com.blockchain.nabu.datamanagers.NabuDataUserProviderNabuDataManagerAdapter
import com.blockchain.nabu.datamanagers.NabuUserReporter
import com.blockchain.nabu.datamanagers.NabuUserSyncUpdateUserWalletInfoWithJWT
import com.blockchain.nabu.datamanagers.TransactionErrorMapper
import com.blockchain.nabu.datamanagers.UniqueAnalyticsNabuUserReporter
import com.blockchain.nabu.datamanagers.UniqueAnalyticsWalletReporter
import com.blockchain.nabu.datamanagers.WalletReporter
import com.blockchain.nabu.datamanagers.custodialwalletimpl.LiveCustodialWalletManager
import com.blockchain.nabu.datamanagers.featureflags.BankLinkingEnabledProvider
import com.blockchain.nabu.datamanagers.featureflags.BankLinkingEnabledProviderImpl
import com.blockchain.nabu.datamanagers.featureflags.FeatureEligibility
import com.blockchain.nabu.datamanagers.featureflags.KycFeatureEligibility
import com.blockchain.nabu.datamanagers.repositories.AssetBalancesRepository
import com.blockchain.nabu.datamanagers.repositories.NabuUserRepository
import com.blockchain.nabu.datamanagers.repositories.QuotesProvider
import com.blockchain.nabu.datamanagers.repositories.WithdrawLocksRepository
import com.blockchain.nabu.datamanagers.repositories.interest.InterestAvailabilityProvider
import com.blockchain.nabu.datamanagers.repositories.interest.InterestAvailabilityProviderImpl
import com.blockchain.nabu.datamanagers.repositories.interest.InterestEligibilityProvider
import com.blockchain.nabu.datamanagers.repositories.interest.InterestEligibilityProviderImpl
import com.blockchain.nabu.datamanagers.repositories.interest.InterestLimitsProvider
import com.blockchain.nabu.datamanagers.repositories.interest.InterestLimitsProviderImpl
import com.blockchain.nabu.datamanagers.repositories.interest.InterestRepository
import com.blockchain.nabu.datamanagers.repositories.serialization.InterestEligibilityMapAdapter
import com.blockchain.nabu.datamanagers.repositories.serialization.InterestLimitsMapAdapter
import com.blockchain.nabu.datamanagers.repositories.swap.CustodialRepository
import com.blockchain.nabu.datamanagers.repositories.swap.SwapActivityProvider
import com.blockchain.nabu.datamanagers.repositories.swap.SwapActivityProviderImpl
import com.blockchain.nabu.datamanagers.repositories.swap.TradingPairsProvider
import com.blockchain.nabu.datamanagers.repositories.swap.TradingPairsProviderImpl
import com.blockchain.nabu.metadata.MetadataRepositoryNabuTokenAdapter
import com.blockchain.nabu.models.responses.nabu.CampaignStateMoshiAdapter
import com.blockchain.nabu.models.responses.nabu.CampaignTransactionStateMoshiAdapter
import com.blockchain.nabu.models.responses.nabu.IsoDateMoshiAdapter
import com.blockchain.nabu.models.responses.nabu.KycStateAdapter
import com.blockchain.nabu.models.responses.nabu.KycTierStateAdapter
import com.blockchain.nabu.models.responses.nabu.UserCampaignStateMoshiAdapter
import com.blockchain.nabu.models.responses.nabu.UserStateAdapter
import com.blockchain.nabu.service.NabuService
import com.blockchain.nabu.service.NabuTierService
import com.blockchain.nabu.service.RetailWalletTokenService
import com.blockchain.nabu.service.TierService
import com.blockchain.nabu.service.TierUpdater
import com.blockchain.nabu.status.KycTiersQueries
import com.blockchain.nabu.stores.NabuSessionTokenStore
import org.koin.dsl.bind
import org.koin.dsl.module
import retrofit2.Retrofit

val nabuModule = module {

    scope(payloadScopeQualifier) {

        factory {
            MetadataRepositoryNabuTokenAdapter(
                metadataRepository = get(),
                createNabuToken = get(),
                metadataManager = get()
            )
        }.bind(NabuToken::class)

        factory {
            NabuDataManagerImpl(
                nabuService = get(),
                retailWalletTokenService = get(),
                nabuTokenStore = get(),
                appVersion = getProperty("app-version"),
                settingsDataManager = get(),
                payloadDataManager = get(),
                prefs = get(),
                walletReporter = get(uniqueId),
                userReporter = get(uniqueUserAnalytics),
                trust = get()
            )
        }.bind(NabuDataManager::class)

        factory {
            LiveCustodialWalletManager(
                nabuService = get(),
                authenticator = get(),
                simpleBuyPrefs = get(),
                paymentAccountMapperMappers = mapOf(
                    "EUR" to get(eur), "GBP" to get(gbp)
                ),
                kycFeatureEligibility = get(),
                assetBalancesRepository = get(),
                interestRepository = get(),
                custodialRepository = get(),
                extraAttributesProvider = get(),
                bankLinkingEnabledProvider = get(),
                transactionErrorMapper = get()
            )
        }.bind(CustodialWalletManager::class)

        factory {
            TransactionErrorMapper()
        }

        factory {
            ExtraAttributesProviderImpl()
        }.bind(ExtraAttributesProvider::class)

        factory {
            BankLinkingEnabledProviderImpl(
                achFF = get(achFeatureFlag),
                globalLinkingFF = get(bankLinkingFeatureFlag)
            )
        }.bind(BankLinkingEnabledProvider::class)

        factory {
            NabuCachedEligibilityProvider(
                nabuService = get(),
                authenticator = get(),
                currencyPrefs = get()
            )
        }.bind(EligibilityProvider::class)

        factory {
            InterestLimitsProviderImpl(
                nabuService = get(),
                authenticator = get(),
                currencyPrefs = get(),
                exchangeRates = get()
            )
        }.bind(InterestLimitsProvider::class)

        factory {
            InterestAvailabilityProviderImpl(
                nabuService = get(),
                authenticator = get()
            )
        }.bind(InterestAvailabilityProvider::class)

        factory {
            InterestEligibilityProviderImpl(
                nabuService = get(),
                authenticator = get()
            )
        }.bind(InterestEligibilityProvider::class)

        factory {
            BalanceProviderImpl(
                nabuService = get(),
                authenticator = get()
            )
        }.bind(BalancesProvider::class)

        factory {
            TradingPairsProviderImpl(
                nabuService = get(),
                authenticator = get()
            )
        }.bind(TradingPairsProvider::class)

        factory {
            SwapActivityProviderImpl(
                nabuService = get(),
                authenticator = get(),
                currencyPrefs = get(),
                exchangeRates = get()
            )
        }.bind(SwapActivityProvider::class)

        factory(uniqueUserAnalytics) {
            UniqueAnalyticsNabuUserReporter(
                nabuUserReporter = get(userAnalytics),
                prefs = get()
            )
        }.bind(NabuUserReporter::class)

        factory(userAnalytics) {
            AnalyticsNabuUserReporterImpl(
                userAnalytics = get()
            )
        }.bind(NabuUserReporter::class)

        factory(uniqueId) {
            UniqueAnalyticsWalletReporter(get(walletAnalytics), prefs = get())
        }.bind(WalletReporter::class)

        factory(walletAnalytics) {
            AnalyticsWalletReporter(userAnalytics = get())
        }.bind(WalletReporter::class)

        factory {
            get<Retrofit>(nabu).create(Nabu::class.java)
        }

        factory { NabuTierService(get(), get()) }
            .bind(TierService::class)
            .bind(TierUpdater::class)

        factory {
            CreateNabuTokenAdapter(get())
        }.bind(CreateNabuToken::class)

        factory { NabuDataUserProviderNabuDataManagerAdapter(get(), get()) }.bind(
            NabuDataUserProvider::class)

        factory { NabuUserSyncUpdateUserWalletInfoWithJWT(get(), get()) }.bind(NabuUserSync::class)

        factory { KycTiersQueries(get(), get()) }

        scoped { KycFeatureEligibility(userRepository = get()) }.bind(FeatureEligibility::class)

        scoped {
            NabuUserRepository(
                nabuDataUserProvider = get())
        }

        scoped {
            AssetBalancesRepository(balancesProvider = get())
        }

        scoped {
            CustodialRepository(
                pairsProvider = get(),
                activityProvider = get()
            )
        }

        scoped {
            InterestRepository(
                interestAvailabilityProvider = get(),
                interestEligibilityProvider = get(),
                interestLimitsProvider = get()
            )
        }

        scoped {
            WithdrawLocksRepository(custodialWalletManager = get())
        }

        factory {
            QuotesProvider(
                nabuService = get(),
                authenticator = get()
            )
        }
    }

    moshiInterceptor(interestLimits) { builder ->
        builder.add(InterestLimitsMapAdapter())
    }

    moshiInterceptor(interestEligibility) { builder ->
        builder.add(InterestEligibilityMapAdapter())
    }

    single { NabuSessionTokenStore() }

    single {
        NabuService(get(nabu))
    }

    single {
        RetailWalletTokenService(
            environmentConfig = get(),
            apiCode = getProperty("api-code"),
            retrofit = get(moshiExplorerRetrofit)
        )
    }

    moshiInterceptor(kyc) { builder ->
        builder
            .add(KycStateAdapter())
            .add(KycTierStateAdapter())
            .add(UserStateAdapter())
            .add(IsoDateMoshiAdapter())
            .add(UserCampaignStateMoshiAdapter())
            .add(CampaignStateMoshiAdapter())
            .add(CampaignTransactionStateMoshiAdapter())
    }
}

val authenticationModule = module {
    scope(payloadScopeQualifier) {
        factory {
            NabuAuthenticator(
                nabuToken = get(),
                nabuDataManager = get(),
                crashLogger = get()
            )
        }.bind(Authenticator::class)
    }
}