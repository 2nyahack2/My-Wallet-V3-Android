package piuk.blockchain.android.ui.dashboard.announcements.rule

import com.blockchain.preferences.WalletStatus
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import org.junit.Before
import org.junit.Test
import piuk.blockchain.android.ui.dashboard.announcements.DismissRecorder

class TransferCryptoAnnouncementTest {

    private val dismissRecorder: DismissRecorder = mock()
    private val dismissEntry: DismissRecorder.DismissEntry = mock()
    private val walletStatus: WalletStatus = mock()

    private lateinit var subject: TransferCryptoAnnouncement

    @Before
    fun setUp() {
        whenever(dismissRecorder[TransferCryptoAnnouncement.DISMISS_KEY]).thenReturn(dismissEntry)
        whenever(dismissEntry.prefsKey).thenReturn(TransferCryptoAnnouncement.DISMISS_KEY)

        subject =
            TransferCryptoAnnouncement(
                dismissRecorder = dismissRecorder,
                walletStatus = walletStatus
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
    fun `should show, when not already shown, wallet is unfunded`() {
        whenever(dismissEntry.isDismissed).thenReturn(false)
        whenever(walletStatus.isWalletFunded).thenReturn(true)

        whenever(walletStatus.isWalletFunded).thenReturn(false)

        subject.shouldShow()
            .test()
            .assertValue { it }
            .assertValueCount(1)
            .assertComplete()
    }

    @Test
    fun `should not show, when not already shown, wallet is funded`() {
        whenever(dismissEntry.isDismissed).thenReturn(false)
        whenever(walletStatus.isWalletFunded).thenReturn(true)

        whenever(walletStatus.isWalletFunded).thenReturn(true)

        subject.shouldShow()
            .test()
            .assertValue { !it }
            .assertValueCount(1)
            .assertComplete()
    }
}
