package com.blockchain.network.modules

import com.blockchain.koin.apiRetrofit
import com.blockchain.koin.bigDecimal
import com.blockchain.koin.bigInteger
import com.blockchain.koin.everypayRetrofit
import com.blockchain.koin.explorerRetrofit
import com.blockchain.koin.kotlinApiRetrofit
import com.blockchain.koin.moshiExplorerRetrofit
import com.blockchain.koin.moshiInterceptor
import com.blockchain.koin.nabu
import com.blockchain.network.EnvironmentUrls
import com.blockchain.serialization.BigDecimalAdaptor
import com.blockchain.serialization.BigIntegerAdapter
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.squareup.moshi.Moshi
import io.reactivex.schedulers.Schedulers
import okhttp3.CertificatePinner
import okhttp3.Interceptor
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.jackson.JacksonConverterFactory
import retrofit2.converter.moshi.MoshiConverterFactory

class OkHttpInterceptors(val list: List<Interceptor>) : List<Interceptor> by list

val apiModule = module {

    moshiInterceptor(bigDecimal) { builder ->
        builder.add(BigDecimalAdaptor())
    }

    moshiInterceptor(bigInteger) { builder ->
        builder.add(BigIntegerAdapter())
    }

    single { JacksonConverterFactory.create(ObjectMapper().registerKotlinModule()) }

    single { RxJava2CallAdapterFactory.createWithScheduler(Schedulers.io()) }

    single {
        CertificatePinner.Builder()
            .add("api.blockchain.info", "sha256/Z87j23nY+/WSTtsgE/O4ZcDVhevBohFPgPMU6rV2iSw=")
            .add("blockchain.info", "sha256/Z87j23nY+/WSTtsgE/O4ZcDVhevBohFPgPMU6rV2iSw=")
            .add("blockchain.com", "sha256/Z87j23nY+/WSTtsgE/O4ZcDVhevBohFPgPMU6rV2iSw=")
            .build()
    }

    single {
        Moshi.Builder()
            .also {
                get<MoshiBuilderInterceptorList>()
                    .forEach { interceptor -> interceptor.intercept(it) }
            }
            .build()
    }

    single {
        MoshiConverterFactory.create(get())
    }

    /**
     * This instance converts to Kotlin data classes ONLY; it will break if used to parse data models
     * written with Java + Jackson.
     */

    /**
     * This instance converts to Kotlin data classes ONLY; it will break if used to parse data models
     * written with Java + Jackson.
     */
    single(moshiExplorerRetrofit) {
        Retrofit.Builder()
            .baseUrl(get<EnvironmentUrls>().explorerUrl)
            .client(get())
            .addConverterFactory(get<MoshiConverterFactory>())
            .addCallAdapterFactory(get<RxJava2CallAdapterFactory>())
            .build()
    }

    single(kotlinApiRetrofit) {
        Retrofit.Builder()
            .baseUrl(get<EnvironmentUrls>().apiUrl)
            .client(get())
            .addConverterFactory(get<MoshiConverterFactory>())
            .addCallAdapterFactory(get<RxJava2CallAdapterFactory>())
            .build()
    }

    single(nabu) {
        Retrofit.Builder()
            .baseUrl(get<EnvironmentUrls>().nabuApi)
            .client(get())
            .addConverterFactory(get<MoshiConverterFactory>())
            .addCallAdapterFactory(get<RxJava2CallAdapterFactory>())
            .build()
    }

    single(apiRetrofit) {
        Retrofit.Builder()
            .baseUrl(get<EnvironmentUrls>().apiUrl)
            .client(get())
            .addConverterFactory(get<JacksonConverterFactory>())
            .addCallAdapterFactory(get<RxJava2CallAdapterFactory>())
            .build()
    }

    single(explorerRetrofit) {
        Retrofit.Builder()
            .baseUrl(get<EnvironmentUrls>().explorerUrl)
            .client(get())
            .addConverterFactory(get<JacksonConverterFactory>())
            .addCallAdapterFactory(get<RxJava2CallAdapterFactory>())
            .build()
    }

    single(everypayRetrofit) {
        Retrofit.Builder()
            .baseUrl(get<EnvironmentUrls>().everypayHostUrl)
            .client(get())
            .addConverterFactory(get<MoshiConverterFactory>())
            .addCallAdapterFactory(get<RxJava2CallAdapterFactory>())
            .build()
    }
}