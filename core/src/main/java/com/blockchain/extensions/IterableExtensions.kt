package com.blockchain.extensions

inline fun <T> Iterable<T>.nextAfterOrNull(predicate: (T) -> Boolean): T? {
    var found = false
    for (item in this) {
        if (found) return item
        if (predicate(item)) found = true
    }
    return null
}

val <T> T.exhaustive: T
    get() = this

fun <E> Iterable<E>.replace(old: E, new: E) = map { if (it == old) new else it }

inline fun <K, reified V> Map<K, V?>.withoutNullValues(): Map<K, V> =
    this.filterValues { it != null }.mapValues { it.value as V }