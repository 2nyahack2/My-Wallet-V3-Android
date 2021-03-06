package com.blockchain.nabu.service

import com.blockchain.nabu.api.wallet.RETAIL_JWT_TOKEN
import com.blockchain.nabu.api.wallet.RetailWallet
import com.blockchain.nabu.models.responses.wallet.RetailJwtResponse
import io.reactivex.Single
import piuk.blockchain.androidcore.data.api.EnvironmentConfig
import retrofit2.Retrofit

class RetailWalletTokenService(
    environmentConfig: EnvironmentConfig,
    private val apiCode: String,
    retrofit: Retrofit
) {

    private val service: RetailWallet = retrofit.create(RetailWallet::class.java)
    private val explorerPath = environmentConfig.explorerUrl

    internal fun requestJwt(
        path: String = explorerPath + RETAIL_JWT_TOKEN,
        guid: String,
        sharedKey: String
    ): Single<RetailJwtResponse> = service.requestJwt(
        path,
        guid,
        sharedKey,
        apiCode
    )
}