package piuk.blockchain.android.ui.scan

import com.blockchain.notifications.analytics.Analytics
import com.blockchain.notifications.analytics.AnalyticsEvents
import com.karumi.dexter.listener.PermissionDeniedResponse
import com.karumi.dexter.listener.PermissionGrantedResponse
import com.karumi.dexter.listener.single.BasePermissionListener
import org.koin.core.KoinComponent
import org.koin.core.inject

class CameraPermissionListener(
    private val granted: () -> Unit,
    private val denied: () -> Unit = {}
) : BasePermissionListener(), KoinComponent {

    private val analytics: Analytics by inject()

    override fun onPermissionGranted(response: PermissionGrantedResponse?) {
        granted()
        analytics.logEvent(AnalyticsEvents.CameraSystemPermissionApproved)
        super.onPermissionGranted(response)
    }

    override fun onPermissionDenied(response: PermissionDeniedResponse?) {
        denied()
        analytics.logEvent(AnalyticsEvents.CameraSystemPermissionDeclined)
        super.onPermissionDenied(response)
    }
}