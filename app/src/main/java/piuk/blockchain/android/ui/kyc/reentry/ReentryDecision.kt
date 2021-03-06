package piuk.blockchain.android.ui.kyc.reentry

import androidx.navigation.NavDirections
import com.blockchain.nabu.datamanagers.NabuDataManager
import com.blockchain.nabu.models.responses.nabu.NabuUser
import piuk.blockchain.android.ui.kyc.navhost.toProfileModel
import com.blockchain.nabu.NabuToken
import io.reactivex.Single
import piuk.blockchain.android.KycNavXmlDirections

interface ReentryDecision {

    fun findReentryPoint(user: NabuUser): ReentryPoint
}

interface KycNavigator {

    /**
     * Will fetch user, if you have it, user overload.
     */
    fun findNextStep(): Single<NavDirections>

    fun findNextStep(user: NabuUser): NavDirections

    fun userAndReentryPointToDirections(user: NabuUser, reentryPoint: ReentryPoint): NavDirections
}

class ReentryDecisionKycNavigator(
    private val token: NabuToken,
    private val dataManager: NabuDataManager,
    private val reentryDecision: ReentryDecision
) : KycNavigator {

    override fun findNextStep(): Single<NavDirections> =
        token.fetchNabuToken()
            .flatMap(dataManager::getUser)
            .map { findNextStep(it) }

    override fun findNextStep(user: NabuUser) =
        userAndReentryPointToDirections(user, reentryDecision.findReentryPoint(user))

    override fun userAndReentryPointToDirections(user: NabuUser, reentryPoint: ReentryPoint) =
        when (reentryPoint) {
            ReentryPoint.EmailEntry -> KycNavXmlDirections.actionStartEmailVerification()
            ReentryPoint.CountrySelection -> KycNavXmlDirections.actionStartCountrySelection()
            ReentryPoint.Profile -> KycNavXmlDirections.actionStartProfile(
                user.requireCountryCode(), user.address?.state ?: ""
            )
            ReentryPoint.Address -> KycNavXmlDirections.actionStartAddressEntry(user.toProfileModel())
            ReentryPoint.MobileEntry -> KycNavXmlDirections.actionStartMobileVerification(user.requireCountryCode())
            ReentryPoint.Veriff -> {
                val countryCode = user.requireCountryCode()
                KycNavXmlDirections.actionStartVeriff(countryCode)
            }
        }
}
