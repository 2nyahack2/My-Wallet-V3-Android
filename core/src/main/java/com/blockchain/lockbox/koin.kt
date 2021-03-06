package com.blockchain.lockbox

import com.blockchain.accounts.AccountList
import com.blockchain.koin.lockbox
import com.blockchain.koin.payloadScopeQualifier
import com.blockchain.lockbox.remoteconfig.LockboxRemoteConfig
import com.blockchain.remoteconfig.FeatureFlag
import org.koin.dsl.bind
import org.koin.dsl.module

val lockboxModule = module {

    scope(payloadScopeQualifier) {

        factory { LockboxDataManager(get(), get(lockbox)) }

        factory(lockbox) { get<LockboxDataManager>() }.bind(AccountList::class)
    }

    factory(lockbox) { LockboxRemoteConfig(get()) }
        .bind(FeatureFlag::class)
}
