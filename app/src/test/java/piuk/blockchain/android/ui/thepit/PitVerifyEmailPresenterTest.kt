package piuk.blockchain.android.ui.thepit

import com.blockchain.android.testutils.rxInit
import com.blockchain.annotations.CommonCode
import com.blockchain.nabu.models.responses.nabu.NabuUser
import com.blockchain.nabu.NabuToken
import com.blockchain.nabu.datamanagers.NabuDataManager
import com.blockchain.nabu.models.responses.tokenresponse.NabuOfflineTokenResponse
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.atLeastOnce
import com.nhaarman.mockito_kotlin.never
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import io.reactivex.Single
import io.reactivex.schedulers.TestScheduler
import org.amshove.kluent.mock
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import piuk.blockchain.androidcore.data.settings.Email
import piuk.blockchain.androidcore.data.settings.EmailSyncUpdater
import java.util.concurrent.TimeUnit

@CommonCode("Also exists in nabu/test/TestHelper.kt")
private val validOfflineToken
    get() = NabuOfflineTokenResponse("userId",
        "lifetimeToken")

open class PitVerifyEmailPresenterTestBase {

    protected lateinit var presenter: PitVerifyEmailPresenter

    private val nabuToken: NabuToken = mock()

    protected val nabu: NabuDataManager = mock()
    protected val emailSyncUpdater: EmailSyncUpdater = mock()
    protected val mockView: PitVerifyEmailView = mock()

    protected val nabuUser: NabuUser = mock()

    @Before
    fun setUp() {
        whenever(nabuToken.fetchNabuToken()).thenReturn(Single.just(validOfflineToken))
        whenever(nabu.getUser(validOfflineToken)).thenReturn(Single.just(nabuUser))

        presenter = PitVerifyEmailPresenter(nabuToken, nabu, emailSyncUpdater).also {
            it.initView(mockView)
        }
    }

    companion object {
        const val TEST_EMAIL = "test@test.org"
    }
}

class PitVerifyEmailPresenterTestTrampoline : PitVerifyEmailPresenterTestBase() {

    @get:Rule
    val initSchedulers = rxInit {
        mainTrampoline()
        ioTrampoline()
    }

    @Test
    fun `when update mail fails, event should be propagated to the view`() {
        whenever(nabu.getUser(validOfflineToken)).thenReturn(Single.just(nabuUser))
        whenever(nabuUser.emailVerified).thenReturn(false)
        whenever(emailSyncUpdater.updateEmailAndSync(any(), any()))
            .thenReturn(Single.error(Throwable()))

        presenter.onViewReady()
        presenter.resendMail("")

        verify(mockView).mailResendFailed()

        verify(mockView, never()).mailResentSuccessfully()
        verify(mockView, never()).emailVerified()
    }

    @Test
    fun `when update mail succeeds, event should be propagated to the view`() {
        whenever(nabu.getUser(validOfflineToken)).thenReturn(Single.just(nabuUser))
        whenever(emailSyncUpdater.updateEmailAndSync(any(), any()))
            .thenReturn(Single.just(Email(TEST_EMAIL, true)))

        presenter.onViewReady()
        presenter.resendMail(TEST_EMAIL)

        verify(mockView).mailResentSuccessfully()

        verify(mockView, never()).mailResendFailed()
        verify(mockView, never()).emailVerified()
    }

    @Test
    fun `when update mail returns a empty email address, the view should learn of the error`() {
        whenever(nabu.getUser(validOfflineToken)).thenReturn(Single.just(nabuUser))
        whenever(emailSyncUpdater.updateEmailAndSync(any(), any()))
            .thenReturn(Single.just(Email("", true)))

        presenter.onViewReady()
        presenter.resendMail(TEST_EMAIL)

        verify(mockView).mailResendFailed()

        verify(mockView, never()).mailResentSuccessfully()
        verify(mockView, never()).emailVerified()
    }

    @Test
    fun `when update mail fails, a second call could succeed`() {
        whenever(nabu.getUser(validOfflineToken)).thenReturn(Single.just(nabuUser))
        whenever(emailSyncUpdater.updateEmailAndSync(any(), any()))
            .thenReturn(Single.error(Throwable()))
            .thenReturn(Single.just(Email(TEST_EMAIL, true)))

        presenter.onViewReady()
        presenter.resendMail(TEST_EMAIL) // Will fail
        presenter.resendMail(TEST_EMAIL) // Will succeed

        verify(mockView).mailResendFailed()
        verify(mockView).mailResentSuccessfully()

        verify(mockView, never()).emailVerified()
    }
}

class PitVerifyEmailPresenterTestTimer : PitVerifyEmailPresenterTestBase() {

    private val testScheduler = TestScheduler()

    @get:Rule
    val initSchedulers = rxInit {
        main(testScheduler)
        io(testScheduler)
        computation(testScheduler)
    }

    @Test
    fun `verification state is polled, and will notify the view when email has been verified`() {
        whenever(nabu.getUser(validOfflineToken)).thenReturn(Single.just(nabuUser))
        whenever(nabuUser.emailVerified)
            .thenReturn(false)
            .thenReturn(true)

        presenter.onViewReady()

        testScheduler.advanceTimeBy(15, TimeUnit.SECONDS)

        verify(mockView, atLeastOnce()).emailVerified()

        verify(mockView, never()).mailResendFailed()
        verify(mockView, never()).mailResentSuccessfully()
    }

    @Test
    fun `verification state is polled, and will continue to poll after an error`() {
        whenever(nabu.getUser(validOfflineToken))
            .thenReturn(Single.error(Throwable()))
            .thenReturn(Single.just(nabuUser))

        whenever(nabuUser.emailVerified)
            .thenReturn(true)

        presenter.onViewReady()

        testScheduler.advanceTimeBy(15, TimeUnit.SECONDS)

        verify(mockView, atLeastOnce()).emailVerified()

        verify(mockView, never()).mailResendFailed()
        verify(mockView, never()).mailResentSuccessfully()
    }
}