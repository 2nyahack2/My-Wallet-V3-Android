package piuk.blockchain.android.ui.shortcuts.receive

import android.content.Intent
import android.graphics.Bitmap
import com.nhaarman.mockito_kotlin.eq
import com.nhaarman.mockito_kotlin.whenever
import io.reactivex.Single
import org.amshove.kluent.mock
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import piuk.blockchain.android.BlockchainTestApplication
import piuk.blockchain.android.scan.QrCodeDataManager
import piuk.blockchain.androidcore.data.payload.PayloadDataManager
import piuk.blockchain.androidcoreui.ui.customviews.ToastCustom
import kotlin.jvm.Throws

@Config(sdk = [23], application = BlockchainTestApplication::class)
@RunWith(RobolectricTestRunner::class)
class ReceiveQrPresenterTest {

    private lateinit var subject: ReceiveQrPresenter
    private val activity: ReceiveQrView = mock()
    private val qrCodeDataManager: QrCodeDataManager = mock()
    private val payloadDataManager: PayloadDataManager = mock()
    private val address = "address"
    private val label = "label"

    @Before
    @Throws(Exception::class)
    fun setUp() {
        subject = ReceiveQrPresenter(payloadDataManager, qrCodeDataManager)
        subject.initView(activity)
        initIntent()
    }

    @Test
    @Throws(Exception::class)
    fun onViewReadyWithIntent() {
        // Arrange
        val intent = Intent()

        intent.putExtra(ReceiveQrActivity.INTENT_EXTRA_ADDRESS, address)
        intent.putExtra(ReceiveQrActivity.INTENT_EXTRA_LABEL, label)
        whenever(activity.pageIntent).thenReturn(intent)
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.RGB_565)
        whenever(qrCodeDataManager.generateQrCode(ArgumentMatchers.anyString(), ArgumentMatchers.anyInt())).thenReturn(
            Single.just(bitmap))
        // Act
        subject.onViewReady()
        // Assert
        verify(activity).pageIntent
        verify(activity).setAddressInfo(address)
        verify(activity).setAddressLabel(label)
        verify(activity).setImageBitmap(bitmap)
        verifyNoMoreInteractions(activity)
    }

    @Test
    @Throws(Exception::class)
    fun onViewReadyWithIntentBitmapException() {
        // Arrange
        whenever(qrCodeDataManager.generateQrCode(
            ArgumentMatchers.anyString(),
            ArgumentMatchers.anyInt())
        ).thenReturn(Single.error(Throwable()))
        // Act
        subject.onViewReady()
        // Assert
        verify(activity).pageIntent
        verify(activity).setAddressInfo(address)
        verify(activity).setAddressLabel(label)

        verify(activity).showToast(ArgumentMatchers.anyInt(), eq(ToastCustom.TYPE_ERROR))
        verify(activity).finishActivity()
        verifyNoMoreInteractions(activity)
    }

    @Test
    @Throws(Exception::class)
    fun onCopyClicked() {
        // Arrange
        val address = "address"
        subject.receiveAddressString = address
        // Act
        subject.onCopyClicked()
        // Assert
        verify(activity).showClipboardWarning(address)
    }

    private fun initIntent() {
        val intent = Intent()
        val address = "address"
        val label = "label"
        intent.putExtra(ReceiveQrActivity.INTENT_EXTRA_ADDRESS, address)
        intent.putExtra(ReceiveQrActivity.INTENT_EXTRA_LABEL, label)
        whenever(activity.pageIntent).thenReturn(intent)
    }
}