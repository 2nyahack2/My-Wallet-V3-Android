package piuk.blockchain.android.ui.dashboard.announcements.rule

import com.blockchain.remoteconfig.FeatureFlag
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import io.reactivex.Single
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import org.junit.Before
import org.junit.Test
import piuk.blockchain.android.ui.dashboard.announcements.DismissRecorder

class WDGLDAvailableAnnouncementTest {
    private val dismissRecorder: DismissRecorder = mock()
    private val dismissEntry: DismissRecorder.DismissEntry = mock()
    private val featureFlag: FeatureFlag = mock()

    private lateinit var subject: WDGLDAvailableAnnouncement

    @Before
    fun setUp() {
        whenever(dismissRecorder[WDGLDAvailableAnnouncement.DISMISS_KEY]).thenReturn(dismissEntry)
        whenever(dismissEntry.prefsKey).thenReturn(WDGLDAvailableAnnouncement.DISMISS_KEY)

        subject =
            WDGLDAvailableAnnouncement(
                dismissRecorder = dismissRecorder,
                dgldFeatureFlag = featureFlag
            )
    }

    @Test
    fun `should not show, when already shown`() {
        whenever(dismissEntry.isDismissed).thenReturn(true)
        whenever(featureFlag.enabled).thenReturn(Single.just(true))

        subject.shouldShow()
            .test()
            .assertValue { !it }
            .assertValueCount(1)
            .assertComplete()
    }

    @Test
    fun `should show, when not already shown`() {
        whenever(dismissEntry.isDismissed).thenReturn(false)
        whenever(featureFlag.enabled).thenReturn(Single.just(true))

        subject.shouldShow()
            .test()
            .assertValue { it }
            .assertValueCount(1)
            .assertComplete()
    }

    @Test
    fun `should not show when feature flag is disabled`() {
        whenever(dismissEntry.isDismissed).thenReturn(false)
        whenever(featureFlag.enabled).thenReturn(Single.just(false))

        subject.shouldShow()
            .test()
            .assertValue { !it }
            .assertValueCount(1)
            .assertComplete()
    }
}
