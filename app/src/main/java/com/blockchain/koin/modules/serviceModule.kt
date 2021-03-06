package com.blockchain.koin.modules

import com.blockchain.koin.apiRetrofit
import com.blockchain.koin.everypayRetrofit
import com.blockchain.koin.explorerRetrofit
import info.blockchain.wallet.ApiCode
import info.blockchain.wallet.BlockchainFramework
import info.blockchain.wallet.api.FeeApi
import info.blockchain.wallet.api.FeeEndpoints
import info.blockchain.wallet.api.WalletApi
import info.blockchain.wallet.api.WalletExplorerEndpoints
import info.blockchain.wallet.ethereum.EthAccountApi
import info.blockchain.wallet.payment.Payment
import info.blockchain.wallet.settings.SettingsManager
import org.koin.dsl.bind
import org.koin.dsl.module
import piuk.blockchain.android.everypay.service.EveryPayService
import retrofit2.Retrofit

val serviceModule = module {

    single { SettingsManager(get()) }

    single { get<Retrofit>(explorerRetrofit).create(WalletExplorerEndpoints::class.java) }

    single { get<Retrofit>(apiRetrofit).create(FeeEndpoints::class.java) }

    single { get<Retrofit>(everypayRetrofit).create(EveryPayService::class.java) }

    factory { WalletApi(get(), get()) }

    factory { Payment() }

    factory { FeeApi(get()) }

    factory {
        object : ApiCode {
            override val apiCode: String
                get() = BlockchainFramework.getApiCode()
        }
    }.bind(ApiCode::class)

    factory { EthAccountApi(get()) }
}
