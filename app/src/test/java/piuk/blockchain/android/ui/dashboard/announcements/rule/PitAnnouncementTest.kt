package piuk.blockchain.android.ui.dashboard.announcements.rule

import com.blockchain.notifications.analytics.Analytics
import com.blockchain.remoteconfig.FeatureFlag
import com.nhaarman.mockito_kotlin.whenever
import io.reactivex.Single
import org.amshove.kluent.mock
import org.junit.Before
import org.junit.Test
import piuk.blockchain.android.thepit.PitLinking
import piuk.blockchain.android.ui.dashboard.announcements.DismissRecorder

class PitAnnouncementTest {
    private val dismissRecorder: DismissRecorder = mock()
    private val dismissEntry: DismissRecorder.DismissEntry = mock()
    private val pitLinking: PitLinking = mock()
    private val featureFlag: FeatureFlag = mock()
    private val analytics: Analytics = mock()

    private lateinit var subject: PitAnnouncement

    @Before
    fun setUp() {
        whenever(dismissRecorder[PitAnnouncement.DISMISS_KEY]).thenReturn(dismissEntry)
        whenever(dismissEntry.prefsKey).thenReturn(PitAnnouncement.DISMISS_KEY)

        subject = PitAnnouncement(
            pitLink = pitLinking,
            dismissRecorder = dismissRecorder,
            analytics = analytics
        )
    }

    @Test
    fun `should not show, when already shown`() {
        whenever(dismissEntry.isDismissed).thenReturn(true)

        subject.shouldShow()
            .test()
            .assertValue { !it }
            .assertValueCount(1)
            .assertComplete()
    }

    @Test
    fun `should not show, if the pit is already linked`() {
        whenever(dismissEntry.isDismissed).thenReturn(false)
        whenever(pitLinking.isPitLinked()).thenReturn(Single.just(true))

        subject.shouldShow()
            .test()
            .assertValue { !it }
            .assertValueCount(1)
            .assertComplete()
    }

    @Test
    fun `should show, if the pit is not linked`() {
        whenever(dismissEntry.isDismissed).thenReturn(false)
        whenever(pitLinking.isPitLinked()).thenReturn(Single.just(false))

        subject.shouldShow()
            .test()
            .assertValue { it }
            .assertValueCount(1)
            .assertComplete()
    }
}
