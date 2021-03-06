package com.blockchain.notifications.koin

import android.app.NotificationManager
import android.content.Context
import com.blockchain.koin.payloadScopeQualifier
import com.blockchain.notifications.NotificationService
import com.blockchain.notifications.NotificationTokenManager
import com.blockchain.notifications.analytics.AnalyticsImpl
import com.blockchain.notifications.analytics.Analytics
import com.blockchain.notifications.analytics.UserAnalytics
import com.blockchain.notifications.analytics.UserAnalyticsImpl
import com.blockchain.notifications.links.DynamicLinkHandler
import com.blockchain.notifications.links.PendingLink
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.dynamiclinks.FirebaseDynamicLinks
import com.google.firebase.iid.FirebaseInstanceId
import org.koin.dsl.bind
import org.koin.dsl.module

val notificationModule = module {

    scope(payloadScopeQualifier) {
        scoped { NotificationTokenManager(
            notificationService = get(),
            payloadManager = get(),
            prefs = get(),
            firebaseInstanceId = get(),
            rxBus = get()
        ) }
    }

    single { FirebaseInstanceId.getInstance() }

    single { FirebaseAnalytics.getInstance(get()) }

    factory { NotificationService(get()) }

    factory { get<Context>().getSystemService(Context.NOTIFICATION_SERVICE) }.bind(NotificationManager::class)

    single { FirebaseDynamicLinks.getInstance() }

    factory { DynamicLinkHandler(get()) }.bind(PendingLink::class)

    factory {
        AnalyticsImpl(
            firebaseAnalytics = get(),
            store = get())
    }.bind(Analytics::class)

    factory { UserAnalyticsImpl(get()) }
        .bind(UserAnalytics::class)
}