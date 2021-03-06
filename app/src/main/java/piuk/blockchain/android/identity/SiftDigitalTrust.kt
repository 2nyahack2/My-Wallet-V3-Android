package piuk.blockchain.android.identity

import android.app.Activity
import com.blockchain.logging.DigitalTrust
import siftscience.android.Sift

class SiftDigitalTrust(
    private val accountId: String,
    private val beaconKey: String
) : DigitalTrust {

    private var currentUserId: String? = null

    fun onActivityCreate(activity: Activity) {
        Sift.Config.Builder()
            .withAccountId(accountId)
            .withBeaconKey(beaconKey)
            .withDisallowLocationCollection(true)
            .build().apply {
                Sift.open(activity, this)
                Sift.collect()
            }
    }

    fun onActivityClose() {
        Sift.close()
    }

    fun onActivityPause() {
        Sift.pause()
    }

    fun onActivityResume(activity: Activity) {
        Sift.resume(activity)
    }

    override fun setUserId(userId: String) {
        if (currentUserId != userId) {
            Sift.setUserId(userId)
            currentUserId = userId
        }
    }

    override fun clearUserId() {
        Sift.unsetUserId()
        currentUserId = null
    }
}