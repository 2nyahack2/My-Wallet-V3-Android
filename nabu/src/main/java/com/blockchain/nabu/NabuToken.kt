package com.blockchain.nabu

import com.blockchain.nabu.models.responses.tokenresponse.NabuOfflineTokenResponse
import io.reactivex.Single

interface NabuToken {

    /**
     * Find or creates the token
     */
    fun fetchNabuToken(currency: String? = null, action: String? = null): Single<NabuOfflineTokenResponse>
}
