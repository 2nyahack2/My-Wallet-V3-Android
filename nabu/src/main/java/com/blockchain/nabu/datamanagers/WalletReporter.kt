package com.blockchain.nabu.datamanagers

import com.blockchain.notifications.analytics.UserAnalytics
import com.blockchain.notifications.analytics.UserProperty
import info.blockchain.wallet.api.data.Settings
import org.spongycastle.util.encoders.Hex
import piuk.blockchain.androidcore.utils.PersistentPrefs
import java.security.MessageDigest

interface WalletReporter {
    fun reportWalletGuid(walletGuid: String)
    fun reportUserSettings(settings: Settings)
}

class AnalyticsWalletReporter(private val userAnalytics: UserAnalytics) : WalletReporter {
    override fun reportWalletGuid(walletGuid: String) {
        val walletId = String(
            Hex.encode(
                MessageDigest.getInstance("SHA-256")
                    .digest(
                        walletGuid.toByteArray(charset("UTF-8"))
                    )
            )
        ).take(UserProperty.MAX_VALUE_LEN)
        userAnalytics.logUserProperty(UserProperty(UserAnalytics.WALLET_ID, walletId))
    }

    override fun reportUserSettings(settings: Settings) {
        userAnalytics.logUserProperty(
            UserProperty(
                UserAnalytics.EMAIL_VERIFIED,
                settings.isEmailVerified.toString()
            )
        )

        userAnalytics.logUserProperty(
            UserProperty(
                UserAnalytics.TWOFA_ENABLED,
                (settings.authType != Settings.AUTH_TYPE_OFF).toString()
            )
        )
    }
}

class UniqueAnalyticsWalletReporter(
    private val walletReporter: WalletReporter,
    private val prefs: PersistentPrefs
) : WalletReporter by walletReporter {
    override fun reportWalletGuid(walletGuid: String) {
        val reportedKey = prefs.getValue(ANALYTICS_REPORTED_WALLET_KEY)?.take(UserProperty.MAX_VALUE_LEN)
        if (reportedKey == null || reportedKey != walletGuid) {
            walletReporter.reportWalletGuid(UserAnalytics.WALLET_ID)
            prefs.setValue(ANALYTICS_REPORTED_WALLET_KEY, walletGuid)
        }
    }

    override fun reportUserSettings(settings: Settings) {
        walletReporter.reportUserSettings(settings)
    }

    companion object {
        private const val ANALYTICS_REPORTED_WALLET_KEY = "analytics_reported_wallet_key"
    }
}