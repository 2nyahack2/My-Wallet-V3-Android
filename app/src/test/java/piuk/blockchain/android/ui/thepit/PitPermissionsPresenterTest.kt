package piuk.blockchain.android.ui.thepit

import com.blockchain.annotations.CommonCode
import com.blockchain.nabu.models.responses.nabu.NabuUser
import com.blockchain.nabu.models.responses.tokenresponse.NabuOfflineTokenResponse
import com.blockchain.android.testutils.rxInit
import com.blockchain.nabu.NabuToken
import com.blockchain.preferences.ThePitLinkingPrefs
import com.blockchain.nabu.datamanagers.NabuDataManager
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.argumentCaptor
import com.nhaarman.mockito_kotlin.times
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.verifyNoMoreInteractions
import com.nhaarman.mockito_kotlin.whenever
import io.reactivex.Completable
import io.reactivex.Single
import junit.framework.Assert.assertEquals
import org.amshove.kluent.mock
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import piuk.blockchain.android.BuildConfig
import piuk.blockchain.android.thepit.PitLinking

@CommonCode("Also exists in nabu/test/TestHelper.kt")
private val validOfflineToken
    get() = NabuOfflineTokenResponse("userId", "lifetimeToken")

class PitPermissionsPresenterTest {

    @get:Rule
    val initSchedulers = rxInit {
        computationTrampoline()
        mainTrampoline()
    }

    private lateinit var presenter: PitPermissionsPresenter

    private val nabu: NabuDataManager = mock()
    private val nabuToken: NabuToken = mock()
    private val pitLinking: PitLinking = mock()
    private val prefs: ThePitLinkingPrefs = mock()
    private val view = mock<PitPermissionsView>()

    private val nabuUser: NabuUser = mock()

    @Before
    fun setUp() {
        whenever(nabuToken.fetchNabuToken()).thenReturn(Single.just(validOfflineToken))
        whenever(nabu.getUser(validOfflineToken)).thenReturn(Single.just(nabuUser))

        presenter = PitPermissionsPresenter(
            nabu,
            nabuToken,
            pitLinking,
            prefs,
            mock()
        ).also {
            it.initView(view)
            it.onViewReady()
        }
    }

    // wallet to pit linking
    @Test
    fun `w2p - when mail is not verified, view should prompt for verification`() {
        whenever(nabuUser.emailVerified).thenReturn(false)
        whenever(nabuUser.email).thenReturn(EMAIL_ADDRESS)

        presenter.tryToConnectWalletToPit()

        verify(view).showLoading()
        verify(view).promptForEmailVerification(EMAIL_ADDRESS)
        verify(view).hideLoading()

        verifyNoMoreInteractions(view)
        verifyNoMoreInteractions(pitLinking)
    }

    @Test
    fun `w2p - when mail is verified, there are no errors, the view should receive a formatted link - simple addr`() {
        whenever(nabuUser.emailVerified).thenReturn(true)
        whenever(nabuUser.email).thenReturn(EMAIL_ADDRESS)

        whenever(nabu.linkWalletWithMercury(any())).thenReturn(Single.just(LINK_ID))

        presenter.tryToConnectWalletToPit()

        verify(view).showLoading()

        argumentCaptor<String>().apply {
            verify(view).onLinkSuccess(capture())
            assertEquals(FORMATTED_LINK, firstValue)
        }
        verify(view).hideLoading()

        verify(pitLinking).sendWalletAddressToThePit()

        verifyNoMoreInteractions(view)
    }

    @Test
    fun `w2p - when mail is verified, there are no errors, the view should receive a formatted link - extended addr`() {
        // AKA check for URI encoding
        whenever(nabuUser.emailVerified).thenReturn(true)
        whenever(nabuUser.email).thenReturn(EMAIL_ADDRESS_PLUS)

        whenever(nabu.linkWalletWithMercury(any())).thenReturn(Single.just(LINK_ID))

        presenter.tryToConnectWalletToPit()

        verify(view).showLoading()

        argumentCaptor<String>().apply {
            verify(view).onLinkSuccess(capture())
            val s = firstValue
            assertEquals(FORMATTED_LINK_PLUS, s)
        }
        verify(view).hideLoading()

        verify(pitLinking).sendWalletAddressToThePit()

        verifyNoMoreInteractions(view)
    }

    // Wallet to pit linking
    @Test
    fun `w2p - when the call to link fails an error should be reported`() {
        whenever(nabuUser.emailVerified).thenReturn(true)
        whenever(nabuUser.email).thenReturn(EMAIL_ADDRESS)

        whenever(nabu.linkWalletWithMercury(any()))
            .thenReturn(Single.error(Throwable(LINK_ERROR)))

        presenter.tryToConnectWalletToPit()

        verify(view).showLoading()
        verify(view).hideLoading()
        verify(view).onLinkFailed(LINK_ERROR)

        verifyNoMoreInteractions(view)
        verifyNoMoreInteractions(pitLinking)
    }

    @Test
    fun `w2p - when the first call to link fails it should be possible to retry`() {
        whenever(nabuUser.emailVerified).thenReturn(true)
        whenever(nabuUser.email).thenReturn(EMAIL_ADDRESS)

        whenever(nabu.linkWalletWithMercury(any()))
            .thenReturn(Single.error(Throwable(LINK_ERROR)))
            .thenReturn(Single.just(LINK_ID))

        presenter.tryToConnectWalletToPit() // Will fail
        presenter.tryToConnectWalletToPit() // Will succeed

        verify(view, times(2)).showLoading()
        verify(view, times(2)).hideLoading()

        verify(view).onLinkFailed(any())

        argumentCaptor<String>().apply {
            verify(view).onLinkSuccess(capture())
            assertEquals(FORMATTED_LINK, firstValue)
        }

        verify(pitLinking).sendWalletAddressToThePit()

        verifyNoMoreInteractions(view)
        verifyNoMoreInteractions(pitLinking)
    }

    // Email verification check
    @Test
    fun `verified check calls back into view if email is verified`() {
        whenever(nabuUser.emailVerified).thenReturn(true)
        whenever(nabuUser.email).thenReturn(EMAIL_ADDRESS)

        presenter.checkEmailIsVerified()

        verify(view).showEmailVerifiedDialog()

        verifyNoMoreInteractions(view)
        verifyNoMoreInteractions(pitLinking)
    }

    @Test
    fun `no callback occurs if error is not verified and no error is raised`() {
        whenever(nabuUser.emailVerified).thenReturn(false)
        whenever(nabuUser.email).thenReturn(EMAIL_ADDRESS)

        presenter.checkEmailIsVerified()

        verifyNoMoreInteractions(view)
        verifyNoMoreInteractions(pitLinking)
    }

    // PIT to wallet linking
    @Test
    fun `p2w - linking makes correct calls into view on success`() {
        whenever(nabuUser.emailVerified).thenReturn(true)
        whenever(nabuUser.email).thenReturn(EMAIL_ADDRESS)

        whenever(nabu.linkMercuryWithWallet(validOfflineToken, LINK_ID))
            .thenReturn(Completable.complete())

        presenter.tryToConnectPitToWallet(LINK_ID)

        verify(pitLinking).sendWalletAddressToThePit()
        verify(view).showLoading()
        verify(view).hideLoading()
        verify(view).onPitLinked()
        verify(prefs).clearPitToWalletLinkId()

        verifyNoMoreInteractions(view)
        verifyNoMoreInteractions(pitLinking)
        verifyNoMoreInteractions(prefs)
    }

    @Test
    fun `p2w - if email not verified, link id should be persisted and view should prompt for verification`() {
        whenever(nabuUser.emailVerified).thenReturn(false)
        whenever(nabuUser.email).thenReturn(EMAIL_ADDRESS)

        presenter.tryToConnectPitToWallet(LINK_ID)

        verify(view).showLoading()
        verify(view).promptForEmailVerification(EMAIL_ADDRESS)
        verify(view).hideLoading()
        verify(prefs).pitToWalletLinkId = LINK_ID

        verifyNoMoreInteractions(view)
        verifyNoMoreInteractions(pitLinking)
        verifyNoMoreInteractions(prefs)
    }

    @Test
    fun `p2w - errors correctly reported on failure`() {
        whenever(nabuUser.emailVerified).thenReturn(true)
        whenever(nabuUser.email).thenReturn(EMAIL_ADDRESS)

        whenever(nabu.linkMercuryWithWallet(validOfflineToken, LINK_ID))
            .thenReturn(Completable.error(Throwable(LINK_ERROR)))

        presenter.tryToConnectPitToWallet(LINK_ID)

        verify(view).showLoading()
        verify(view).hideLoading()
        verify(view).onLinkFailed(LINK_ERROR)

        verifyNoMoreInteractions(view)
        verifyNoMoreInteractions(pitLinking)
        verifyNoMoreInteractions(prefs)
    }

    companion object {
        private const val LINK_ID = "0200000020"
        private const val EMAIL_ADDRESS = "test@test.com"
        private const val EMAIL_ADDRESS_PLUS = "test+test@test.com"
        private const val FORMATTED_LINK =
            BuildConfig.PIT_LINKING_URL + LINK_ID + "?email=test%40test.com&utm_source=" +
                    "android_wallet&utm_medium=wallet_linking"
        private const val FORMATTED_LINK_PLUS =
            BuildConfig.PIT_LINKING_URL + LINK_ID + "?email=test%2Btest%40test.com&utm_source=" +
                    "android_wallet&utm_medium=wallet_linking"

        private const val LINK_ERROR = "That went well"
    }
}
