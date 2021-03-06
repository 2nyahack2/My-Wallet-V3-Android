package com.blockchain.nabu

import com.blockchain.nabu.models.responses.tokenresponse.NabuSessionTokenResponse
import io.reactivex.Completable
import io.reactivex.Maybe
import io.reactivex.Single

interface Authenticator {

    fun <T> authenticate(
        singleFunction: (NabuSessionTokenResponse) -> Single<T>
    ): Single<T>

    fun <T> authenticateMaybe(
        maybeFunction: (NabuSessionTokenResponse) -> Maybe<T>
    ): Maybe<T>

    fun <T> authenticateSingle(
        singleFunction: (Single<NabuSessionTokenResponse>) -> Single<T>
    ): Single<T>

    fun authenticateCompletable(
        completableFunction: (NabuSessionTokenResponse) -> Completable
    ): Completable = authenticate {
        completableFunction(it)
            .toSingleDefault(Unit)
    }.ignoreElement()

    fun authenticate(): Single<NabuSessionTokenResponse> =
        authenticateSingle { it }

    fun invalidateToken()
}
