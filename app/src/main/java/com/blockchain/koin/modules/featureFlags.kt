package com.blockchain.koin.modules

import com.blockchain.koin.achFeatureFlag
import com.blockchain.koin.bankLinkingFeatureFlag
import com.blockchain.koin.coinifyFeatureFlag
import com.blockchain.koin.coinifyUsersToKyc
import com.blockchain.koin.dgldFeatureFlag
import com.blockchain.koin.interestAccountFeatureFlag
import com.blockchain.koin.smsVerifFeatureFlag
import com.blockchain.koin.sunriver
import com.blockchain.remoteconfig.RemoteConfig
import com.blockchain.remoteconfig.featureFlag
import org.koin.dsl.module

val featureFlagsModule = module {
    factory(coinifyUsersToKyc) {
        get<RemoteConfig>().featureFlag("android_notify_coinify_users_to_kyc")
    }

    factory(coinifyFeatureFlag) {
        get<RemoteConfig>().featureFlag("coinify_enabled")
    }

    factory(smsVerifFeatureFlag) {
        get<RemoteConfig>().featureFlag("android_sms_verification")
    }

    factory(sunriver) {
        get<RemoteConfig>().featureFlag("android_sunriver_airdrop_enabled")
    }

    factory(interestAccountFeatureFlag) {
        get<RemoteConfig>().featureFlag("interest_account_enabled")
    }

    factory(dgldFeatureFlag) {
        get<RemoteConfig>().featureFlag("wdgld_enabled")
    }

    factory(achFeatureFlag) {
        get<RemoteConfig>().featureFlag("ach_enabled")
    }

    factory(bankLinkingFeatureFlag) {
        get<RemoteConfig>().featureFlag("bank_linking_enabled")
    }
}