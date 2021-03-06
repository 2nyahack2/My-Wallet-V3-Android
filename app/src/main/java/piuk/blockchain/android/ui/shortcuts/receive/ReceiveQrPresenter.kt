package piuk.blockchain.android.ui.shortcuts.receive

import androidx.annotation.VisibleForTesting
import io.reactivex.rxkotlin.plusAssign
import piuk.blockchain.android.R
import piuk.blockchain.android.scan.QrCodeDataManager
import piuk.blockchain.androidcore.data.payload.PayloadDataManager
import piuk.blockchain.androidcoreui.ui.base.BasePresenter
import piuk.blockchain.androidcoreui.ui.customviews.ToastCustom

internal class ReceiveQrPresenter(
    val payloadDataManager: PayloadDataManager,
    private val qrCodeDataManager: QrCodeDataManager
) : BasePresenter<ReceiveQrView>() {

    @VisibleForTesting
    var receiveAddressString: String? = null

    override fun onViewReady() {
        val intent = view.pageIntent

        if (intent.getStringExtra(ReceiveQrActivity.INTENT_EXTRA_ADDRESS) != null &&
            intent.getStringExtra(ReceiveQrActivity.INTENT_EXTRA_LABEL) != null
        ) {

            // Show QR Code
            receiveAddressString = intent.getStringExtra(ReceiveQrActivity.INTENT_EXTRA_ADDRESS)
            val labelString = intent.getStringExtra(ReceiveQrActivity.INTENT_EXTRA_LABEL) ?: ""

            view.setAddressInfo(receiveAddressString ?: "")
            view.setAddressLabel(labelString)

            compositeDisposable += qrCodeDataManager.generateQrCode(
                "bitcoin:$receiveAddressString",
                DIMENSION_QR_CODE
            ).subscribe(
                { bitmap -> view.setImageBitmap(bitmap) },
                {
                    view.showToast(R.string.shortcut_receive_qr_error, ToastCustom.TYPE_ERROR)
                    view.finishActivity()
                }
            )
        } else {
            view.finishActivity()
        }
    }

    fun onCopyClicked() {
        view.showClipboardWarning(receiveAddressString ?: "")
    }

    companion object {
        private val DIMENSION_QR_CODE = 600
    }
}
