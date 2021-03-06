package com.blockchain.nabu.service

import com.blockchain.koin.bigDecimal
import com.blockchain.koin.nabu
import com.blockchain.nabu.Authenticator
import com.blockchain.network.EnvironmentUrls
import com.blockchain.network.modules.MoshiBuilderInterceptorList
import com.blockchain.network.modules.OkHttpInterceptors
import com.nhaarman.mockito_kotlin.spy
import info.blockchain.balance.CryptoCurrency
import io.fabric8.mockwebserver.DefaultMockServer
import okhttp3.OkHttpClient
import org.koin.dsl.bind
import org.koin.dsl.module

fun apiServerTestModule(server: DefaultMockServer) = module {

    single { OkHttpClient() }

    single { OkHttpInterceptors(emptyList()) }

    single {
        MoshiBuilderInterceptorList(
            listOf(
                get(bigDecimal),
                get(nabu)
            )
        )
    }

    single {
        object : EnvironmentUrls {

            override val explorerUrl: String
                get() = throw NotImplementedError()

            override val apiUrl: String
                get() = server.url("")

            override val everypayHostUrl: String
                get() = throw NotImplementedError()

            override fun websocketUrl(currency: CryptoCurrency): String {
                throw NotImplementedError()
            }
        }
    }.bind(EnvironmentUrls::class)

    single { spy(MockAuthenticator("testToken")) }.bind(Authenticator::class)
}
