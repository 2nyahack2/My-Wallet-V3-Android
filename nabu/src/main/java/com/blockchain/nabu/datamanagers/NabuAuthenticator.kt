package com.blockchain.nabu.datamanagers

import com.blockchain.logging.CrashLogger
import com.blockchain.nabu.Authenticator
import com.blockchain.nabu.NabuToken
import com.blockchain.nabu.models.responses.tokenresponse.NabuSessionTokenResponse
import io.reactivex.Maybe
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import java.lang.IllegalStateException

internal class NabuAuthenticator(
    private val nabuToken: NabuToken,
    private val nabuDataManager: NabuDataManager,
    private val crashLogger: CrashLogger
) : Authenticator {

    override fun <T> authenticateSingle(singleFunction: (Single<NabuSessionTokenResponse>) -> Single<T>): Single<T> =
        nabuToken.fetchNabuToken()
            .map { nabuDataManager.currentToken(it) }
            .flatMap { singleFunction(it) }
            .doOnError {
                it.message?.let { msg ->
                    logMessageIfNeeded(msg)
                }
            }

    override fun <T> authenticateMaybe(maybeFunction: (NabuSessionTokenResponse) -> Maybe<T>): Maybe<T> =
        nabuToken.fetchNabuToken()
            .flatMapMaybe { tokenResponse ->
                nabuDataManager.authenticateMaybe(tokenResponse, maybeFunction)
                    .subscribeOn(Schedulers.io())
            }.doOnError {
                it.message?.let { msg ->
                    logMessageIfNeeded(msg)
                }
            }

    override fun <T> authenticate(singleFunction: (NabuSessionTokenResponse) -> Single<T>): Single<T> =
        nabuToken.fetchNabuToken()
            .flatMap { tokenResponse ->
                nabuDataManager.authenticate(tokenResponse, singleFunction)
                    .subscribeOn(Schedulers.io())
            }.doOnError {
                it.message?.let { msg ->
                    logMessageIfNeeded(msg)
                }
            }

    override fun invalidateToken() {
        nabuDataManager.invalidateToken()
    }

    private fun logMessageIfNeeded(message: String) {
        if (message.contains("BLOCKED_IP", ignoreCase = true))
            crashLogger.logException(BlockedIpException(message))
    }
}

class BlockedIpException(message: String) : IllegalStateException(message)
