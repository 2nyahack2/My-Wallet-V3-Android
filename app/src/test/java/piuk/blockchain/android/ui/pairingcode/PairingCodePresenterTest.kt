package piuk.blockchain.android.ui.pairingcode

import android.graphics.Bitmap
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.verifyNoMoreInteractions
import com.nhaarman.mockito_kotlin.whenever
import io.reactivex.Observable
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import piuk.blockchain.android.R
import piuk.blockchain.android.scan.QrCodeDataManager
import piuk.blockchain.android.util.StringUtils
import piuk.blockchain.androidcore.data.auth.AuthDataManager
import piuk.blockchain.androidcore.data.payload.PayloadDataManager

class PairingCodePresenterTest {

    private lateinit var subject: PairingCodePresenter
    private val mockActivity: PairingCodeView = mock()

    private val mockQrCodeDataManager: QrCodeDataManager = mock()
    private val mockStringUtils: StringUtils = mock()
    private val mockPayloadDataManager: PayloadDataManager =
        mock(defaultAnswer = Mockito.RETURNS_DEEP_STUBS)
    private val mockAuthDataManager: AuthDataManager = mock()

    @Before
    fun setUp() {
        whenever(mockStringUtils.getString(R.string.pairing_code_instruction_1)).thenReturn("")
        subject = PairingCodePresenter(
            mockQrCodeDataManager,
            mockStringUtils,
            mockPayloadDataManager,
            mockAuthDataManager
        )
        subject.initView(mockActivity)
    }

    @Test
    fun generatePairingQr() {
        // Arrange
        val mockBitmap: Bitmap = mock()

        whenever(mockPayloadDataManager.wallet!!.guid).thenReturn("asdf")
        whenever(mockPayloadDataManager.wallet!!.sharedKey).thenReturn("ghjk")
        whenever(mockPayloadDataManager.tempPassword).thenReturn("zxcv")
        whenever(
            mockQrCodeDataManager.generatePairingCode(
                any(),
                any(),
                any(),
                any(),
                any()
            )
        ).thenReturn(Observable.just(mockBitmap))

        val body = ResponseBody.create(("application/text").toMediaTypeOrNull(), "asdasdasd")
        whenever(mockAuthDataManager.getPairingEncryptionPassword(any()))
            .thenReturn(Observable.just(body))
        // Act
        subject.generatePairingQr()
        // Assert
        verify(mockAuthDataManager).getPairingEncryptionPassword(any())
        verify(mockQrCodeDataManager).generatePairingCode(any(), any(), any(), any(), any())
        verify(mockActivity).showProgressSpinner()
        verify(mockActivity).onQrLoaded(mockBitmap)
        verify(mockActivity).hideProgressSpinner()
        verifyNoMoreInteractions(mockActivity)
    }
}
