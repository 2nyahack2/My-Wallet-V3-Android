package piuk.blockchain.android.ui.auth

import android.view.View
import kotlinx.android.synthetic.main.dialog_sheet_enroll_biometrics.view.*
import piuk.blockchain.android.R
import piuk.blockchain.android.ui.base.SlidingModalBottomDialog

class BiometricsEnrollmentBottomSheet : SlidingModalBottomDialog() {
    interface Host : SlidingModalBottomDialog.Host {
        fun enrollBiometrics()
        fun cancel()
    }

    override val host: Host by lazy {
        super.host as? Host ?: throw IllegalStateException(
            "Host fragment is not a BiometricsEnrollmentBottomSheet.Host"
        )
    }

    override val layoutResource: Int
        get() = R.layout.dialog_sheet_enroll_biometrics

    override fun initControls(view: View) {
        view.biometric_enable.setOnClickListener {
            host.enrollBiometrics()
        }

        view.biometric_cancel.setOnClickListener {
            dismiss()
            host.cancel()
        }
    }

    companion object {
        fun newInstance(): BiometricsEnrollmentBottomSheet = BiometricsEnrollmentBottomSheet()
    }
}