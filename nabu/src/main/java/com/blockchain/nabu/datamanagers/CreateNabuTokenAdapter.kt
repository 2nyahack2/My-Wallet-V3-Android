package com.blockchain.nabu.datamanagers

import com.blockchain.nabu.CreateNabuToken
import com.blockchain.nabu.models.responses.tokenresponse.NabuOfflineTokenResponse
import io.reactivex.Single

internal class CreateNabuTokenAdapter(
    private val nabuDataManager: NabuDataManager
) : CreateNabuToken {

    override fun createNabuOfflineToken(currency: String?, action: String?): Single<NabuOfflineTokenResponse> =
        nabuDataManager.requestJwt()
            .flatMap { jwt ->
                nabuDataManager.getAuthToken(jwt = jwt, currency = currency, action = action)
            }
}
