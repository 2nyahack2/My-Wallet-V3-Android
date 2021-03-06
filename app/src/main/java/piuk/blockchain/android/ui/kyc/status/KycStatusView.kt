package piuk.blockchain.android.ui.kyc.status

import androidx.annotation.StringRes
import com.blockchain.nabu.models.responses.nabu.KycTierState
import piuk.blockchain.androidcoreui.ui.base.View

interface KycStatusView : View {

    fun finishPage()

    fun renderUi(kycState: KycTierState)

    fun showProgressDialog()

    fun dismissProgressDialog()

    fun startExchange()

    fun showToast(@StringRes message: Int)

    fun showNotificationsEnabledDialog()
}
