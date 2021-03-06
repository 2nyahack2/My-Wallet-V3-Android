package com.blockchain.koin.modules

import android.os.Build
import com.blockchain.network.modules.OkHttpInterceptors
import com.facebook.stetho.okhttp3.StethoInterceptor
import org.koin.dsl.module
import piuk.blockchain.android.BuildConfig
import piuk.blockchain.androidcore.data.api.EnvironmentConfig
import piuk.blockchain.androidcore.data.api.interceptors.ApiInterceptor
import piuk.blockchain.androidcore.data.api.interceptors.DeviceIdInterceptor
import piuk.blockchain.androidcore.data.api.interceptors.UserAgentInterceptor

val apiInterceptorsModule = module {

    single {
        val env: EnvironmentConfig = get()
        val versionName = BuildConfig.VERSION_NAME.removeSuffix(BuildConfig.VERSION_NAME_SUFFIX)
        OkHttpInterceptors(
            if (env.isRunningInDebugMode()) {
                listOf(
                    // Stetho for debugging network ops via Chrome
                    StethoInterceptor(),
                    // Add logging for debugging purposes
                    ApiInterceptor(),
                    // Add header in all requests
                    UserAgentInterceptor(versionName, Build.VERSION.RELEASE),
                    DeviceIdInterceptor(get(), get())
                )
            } else {
                listOf(
                    // Add header in all requests
                    UserAgentInterceptor(versionName, Build.VERSION.RELEASE),
                    DeviceIdInterceptor(get(), get())
                )
            })
    }
}
