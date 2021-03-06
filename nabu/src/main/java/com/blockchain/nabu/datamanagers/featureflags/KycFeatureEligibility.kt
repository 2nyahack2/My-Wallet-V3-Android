package com.blockchain.nabu.datamanagers.featureflags

import com.blockchain.nabu.datamanagers.repositories.NabuUserRepository
import com.blockchain.nabu.models.responses.nabu.KycState
import io.reactivex.Single

class KycFeatureEligibility(private val userRepository: NabuUserRepository) : FeatureEligibility {
    override fun isEligibleFor(feature: Feature): Single<Boolean> =
        when (feature) {
            Feature.INTEREST_RATES,
            Feature.INTEREST_DETAILS,
            Feature.SIMPLEBUY_BALANCE ->
                userRepository.fetchUser()
                    .map {
                        it.currentTier == 2 && it.kycState == KycState.Verified
                    }
        }
}