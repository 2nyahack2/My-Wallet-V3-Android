package com.blockchain.nabu.service

import com.blockchain.nabu.api.nabu.Nabu
import com.blockchain.nabu.extensions.wrapErrorMessage
import com.blockchain.nabu.models.responses.nabu.TierUpdateJson
import com.blockchain.nabu.models.responses.nabu.KycTiers
import com.blockchain.nabu.Authenticator
import io.reactivex.Completable
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers

internal class NabuTierService(
    private val endpoint: Nabu,
    private val authenticator: Authenticator
) : TierService, TierUpdater {

    override fun tiers(): Single<KycTiers> =
        authenticator.authenticate {
            endpoint.getTiers(it.authHeader).wrapErrorMessage()
        }.subscribeOn(Schedulers.io())

    override fun setUserTier(tier: Int): Completable =
        authenticator.authenticate {
            endpoint.setTier(
                TierUpdateJson(tier),
                it.authHeader
            ).toSingleDefault(tier)
        }.ignoreElement()
}
