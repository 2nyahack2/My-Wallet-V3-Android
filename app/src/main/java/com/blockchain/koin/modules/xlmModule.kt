package com.blockchain.koin.modules

import com.blockchain.koin.payloadScopeQualifier
import com.blockchain.sunriver.XlmFeesFetcher
import org.koin.dsl.bind
import org.koin.dsl.module
import piuk.blockchain.android.sunriver.XlmFeesFetcherAdapter

val xlmModule = module {
    scope(payloadScopeQualifier) {
        scoped {
            XlmFeesFetcherAdapter(get())
        }.bind(XlmFeesFetcher::class)
    }
}
