package com.blockchain.koin

import info.blockchain.wallet.api.dust.BchDustService
import info.blockchain.wallet.api.dust.DustApi
import info.blockchain.wallet.api.dust.DustService
import info.blockchain.wallet.metadata.MetadataService
import info.blockchain.wallet.metadata.MetadataInteractor
import info.blockchain.wallet.multiaddress.MultiAddressFactory
import info.blockchain.wallet.payload.BalanceManagerBch
import info.blockchain.wallet.payload.BalanceManagerBtc
import info.blockchain.wallet.payload.PayloadManager
import info.blockchain.wallet.payload.PayloadManagerWiper
import info.blockchain.wallet.prices.CurrentPriceApi
import info.blockchain.wallet.prices.PriceApi
import info.blockchain.wallet.prices.PriceEndpoints
import info.blockchain.wallet.prices.toCachedIndicativeFiatPriceService
import org.koin.dsl.bind
import org.koin.dsl.module
import retrofit2.Retrofit

val walletModule = module {

    scope(payloadScopeQualifier) {

        scoped { PayloadManager(get(), get(), get(), get()) }

        factory { MultiAddressFactory(get()) }

        factory { BalanceManagerBtc(get()) }

        factory { BalanceManagerBch(get()) }
    }

    factory { PriceApi(get(), get()) }

    factory {
        MetadataInteractor(
            metadataService = get()
        )
    }

    single { get<Retrofit>(apiRetrofit).create(MetadataService::class.java) }

    single { get<Retrofit>(kotlinApiRetrofit).create(PriceEndpoints::class.java) }

    factory { get<PriceApi>() as CurrentPriceApi }

    factory { get<CurrentPriceApi>().toCachedIndicativeFiatPriceService() }

    factory {
        BchDustService(get<Retrofit>(kotlinApiRetrofit).create(DustApi::class.java),
            get())
    }.bind(DustService::class)

    single {
        object : PayloadManagerWiper {
            override fun wipe() {
                if (!payloadScope.closed) {
                    payloadScope.close()
                }
            }
        }
    }.bind(PayloadManagerWiper::class)
}
