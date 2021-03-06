@file:Suppress("USELESS_CAST")

package piuk.blockchain.android.ui.kyc.koin

import com.blockchain.koin.payloadScopeQualifier
import com.blockchain.nabu.CurrentTier
import com.blockchain.nabu.EthEligibility
import org.koin.dsl.bind
import org.koin.dsl.module
import piuk.blockchain.android.ui.kyc.address.CurrentTierAdapter
import piuk.blockchain.android.ui.kyc.address.EligibilityForFreeEthAdapter
import piuk.blockchain.android.ui.kyc.address.KycHomeAddressPresenter
import piuk.blockchain.android.ui.kyc.address.Tier2Decision
import piuk.blockchain.android.ui.kyc.address.Tier2DecisionAdapter
import piuk.blockchain.android.ui.kyc.countryselection.KycCountrySelectionPresenter
import piuk.blockchain.android.ui.kyc.email.entry.KycEmailEntryPresenter
import piuk.blockchain.android.ui.kyc.email.validation.KycEmailValidationPresenter
import piuk.blockchain.android.ui.kyc.invalidcountry.KycInvalidCountryPresenter
import piuk.blockchain.android.ui.kyc.mobile.entry.KycMobileEntryPresenter
import piuk.blockchain.android.ui.kyc.mobile.validation.KycMobileValidationPresenter
import piuk.blockchain.android.ui.kyc.navhost.KycNavHostPresenter
import piuk.blockchain.android.ui.kyc.navhost.KycStarter
import piuk.blockchain.android.ui.kyc.navhost.StartKyc
import piuk.blockchain.android.ui.kyc.profile.KycProfilePresenter
import piuk.blockchain.android.ui.kyc.reentry.KycNavigator
import piuk.blockchain.android.ui.kyc.reentry.ReentryDecision
import piuk.blockchain.android.ui.kyc.reentry.ReentryDecisionKycNavigator
import piuk.blockchain.android.ui.kyc.reentry.TiersReentryDecision
import piuk.blockchain.android.ui.kyc.splash.KycSplashPresenter
import piuk.blockchain.android.ui.kyc.status.KycStatusPresenter
import piuk.blockchain.android.ui.kyc.tiersplash.KycTierSplashPresenter
import piuk.blockchain.android.ui.kyc.veriffsplash.VeriffSplashPresenter

val kycUiModule = module {

    factory { KycStarter() as StartKyc }

    factory { TiersReentryDecision() as ReentryDecision }

    scope(payloadScopeQualifier) {

        factory {
            ReentryDecisionKycNavigator(
                token = get(),
                dataManager = get(),
                reentryDecision = get()
            )
        }.bind(KycNavigator::class)

        factory {
            KycTierSplashPresenter(
                tierUpdater = get(),
                tierService = get(),
                kycNavigator = get()
            )
        }

        factory {
            KycSplashPresenter(
                nabuToken = get(),
                kycNavigator = get()
            )
        }

        factory { KycCountrySelectionPresenter(nabuDataManager = get()) }

        factory {
            KycProfilePresenter(nabuToken = get(),
                nabuDataManager = get(),
                metadataRepository = get(),
                stringUtils = get())
        }

        factory {
            KycHomeAddressPresenter(
                nabuToken = get(),
                nabuDataManager = get(),
                tier2Decision = get(),
                phoneVerificationQuery = get()
            )
        }

        factory { KycMobileEntryPresenter(phoneNumberUpdater = get(), nabuUserSync = get()) }

        factory {
            KycMobileValidationPresenter(
                nabuUserSync = get(),
                phoneNumberUpdater = get(),
                analytics = get()
            )
        }

        factory { KycEmailEntryPresenter(get()) }

        factory { KycEmailValidationPresenter(get(), get()) }

        factory {
            VeriffSplashPresenter(
                nabuToken = get(),
                nabuDataManager = get(),
                analytics = get(),
                prefs = get()
            )
        }

        factory { KycStatusPresenter(get(), get(), get()) }

        factory {
            KycNavHostPresenter(
                nabuToken = get(),
                nabuDataManager = get(),
                sunriverCampaign = get(),
                reentryDecision = get(),
                tierUpdater = get(),
                kycNavigator = get()
            )
        }

        factory { KycInvalidCountryPresenter(get(), get()) }
    }
}

val kycUiNabuModule = module {

    scope(payloadScopeQualifier) {

        factory {
            Tier2DecisionAdapter(get(), get()) as Tier2Decision
        }

        factory {
            CurrentTierAdapter(get(), get()) as CurrentTier
        }

        factory {
            EligibilityForFreeEthAdapter(
                nabuToken = get(),
                nabuDataManager = get()
            )
        }.bind(EthEligibility::class)
    }
}
