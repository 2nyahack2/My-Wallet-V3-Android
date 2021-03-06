package com.blockchain.rx

import io.reactivex.Single
import io.reactivex.rxkotlin.subscribeBy
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class TimedCacheRequest<T>(
    private val cacheLifetimeSeconds: Long,
    private val refreshFn: () -> Single<T>
) {
    val expired = AtomicBoolean(true)
    var current = refreshFn.invoke()

    fun getCachedSingle(): Single<T> =
        Single.defer {
            if (expired.compareAndSet(true, false)) {
                current = refreshFn.invoke().cache()

                Single.timer(cacheLifetimeSeconds, TimeUnit.SECONDS)
                    .subscribeBy(onSuccess = { expired.set(true) })
            }
            current
        }
}

class ParameteredTimedCacheRequest<INPUT, OUTPUT>(
    private val cacheLifetimeSeconds: Long,
    private val refreshFn: (INPUT) -> Single<OUTPUT>
) {
    val expired = hashMapOf<INPUT, Boolean>()
    lateinit var current: Single<OUTPUT>

    fun getCachedSingle(input: INPUT): Single<OUTPUT> =
        Single.defer {
            if (expired[input] != false) {
                current = refreshFn.invoke(input).cache().doOnSuccess {
                    expired[input] = false
                }

                Single.timer(cacheLifetimeSeconds, TimeUnit.SECONDS)
                    .subscribeBy(onSuccess = { expired[input] = true })
            }
            current
        }
}
