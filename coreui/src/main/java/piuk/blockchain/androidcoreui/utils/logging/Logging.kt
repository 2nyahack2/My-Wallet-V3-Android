package piuk.blockchain.androidcoreui.utils.logging

import android.content.Context
import android.os.Bundle
import com.blockchain.logging.CustomEventBuilder
import com.blockchain.logging.EventLogger
import com.google.firebase.analytics.FirebaseAnalytics
import piuk.blockchain.androidcoreui.BuildConfig

@Deprecated("Use com.blockchain.notifications.analytics.Analytics instead")
class InjectableLogging(context: Context) : EventLogger {
    private var analytics: FirebaseAnalytics = FirebaseAnalytics.getInstance(context)

    override fun logEvent(customEventBuilder: CustomEventBuilder) {
        val b = Bundle()
        customEventBuilder.build { key, value ->
            b.putString(key, value)
        }
        analytics.logEvent(customEventBuilder.eventName, b)
    }
}

@Deprecated("Use com.blockchain.notifications.analytics.Analytics instead")
object Logging {
    private val shouldLog = BuildConfig.USE_CRASHLYTICS
    private lateinit var analytics: FirebaseAnalytics

    fun init(context: Context) {
        analytics = FirebaseAnalytics.getInstance(context)
    }

    fun logEvent(event: LoggingEvent) {
        if (shouldLog) {
            val b = Bundle()
            for ((t, u) in event.params) {
                when (u) {
                    is String -> b.putString(t, u)
                    is Int -> b.putInt(t, u)
                    is Boolean -> b.putBoolean(t, u)
                }
            }

            analytics.logEvent(event.identifier, b)
        }
    }

    fun logSignUp(success: Boolean) {
        if (shouldLog) {
            val b = Bundle()
            b.putString(FirebaseAnalytics.Param.METHOD, success.toString())
            analytics.logEvent(FirebaseAnalytics.Event.SIGN_UP, b)
        }
    }

    fun logContentView(screen: String) {
        if (shouldLog) {
            val b = Bundle()
            b.putString(FirebaseAnalytics.Param.ITEMS, screen)
            analytics.logEvent(FirebaseAnalytics.Event.VIEW_ITEM, b)
        }
    }

    fun logLogin(success: Boolean) {
        if (shouldLog) {
            val b = Bundle()
            b.putString(FirebaseAnalytics.Param.METHOD, success.toString())
            analytics.logEvent(FirebaseAnalytics.Event.LOGIN, b)
        }
    }

    fun logShare(share: String) {
        if (shouldLog) {
            val b = Bundle()
            b.putString(FirebaseAnalytics.Param.METHOD, share)
            analytics.logEvent(FirebaseAnalytics.Event.SHARE, b)
        }
    }
}

class LoggingEvent(val identifier: String, val params: Map<String, Any>)