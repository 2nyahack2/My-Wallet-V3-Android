package piuk.blockchain.androidcore.utils.extensions

import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.Maybe
import io.reactivex.MaybeSource
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.annotations.CheckReturnValue
import io.reactivex.annotations.SchedulerSupport
import io.reactivex.disposables.Disposable
import io.reactivex.exceptions.OnErrorNotImplementedException
import io.reactivex.internal.functions.Functions

/**
 * Subscribes to a [Maybe] and silently consumes any emitted values. Any exceptions thrown won't
 * cascade into a [OnErrorNotImplementedException], but will be signalled to the RxJava plugin
 * error handler. Note that this means that [RxJavaPlugins.setErrorHandler()] MUST be set.
 *
 * @return A [Disposable] object.
 */
fun <T> Maybe<T>.emptySubscribe(): Disposable =
    subscribe(Functions.emptyConsumer(), Functions.ERROR_CONSUMER)

/**
 * Subscribes to a [Maybe] and silently consumes any emitted values. Any exceptions thrown won't
 * cascade into a [OnErrorNotImplementedException], but will be signalled to the RxJava plugin
 * error handler. Note that this means that [RxJavaPlugins.setErrorHandler()] MUST be set.
 *
 * @return A [Disposable] object.
 */
fun <T> Single<T>.emptySubscribe(): Disposable =
    subscribe(Functions.emptyConsumer(), Functions.ERROR_CONSUMER)

/**
 * Subscribes to a [Flowable] and silently consumes any emitted values. Any exceptions thrown won't
 * cascade into a [OnErrorNotImplementedException], but will be signalled to the RxJava plugin
 * error handler. Note that this means that [RxJavaPlugins.setErrorHandler()] MUST be set.
 *
 * @return A [Disposable] object.
 */
fun <T> Flowable<T>.emptySubscribe(): Disposable =
    subscribe(Functions.emptyConsumer(), Functions.ERROR_CONSUMER)

/**
 * Subscribes to a [Observable] and silently consumes any emitted values. Any exceptions thrown won't
 * cascade into a [OnErrorNotImplementedException], but will be signalled to the RxJava plugin
 * error handler. Note that this means that [RxJavaPlugins.setErrorHandler()] MUST be set.
 *
 * @return A [Disposable] object.
 */
fun <T> Observable<T>.emptySubscribe(): Disposable =
    subscribe(Functions.emptyConsumer(), Functions.ERROR_CONSUMER)

/**
 * Subscribes to a [Completable] and silently completes, if applicable. Any exceptions thrown won't
 * cascade into a [OnErrorNotImplementedException], but will be signalled to the RxJava plugin
 * error handler. Note that this means that [RxJavaPlugins.setErrorHandler()] MUST be set.
 *
 * @return A [Disposable] object.
 */
fun Completable.emptySubscribe(): Disposable =
    subscribe(Functions.EMPTY_ACTION, Functions.ERROR_CONSUMER)

@CheckReturnValue
@SchedulerSupport(SchedulerSupport.NONE)
fun <T, R> Maybe<T>.flatMapBy(
    onSuccess: (T) -> MaybeSource<out R>?,
    onError: (Throwable?) -> MaybeSource<out R>?,
    onComplete: () -> MaybeSource<out R>?
): Maybe<R> = this.flatMap(
    onSuccess,
    onError,
    onComplete
)

fun <T> Completable.thenSingle(block: () -> Single<T>): Single<T> =
    andThen(Single.defer { block() })

fun Completable.then(block: () -> Completable): Completable =
    andThen(Completable.defer { block() })

fun <T> Completable.thenMaybe(block: () -> Maybe<T>): Maybe<T> =
    andThen(Maybe.defer { block() })

fun <T, R> Observable<List<T>>.mapList(func: (T) -> R): Single<List<R>> =
    flatMapIterable { list ->
        list.map { func(it) }
    }.toList()

fun <T, R> Single<List<T>>.mapList(func: (T) -> R): Single<List<R>> =
    flattenAsObservable { list ->
        list.map { func(it) }
    }.toList()
