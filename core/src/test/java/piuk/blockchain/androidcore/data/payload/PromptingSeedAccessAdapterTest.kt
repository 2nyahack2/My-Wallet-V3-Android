package piuk.blockchain.androidcore.data.payload

import com.blockchain.android.testutils.rxInit
import com.blockchain.ui.password.SecondPasswordHandler
import com.blockchain.wallet.Seed
import com.blockchain.wallet.SeedAccess
import com.blockchain.wallet.SeedAccessWithoutPrompt
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.verifyNoMoreInteractions
import io.reactivex.Maybe
import org.amshove.kluent.`it returns`
import org.amshove.kluent.itReturns
import org.amshove.kluent.mock
import org.junit.Rule
import org.junit.Test

class PromptingSeedAccessAdapterTest {

    @get:Rule
    val initRx = rxInit {
        mainTrampoline()
    }

    @Test
    fun `seed prompt if required`() {
        val theSeed: Seed = mock()
        val seedAccessWithoutPrompt: SeedAccessWithoutPrompt = mock {
            on { seed } `it returns` Maybe.empty()
            on { seed(any()) } `it returns` Maybe.just(theSeed)
        }
        val secondPasswordHandler: SecondPasswordHandler = mock {
            on { hasSecondPasswordSet } `it returns` true
            on { secondPassword() } itReturns Maybe.just("ABCDEF")
        }
        val seedAccess: SeedAccess = PromptingSeedAccessAdapter(seedAccessWithoutPrompt, secondPasswordHandler)

        seedAccess.seedPromptIfRequired.test()
            .assertValue(theSeed)
            .assertComplete()

        verify(seedAccessWithoutPrompt).seed("ABCDEF")
    }

    @Test
    fun `no seed prompt if not required`() {
        val theSeed: Seed = mock()
        val seedAccessWithoutPrompt: SeedAccessWithoutPrompt = mock {
            on { seed } `it returns` Maybe.just(theSeed)
        }
        val secondPasswordHandler: SecondPasswordHandler = mock {
            on { secondPassword() } itReturns Maybe.empty()
        }
        val seedAccess: SeedAccess = PromptingSeedAccessAdapter(seedAccessWithoutPrompt, secondPasswordHandler)

        seedAccess.seedPromptIfRequired
            .test()
            .assertValue(theSeed)
            .assertComplete()

        verify(secondPasswordHandler).secondPassword()
        verifyNoMoreInteractions(secondPasswordHandler)
    }
}
