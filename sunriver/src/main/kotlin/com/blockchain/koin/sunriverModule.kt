package com.blockchain.koin

import com.blockchain.accounts.AccountList
import com.blockchain.accounts.XlmAsyncAccountListAdapter
import com.blockchain.sunriver.HorizonProxy
import com.blockchain.sunriver.MemoMapper
import com.blockchain.sunriver.XlmDataManager
import com.blockchain.sunriver.XlmSecretAccess
import com.blockchain.sunriver.datamanager.XlmMetaDataInitializer
import org.koin.dsl.bind
import org.koin.dsl.module

val sunriverModule = module {

    scope(payloadScopeQualifier) {

        factory { XlmSecretAccess(get()) }

        scoped { XlmDataManager(
            horizonProxy = get(),
            metaDataInitializer = get(),
            xlmSecretAccess = get(),
            memoMapper = get(),
            xlmFeesFetcher = get(),
            xlmTimeoutFetcher = get(),
            lastTxUpdater = get(),
            eventLogger = get(),
            xlmHorizonUrlFetcher = get(),
            xlmHorizonDefUrl = getProperty("HorizonURL"))
        }

        factory { HorizonProxy() }

        scoped { XlmMetaDataInitializer(get(), get(), get(), get()) }

        factory(xlm) {
            XlmAsyncAccountListAdapter(xlmDataManager = get())
        }.bind(AccountList::class)
    }

    factory { MemoMapper() }
}
