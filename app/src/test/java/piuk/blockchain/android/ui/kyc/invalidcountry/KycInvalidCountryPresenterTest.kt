package piuk.blockchain.android.ui.kyc.invalidcountry

import com.blockchain.android.testutils.rxInit
import com.blockchain.metadata.MetadataRepository
import com.blockchain.nabu.datamanagers.NabuDataManager
import com.blockchain.nabu.metadata.NabuCredentialsMetadata
import com.blockchain.nabu.models.responses.tokenresponse.NabuOfflineTokenResponse
import com.nhaarman.mockito_kotlin.eq
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import io.reactivex.Completable
import io.reactivex.Single
import org.amshove.kluent.any
import org.amshove.kluent.mock
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import piuk.blockchain.android.ui.kyc.countryselection.util.CountryDisplayModel

class KycInvalidCountryPresenterTest {

    private lateinit var subject: KycInvalidCountryPresenter
    private val nabuDataManager: NabuDataManager = mock()
    private val metadataRepo: MetadataRepository = mock()
    private val view: KycInvalidCountryView = mock()

    @get:Rule
    val rxSchedulers = rxInit {
        mainTrampoline()
        ioTrampoline()
    }

    @Before
    fun setUp() {
        subject = KycInvalidCountryPresenter(nabuDataManager, metadataRepo)
        subject.initView(view)
    }

    @Test
    fun `on no thanks clicked request successful`() {
        // Arrange
        givenSuccessfulUserCreation()
        givenSuccessfulRecordCountryRequest()
        givenViewReturnsDisplayModel()
        // Act
        subject.onNoThanks()
        // Assert
        verify(nabuDataManager).recordCountrySelection(any(), any(), any(), eq(null), eq(false))
        verify(view).showProgressDialog()
        verify(view).dismissProgressDialog()
        verify(view).finishPage()
    }

    @Test
    fun `on notify me clicked request successful`() {
        // Arrange
        givenSuccessfulUserCreation()
        givenSuccessfulRecordCountryRequest()
        givenViewReturnsDisplayModel()
        // Act
        subject.onNotifyMe()
        // Assert
        verify(nabuDataManager).recordCountrySelection(any(), any(), any(), eq(null), eq(true))
        verify(view).showProgressDialog()
        verify(view).dismissProgressDialog()
        verify(view).finishPage()
    }

    @Test
    fun `on no thanks clicked request fails but exception swallowed`() {
        // Arrange
        givenSuccessfulUserCreation()
        whenever(nabuDataManager.recordCountrySelection(any(), any(), any(), eq(null), any()))
            .thenReturn(Completable.error { Throwable() })
        givenSuccessfulRecordCountryRequest()
        givenViewReturnsDisplayModel()
        // Act
        subject.onNoThanks()
        // Assert
        verify(view).showProgressDialog()
        verify(view).dismissProgressDialog()
        verify(view).finishPage()
    }

    private fun <T> any(type: Class<T>): T = Mockito.any<T>(type)

    private fun givenSuccessfulUserCreation() {
        val jwt = "JWT"
        whenever(nabuDataManager.requestJwt()).thenReturn(Single.just(jwt))
        val offlineToken = NabuOfflineTokenResponse("", "")
        whenever(nabuDataManager.getAuthToken(jwt))
            .thenReturn(Single.just(offlineToken))
        whenever(
            metadataRepo.saveMetadata(
                any(),
                eq(NabuCredentialsMetadata::class.java),
                eq(NabuCredentialsMetadata.USER_CREDENTIALS_METADATA_NODE))
        ).thenReturn(Completable.complete())
    }

    private fun givenSuccessfulRecordCountryRequest() {
        whenever(nabuDataManager.recordCountrySelection(any(), any(), any(), eq(null), any()))
            .thenReturn(Completable.complete())
    }

    private fun givenViewReturnsDisplayModel() {
        whenever(view.displayModel).thenReturn(
            CountryDisplayModel(
                name = "Great Britain",
                countryCode = "GB",
                state = null,
                flag = null,
                isState = false
            )
        )
    }
}