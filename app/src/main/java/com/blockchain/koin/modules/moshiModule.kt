package com.blockchain.koin.modules

import com.blockchain.koin.bigDecimal
import com.blockchain.koin.bigInteger
import com.blockchain.koin.interestEligibility
import com.blockchain.koin.interestLimits
import com.blockchain.koin.kyc
import com.blockchain.network.modules.MoshiBuilderInterceptorList
import org.koin.dsl.module

val moshiModule = module {

    single {
        MoshiBuilderInterceptorList(
            listOf(
                get(bigDecimal),
                get(bigInteger),
                get(kyc),
                get(interestLimits),
                get(interestEligibility)
            )
        )
    }
}
