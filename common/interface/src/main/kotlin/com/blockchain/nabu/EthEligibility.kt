package com.blockchain.nabu

import io.reactivex.Single

interface EthEligibility {
    fun isEligible(): Single<Boolean>
}