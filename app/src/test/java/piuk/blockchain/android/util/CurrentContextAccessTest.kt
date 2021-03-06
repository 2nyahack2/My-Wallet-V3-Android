package piuk.blockchain.android.util

import androidx.appcompat.app.AppCompatActivity
import org.amshove.kluent.`should be`
import org.amshove.kluent.mock
import org.junit.Test

class CurrentContextAccessTest {

    @Test
    fun `context is null at first`() {
        CurrentContextAccess().context `should be` null
    }

    @Test
    fun `context is saved on contextOpen()`() {
        val access = CurrentContextAccess()
        val activity = mock<AppCompatActivity>()
        access.contextOpen(activity)
        access.context `should be` activity
    }

    @Test
    fun `context is released when closed`() {
        val access = CurrentContextAccess()
        val activity = mock<AppCompatActivity>()

        access.contextOpen(activity)
        access.contextClose(activity)

        access.context `should be` null
    }

    @Test
    fun `closing a different context does not affect the current one`() {
        val access = CurrentContextAccess()
        val activity1 = mock<AppCompatActivity>()
        val activity2 = mock<AppCompatActivity>()
        access.contextOpen(activity1)
        access.contextClose(activity2)

        access.context `should be` activity1
    }
}
