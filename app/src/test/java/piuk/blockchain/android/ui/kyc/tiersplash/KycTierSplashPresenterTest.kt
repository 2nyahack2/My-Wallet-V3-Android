package piuk.blockchain.android.ui.kyc.tiersplash

import androidx.navigation.NavDirections
import com.blockchain.android.testutils.rxInit
import com.blockchain.nabu.models.responses.nabu.KycTierState
import com.blockchain.nabu.models.responses.nabu.LimitsJson
import com.blockchain.nabu.models.responses.nabu.TierResponse
import com.blockchain.nabu.models.responses.nabu.KycTiers
import com.blockchain.nabu.service.TierService
import com.blockchain.nabu.service.TierUpdater
import com.blockchain.testutils.usd
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.never
import com.nhaarman.mockito_kotlin.verify
import info.blockchain.balance.FiatValue
import io.reactivex.Completable
import io.reactivex.Single
import org.amshove.kluent.`it returns`
import org.junit.Rule
import org.junit.Test
import piuk.blockchain.android.KycNavXmlDirections
import piuk.blockchain.android.R
import piuk.blockchain.android.ui.kyc.reentry.KycNavigator

class KycTierSplashPresenterTest {

    @get:Rule
    val rxSchedulers = rxInit {
        mainTrampoline()
    }

    @Test
    fun `on tier1 selected`() {
        val view: KycTierSplashView = mock()
        val tierUpdater: TierUpdater = givenTierUpdater()
        KycTierSplashPresenter(tierUpdater, givenTiers(), givenRedirect(email()))
            .also {
                it.initView(view)
                it.onViewResumed()
            }
            .tier1Selected()
        verify(view).navigateTo(email(), 1)
        verify(tierUpdater).setUserTier(1)
    }

    @Test
    fun `on tier1 selected - error setting tier`() {
        val view: KycTierSplashView = mock()
        val tierUpdater: TierUpdater = givenUnableToSetTier()
        KycTierSplashPresenter(tierUpdater, givenTiers(), givenRedirect(email()))
            .also {
                it.initView(view)
                it.onViewResumed()
            }
            .tier1Selected()
        verify(tierUpdater).setUserTier(1)
        verify(view, never()).navigateTo(any(), any())
        verify(view).showError(R.string.kyc_non_specific_server_error)
    }

    @Test
    fun `on tier1 selected but tier 1 is verified`() {
        val view: KycTierSplashView = mock()
        val tierUpdater: TierUpdater = givenTierUpdater()
        KycTierSplashPresenter(
            tierUpdater,
            givenTiers(
                tiers(
                    KycTierState.Verified to 1000.usd(),
                    KycTierState.None to 25000.usd()
                )
            ),
            givenRedirect(mobile())
        ).also {
            it.initView(view)
            it.onViewResumed()
        }.tier1Selected()
        verify(view, never()).navigateTo(any(), any())
        verify(tierUpdater, never()).setUserTier(any())
    }

    @Test
    fun `on tier2 selected`() {
        val view: KycTierSplashView = mock()
        val tierUpdater: TierUpdater = givenTierUpdater()
        KycTierSplashPresenter(tierUpdater, givenTiers(), givenRedirect(veriff()))
            .also {
                it.initView(view)
                it.onViewResumed()
            }
            .tier2Selected()
        verify(view).navigateTo(veriff(), 2)
        verify(tierUpdater).setUserTier(2)
    }

    @Test
    fun `on tier2 selected - error setting tier`() {
        val view: KycTierSplashView = mock()
        val tierUpdater: TierUpdater = givenUnableToSetTier()
        KycTierSplashPresenter(tierUpdater, givenTiers(), givenRedirect(veriff()))
            .also {
                it.initView(view)
                it.onViewResumed()
            }
            .tier2Selected()
        verify(tierUpdater).setUserTier(2)
        verify(view, never()).navigateTo(any(), any())
        verify(view).showError(R.string.kyc_non_specific_server_error)
    }

    @Test
    fun `on tier2 selected but tier 2 is verified`() {
        val view: KycTierSplashView = mock()
        val tierUpdater: TierUpdater = givenTierUpdater()
        KycTierSplashPresenter(
            tierUpdater,
            givenTiers(
                tiers(
                    KycTierState.None to 1000.usd(),
                    KycTierState.Verified to 25000.usd()
                )
            ),
            mock()
        ).also {
            it.initView(view)
            it.onViewResumed()
        }.tier2Selected()
        verify(view, never()).navigateTo(any(), any())
        verify(tierUpdater, never()).setUserTier(any())
    }

    private fun givenTierUpdater(): TierUpdater =
        mock {
            on { setUserTier(any()) } `it returns` Completable.complete()
        }

    private fun givenUnableToSetTier(): TierUpdater =
        mock {
            on { setUserTier(any()) } `it returns` Completable.error(Throwable())
        }
}

private fun givenTiers(tiers: KycTiers? = null): TierService =
    mock {
        on { tiers() } `it returns` Single.just(
            tiers ?: tiers(
                KycTierState.None to 1000.usd(),
                KycTierState.None to 25000.usd()
            )
        )
    }

private fun tiers(tier1: Pair<KycTierState, FiatValue>, tier2: Pair<KycTierState, FiatValue>) =
    KycTiers(
        tiersResponse = listOf(
            TierResponse(
                0,
                "Tier 0",
                state = KycTierState.Verified,
                limits = LimitsJson(
                    currency = "USD",
                    daily = null,
                    annual = null
                )
            ),
            TierResponse(
                1,
                "Tier 1",
                state = tier1.first,
                limits = LimitsJson(
                    currency = tier1.second.currencyCode,
                    daily = null,
                    annual = tier1.second.toBigDecimal()
                )
            ),
            TierResponse(
                2,
                "Tier 2",
                state = tier2.first,
                limits = LimitsJson(
                    currency = tier2.second.currencyCode,
                    daily = null,
                    annual = tier2.second.toBigDecimal()
                )
            )
        )
    )

private fun email(): NavDirections = KycNavXmlDirections.actionStartEmailVerification()
private fun mobile(): NavDirections = KycNavXmlDirections.actionStartMobileVerification("DE")
private fun veriff(): NavDirections = KycNavXmlDirections.actionStartVeriff("DE")

private fun givenRedirect(email: NavDirections): KycNavigator =
    mock {
        on {
            findNextStep()
        } `it returns` Single.just(email)
    }