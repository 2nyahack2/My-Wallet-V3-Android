package piuk.blockchain.android.data.connectivity

import android.content.Context
import android.net.ConnectivityManager

object ConnectivityStatus {
    fun hasConnectivity(context: Context?): Boolean {
        val manager = context?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val info = manager?.activeNetworkInfo
        return info != null && info.isConnected
    }
}