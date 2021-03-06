package piuk.blockchain.android.ui.kyc.veriffsplash

import com.blockchain.android.testutils.rxInit
import com.blockchain.nabu.NabuToken
import com.blockchain.nabu.datamanagers.NabuDataManager
import com.blockchain.nabu.models.responses.nabu.NabuApiException
import com.blockchain.nabu.models.responses.nabu.SupportedDocuments
import com.blockchain.nabu.models.responses.tokenresponse.NabuOfflineTokenResponse
import com.blockchain.notifications.analytics.Analytics
import com.blockchain.notifications.analytics.AnalyticsEvent
import com.blockchain.veriff.VeriffApplicantAndToken
import com.nhaarman.mockito_kotlin.argumentCaptor
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.never
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import io.reactivex.Observable
import io.reactivex.Single
import junit.framework.Assert.assertEquals
import okhttp3.ResponseBody
import org.junit.Rule
import org.junit.Test
import piuk.blockchain.androidcore.utils.PersistentPrefs
import piuk.blockchain.androidcoreui.ui.base.UiState
import retrofit2.HttpException
import retrofit2.Response

class VeriffSplashPresenterTest {

    private val nabuToken: NabuToken = mock()
    private val nabuDataManager: NabuDataManager = mock()
    private val view: VeriffSplashView = mock()
    private val analytics: Analytics = mock()
    private val prefs: PersistentPrefs = mock()

    private val subject = VeriffSplashPresenter(
        nabuToken = nabuToken,
        nabuDataManager = nabuDataManager,
        analytics = analytics,
        prefs = prefs
    )

    @Suppress("unused")
    @get:Rule
    val initSchedulers = rxInit {
        mainTrampoline()
        ioTrampoline()
    }

    @Test
    fun onViewReady_happyPath_displayDocsView() {
        // Arrange
        setupFetchNabuToken_ok()
        setupFetchRequiredDocs_ok()
        setupFetchApplicantToken_ok()

        setupUiButtonStubs_unclicked()

        subject.initView(view)

        // Act
        subject.onViewReady()

        // Assert
        verify(view).setUiState(UiState.LOADING)

        argumentCaptor<List<SupportedDocuments>>().apply {
            verify(view).supportedDocuments(capture())
            assertEquals(firstValue, SUPPORTED_DOCS)
        }

        verify(view).setUiState(UiState.CONTENT)

        argumentCaptor<AnalyticsEvent>().apply {
            verify(analytics).logEvent(capture())

            assertEquals(allValues.size, 1)
            assertEquals(firstValue.params["result"], "START_KYC")
        }

        verify(view, never()).setUiState(UiState.FAILURE)
    }

    @Test
    fun onViewReady_pre_IDV_fail_displayUnavailableView() {
        // Arrange
        setupFetchNabuToken_ok()
        setupFetchRequiredDocs_ok()
        setupFetchApplicantToken_error_4xx()

        setupUiButtonStubs_unclicked()

        subject.initView(view)

        // Act
        subject.onViewReady()

        // Assert
        verify(view).setUiState(UiState.LOADING)

        argumentCaptor<List<SupportedDocuments>>().apply {
            verify(view).supportedDocuments(capture())

            assertEquals(allValues.size, 1)
            assertEquals(firstValue, SUPPORTED_DOCS)
        }

        verify(view).setUiState(UiState.FAILURE)
        verify(prefs).devicePreIDVCheckFailed = true

        argumentCaptor<AnalyticsEvent>().apply {
            verify(analytics).logEvent(capture())

            assertEquals(allValues.size, 1)
            assertEquals(firstValue.params["result"], "UNAVAILABLE")
        }

        verify(view, never()).setUiState(UiState.CONTENT)
    }

    // Setup:
    private fun setupFetchNabuToken_ok() {
        whenever(nabuToken.fetchNabuToken()).thenReturn(Single.just(TOKEN_RESPONSE))
    }

    private fun setupFetchRequiredDocs_ok() {
        whenever(view.countryCode).thenReturn(COUNTRY_CODE)
        whenever(nabuDataManager.getSupportedDocuments(TOKEN_RESPONSE, COUNTRY_CODE))
            .thenReturn(Single.just(SUPPORTED_DOCS))
    }

    private fun setupFetchApplicantToken_ok() {
        whenever(nabuDataManager.startVeriffSession(TOKEN_RESPONSE))
            .thenReturn(Single.just(APPLICANT_TOKEN))
    }

    private fun setupFetchApplicantToken_error_4xx() {
        val body = Response.error<VeriffApplicantAndToken>(
            406,
            ResponseBody.create(
                null,
                "{\"message\":\"Totes Nope\"}"
            )
        )
        val httpError = NabuApiException.fromResponseBody(HttpException(body))

        whenever(nabuDataManager.startVeriffSession(TOKEN_RESPONSE))
            .thenReturn(Single.error(httpError))
    }

    private fun setupUiButtonStubs_unclicked() {
        whenever(view.nextClick).thenReturn(Observable.never())
        whenever(view.swapClick).thenReturn(Observable.never())
    }

    companion object {
        private const val COUNTRY_CODE = "UK"
        private const val NABU_TOKEN = "TTTT_TOKEN_NNNN"

        private val TOKEN_RESPONSE = NabuOfflineTokenResponse(
            userId = "userId",
            token = NABU_TOKEN
        )

        private val SUPPORTED_DOCS = listOf(
            SupportedDocuments.PASSPORT,
            SupportedDocuments.DRIVING_LICENCE
        )

        private val APPLICANT_TOKEN = VeriffApplicantAndToken(
            applicantId = "WHATEVER",
            token = "ANOTHER_TOKEN"
        )
    }
}
