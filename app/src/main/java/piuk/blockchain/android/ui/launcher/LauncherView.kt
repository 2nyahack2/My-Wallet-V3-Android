package piuk.blockchain.android.ui.launcher

import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import piuk.blockchain.androidcoreui.ui.base.View
import piuk.blockchain.androidcoreui.ui.customviews.ToastCustom

interface LauncherView : View {

    fun getPageIntent(): Intent

    fun onNoGuid()

    fun onRequestPin()

    fun onCorruptPayload()

    fun onRequestUpgrade()

    fun onStartMainActivity(uri: Uri?, launchBuySellIntro: Boolean = false)

    fun launchBuySellIntro()

    fun onReEnterPassword()

    fun showToast(@StringRes message: Int, @ToastCustom.ToastType toastType: String)

    fun showMetadataNodeFailure()

    fun showSecondPasswordDialog()

    fun updateProgressVisibility(show: Boolean)
}
