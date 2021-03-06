package com.blockchain.nabu.datamanagers

import com.blockchain.nabu.models.responses.nabu.NabuUser
import com.blockchain.nabu.NabuToken
import io.reactivex.Single

interface NabuDataUserProvider {

    fun getUser(): Single<NabuUser>
}

internal class NabuDataUserProviderNabuDataManagerAdapter(
    private val nabuToken: NabuToken,
    private val nabuDataManager: NabuDataManager
) : NabuDataUserProvider {

    override fun getUser(): Single<NabuUser> =
        nabuToken
            .fetchNabuToken()
            .flatMap(nabuDataManager::getUser)
}
