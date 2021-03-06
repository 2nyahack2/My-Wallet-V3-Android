package piuk.blockchain.android.data.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest

import io.reactivex.Completable
import io.reactivex.android.schedulers.AndroidSchedulers
import piuk.blockchain.androidcore.data.events.ActionEvent
import piuk.blockchain.androidcore.data.events.SpottyNetworkConnectionEvent
import piuk.blockchain.androidcore.data.rxjava.RxBus
import piuk.blockchain.androidcore.utils.extensions.emptySubscribe

internal class ConnectionStateMonitor(
    private val context: Context,
    private val rxBus: RxBus
) :
    ConnectivityManager.NetworkCallback() {

    private val networkRequest: NetworkRequest = NetworkRequest.Builder()
        .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
        .build()

    private var lastBroadcastTime: Long = 0

    fun enable() {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        connectivityManager.registerNetworkCallback(networkRequest, this)
    }

    override fun onAvailable(network: Network) {
        // Sends max of one broadcast every 30s if network connection is spotty
        if (System.currentTimeMillis() - lastBroadcastTime > COOL_DOWN_INTERVAL) {
            broadcastOnMainThread().emptySubscribe()
            lastBroadcastTime = System.currentTimeMillis()
        }
    }

    private fun broadcastOnMainThread(): Completable {
        return Completable.fromAction {
            rxBus.emitEvent(ActionEvent::class.java, SpottyNetworkConnectionEvent())
        }
        .subscribeOn(AndroidSchedulers.mainThread())
    }

    companion object {
        private const val COOL_DOWN_INTERVAL = 1000 * 30L
    }
}
