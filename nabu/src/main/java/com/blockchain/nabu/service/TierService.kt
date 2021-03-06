package com.blockchain.nabu.service

import com.blockchain.nabu.models.responses.nabu.KycTiers
import io.reactivex.Single

interface TierService {

    fun tiers(): Single<KycTiers>
}
