package com.blockchain.nabu.api.wallet

import com.blockchain.nabu.models.responses.wallet.RetailJwtResponse
import io.reactivex.Single
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Url

internal interface RetailWallet {

    @GET
    fun requestJwt(
        @Url url: String,
        @Query("guid") guid: String,
        @Query("sharedKey") sharedKey: String,
        @Query("api_code") apiCode: String
    ): Single<RetailJwtResponse>
}