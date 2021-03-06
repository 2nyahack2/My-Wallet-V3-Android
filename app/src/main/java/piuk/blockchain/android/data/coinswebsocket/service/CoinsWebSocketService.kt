package piuk.blockchain.android.data.coinswebsocket.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.blockchain.koin.scopedInject
import com.blockchain.notifications.NotificationsUtil
import com.blockchain.notifications.analytics.Analytics
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import org.koin.core.KoinComponent
import org.koin.core.inject
import piuk.blockchain.android.R
import piuk.blockchain.android.data.coinswebsocket.strategy.CoinsWebSocketStrategy
import piuk.blockchain.android.ui.home.MainActivity
import piuk.blockchain.android.util.lifecycle.AppState
import piuk.blockchain.android.util.lifecycle.LifecycleInterestedComponent
import piuk.blockchain.androidcore.data.events.ActionEvent
import piuk.blockchain.androidcore.data.rxjava.RxBus
import piuk.blockchain.androidcoreui.ui.customviews.ToastCustom

class CoinsWebSocketService(private val applicationContext: Context) : MessagesSocketHandler, KoinComponent {

    private val compositeDisposable = CompositeDisposable()
    private val notificationManager: NotificationManager by inject()
    private val coinsWebSocketStrategy: CoinsWebSocketStrategy by scopedInject()
    private val lifecycleInterestedComponent: LifecycleInterestedComponent by inject()
    private val rxBus: RxBus by inject()
    private val analytics: Analytics by inject()

    fun start() {
        compositeDisposable.clear()
        coinsWebSocketStrategy.close()
        coinsWebSocketStrategy.setMessagesHandler(this)
        coinsWebSocketStrategy.open()
        compositeDisposable += lifecycleInterestedComponent.appStateUpdated.subscribe {
            if (it == AppState.FOREGROUNDED) {
                coinsWebSocketStrategy.open()
            } else {
                coinsWebSocketStrategy.close()
            }
        }
    }

    override fun showToast(message: Int) {
        ToastCustom.makeText(
            applicationContext,
            applicationContext.getString(message),
            ToastCustom.LENGTH_SHORT,
            ToastCustom.TYPE_GENERAL)
    }

    override fun triggerNotification(title: String, marquee: String, text: String) {
        val notifyIntent = Intent(applicationContext, MainActivity::class.java)
        notifyIntent.putExtra(NotificationsUtil.INTENT_FROM_NOTIFICATION, true)

        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            notifyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT)

        NotificationsUtil(applicationContext, notificationManager, analytics).triggerNotification(
            title,
            marquee,
            text,
            R.mipmap.ic_launcher,
            pendingIntent,
            1000)
    }

    override fun sendBroadcast(event: ActionEvent) {
        rxBus.emitEvent(ActionEvent::class.java, event)
    }

    fun release() {
        coinsWebSocketStrategy.close()
        compositeDisposable.clear()
    }
}