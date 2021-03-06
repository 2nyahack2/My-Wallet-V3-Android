package piuk.blockchain.android.ui.createwallet

import androidx.annotation.StringRes
import piuk.blockchain.androidcoreui.ui.base.View

interface CreateWalletView : View {

    fun setEntropyStrength(score: Int)

    fun setEntropyLevel(level: Int)

    fun showError(@StringRes message: Int)

    fun warnWeakPassword(email: String, password: String)

    fun startPinEntryActivity()

    fun showProgressDialog(message: Int)

    fun dismissProgressDialog()

    fun getDefaultAccountName(): String
}
