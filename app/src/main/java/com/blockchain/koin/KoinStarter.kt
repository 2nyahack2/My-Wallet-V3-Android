package com.blockchain.koin

import android.app.Application
import com.blockchain.koin.modules.apiInterceptorsModule
import com.blockchain.koin.modules.appProperties
import com.blockchain.koin.modules.applicationModule
import com.blockchain.koin.modules.environmentModule
import com.blockchain.koin.modules.featureFlagsModule
import com.blockchain.koin.modules.keys
import com.blockchain.koin.modules.moshiModule
import com.blockchain.koin.modules.nabuUrlModule
import com.blockchain.koin.modules.serviceModule
import com.blockchain.koin.modules.urls
import com.blockchain.koin.modules.xlmModule
import com.blockchain.lockbox.lockboxModule
import com.blockchain.network.modules.apiModule
import com.blockchain.network.modules.okHttpModule
import com.blockchain.notifications.koin.notificationModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.logger.Level
import org.koin.core.logger.Logger
import org.koin.core.logger.MESSAGE
import piuk.blockchain.android.BuildConfig
import piuk.blockchain.android.campaign.campaignModule
import piuk.blockchain.android.coincore.coincoreModule
import piuk.blockchain.android.ui.activity.activitiesModule
import piuk.blockchain.android.ui.dashboard.announcements.dashboardAnnouncementsModule
import piuk.blockchain.android.ui.kyc.koin.kycUiModule
import piuk.blockchain.android.ui.kyc.koin.kycUiNabuModule
import piuk.blockchain.android.ui.start.startupUiModule
import piuk.blockchain.android.ui.transactionflow.transactionModule
import timber.log.Timber

object KoinStarter {

    @Suppress("ConstantConditionIf")
    @JvmStatic
    fun start(application: Application) {
        stopKoin()
        startKoin {
            if (BuildConfig.LOG_KOIN_STARTUP) TimberLogger() else NullLogger()
            properties(appProperties + keys + urls)
            androidContext(application)
            modules(listOf(
                activitiesModule,
                apiInterceptorsModule,
                apiModule,
                applicationModule,
                campaignModule,
                coincoreModule,
                transactionModule,
                okHttpModule,
                coreModule,
                coreUiModule,
                dashboardAnnouncementsModule,
                environmentModule,
                featureFlagsModule,
                authenticationModule,
                kycUiModule,
                kycUiNabuModule,
                lockboxModule,
                moshiModule,
                nabuModule,
                nabuUrlModule,
                notificationModule,
                serviceModule,
                startupUiModule,
                sunriverModule,
                walletModule,
                xlmModule
            ))
        }
    }
}

private class TimberLogger : Logger() {
    override fun log(level: Level, msg: MESSAGE) {
        when (level) {
            Level.DEBUG -> Timber.d(msg)
            Level.INFO -> Timber.i(msg)
            Level.ERROR -> Timber.e(msg)
            else -> {
            }
        }
    }
}

private class NullLogger : Logger() {
    override fun log(level: Level, msg: MESSAGE) {}
}