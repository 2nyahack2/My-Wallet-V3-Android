package com.blockchain.nabu.datamanagers

import com.blockchain.nabu.models.responses.nabu.NabuUser
import com.blockchain.notifications.analytics.UserAnalytics
import com.blockchain.notifications.analytics.UserProperty
import piuk.blockchain.androidcore.utils.PersistentPrefs

interface NabuUserReporter {
    fun reportUserId(userId: String)
    fun reportUser(nabuUser: NabuUser)
}

class AnalyticsNabuUserReporterImpl(
    private val userAnalytics: UserAnalytics
) : NabuUserReporter {
    override fun reportUserId(userId: String) {
        userAnalytics.logUserId(userId)
    }

    override fun reportUser(nabuUser: NabuUser) {
        userAnalytics.logUserProperty(UserProperty(
            UserAnalytics.KYC_LEVEL,
            nabuUser.tierInProgressOrCurrentTier.toString()
        ))
        nabuUser.updatedAt?.let {
            userAnalytics.logUserProperty(UserProperty(UserAnalytics.KYC_UPDATED_DATE, it))
        }
        nabuUser.insertedAt?.let {
            userAnalytics.logUserProperty(UserProperty(UserAnalytics.KYC_CREATION_DATE, it))
        }
    }
}

class UniqueAnalyticsNabuUserReporter(
    private val nabuUserReporter: NabuUserReporter,
    private val prefs: PersistentPrefs
) : NabuUserReporter by nabuUserReporter {
    override fun reportUserId(userId: String) {
        val reportedKey = prefs.getValue(ANALYTICS_REPORTED_NABU_USER_KEY)
        if (reportedKey == null || reportedKey != userId) {
            nabuUserReporter.reportUserId(userId)
            prefs.setValue(ANALYTICS_REPORTED_NABU_USER_KEY, userId)
        }
    }

    companion object {
        private const val ANALYTICS_REPORTED_NABU_USER_KEY = "analytics_reported_nabu_user_key"
    }
}