package piuk.blockchain.android.util

import android.app.ActivityManager
import android.content.Context

class OSUtil(private val context: Context) {

    fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE)
                as? ActivityManager ?: return false
        for (s in manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.name == s.service.className) {
                return true
            }
        }
        return false
    }
}
