package com.blockchain.nabu.service

import com.blockchain.nabu.Authenticator
import com.blockchain.nabu.models.responses.tokenresponse.NabuSessionTokenResponse
import io.reactivex.Maybe
import io.reactivex.Single

class MockAuthenticator(val token: String) : Authenticator {

    override fun <T> authenticateSingle(singleFunction: (Single<NabuSessionTokenResponse>) -> Single<T>): Single<T> =
        Single.fromCallable {
            singleFunction(
                singleSessionTokenResponse()
            )
        }.flatMap { it }

    override fun <T> authenticate(singleFunction: (NabuSessionTokenResponse) -> Single<T>): Single<T> =
        singleSessionTokenResponse().flatMap { singleFunction(it) }

    override fun <T> authenticateMaybe(maybeFunction: (NabuSessionTokenResponse) -> Maybe<T>): Maybe<T> =
        singleSessionTokenResponse().flatMapMaybe { maybeFunction(it) }

    private fun singleSessionTokenResponse(): Single<NabuSessionTokenResponse> =
        Single.just(
            NabuSessionTokenResponse(
                id = "",
                userId = "",
                token = token,
                isActive = true,
                expiresAt = "",
                insertedAt = "",
                updatedAt = ""
            )
        )

    override fun invalidateToken() {
    }
}
