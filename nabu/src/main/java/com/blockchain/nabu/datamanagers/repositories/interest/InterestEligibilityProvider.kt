package com.blockchain.nabu.datamanagers.repositories.interest

import com.blockchain.nabu.Authenticator
import com.blockchain.nabu.models.responses.interest.DisabledReason
import com.blockchain.nabu.service.NabuService
import info.blockchain.balance.CryptoCurrency
import io.reactivex.Single

interface InterestEligibilityProvider {
    fun getEligibilityForAllAssets(): Single<List<AssetInterestEligibility>>
}

class InterestEligibilityProviderImpl(
    private val nabuService: NabuService,
    private val authenticator: Authenticator
) : InterestEligibilityProvider {
    override fun getEligibilityForAllAssets(): Single<List<AssetInterestEligibility>> =
        authenticator.authenticate { token ->
            nabuService.getInterestEligibility(token).map { eligibilityResponse ->
                eligibilityResponse.eligibleList.entries.map { entry ->
                    CryptoCurrency.fromNetworkTicker(entry.key)?.let {
                        AssetInterestEligibility(
                            it,
                            Eligibility(
                                entry.value.eligible,
                                entry.value.ineligibilityReason
                            )
                        )
                    }
                }.mapNotNull { it }
            }
        }
}

data class AssetInterestEligibility(
    val cryptoCurrency: CryptoCurrency,
    val eligibility: Eligibility
)

data class Eligibility(
    val eligible: Boolean,
    val ineligibilityReason: DisabledReason
)