package com.blockchain.nabu.datamanagers.featureflags

import io.reactivex.Single

enum class Feature {
    INTEREST_RATES,
    INTEREST_DETAILS,
    SIMPLEBUY_BALANCE
}

interface FeatureEligibility {
    fun isEligibleFor(feature: Feature): Single<Boolean>
}