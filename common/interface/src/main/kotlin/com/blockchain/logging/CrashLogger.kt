package com.blockchain.logging

interface CrashLogger {
    fun init(ctx: Any)

    fun logEvent(msg: String) // Log something for crash debugging context
    fun logState(name: String, data: String) // Log a key/value property
    fun logException(throwable: Throwable, logMsg: String = "") // Log non-fatal exception catches
    fun logAndRethrowException(throwable: Throwable, logMsg: String = "") // Log non-fatal exception catches

    // Log various app state information. Extend this as required
    fun onlineState(isOnline: Boolean)
    fun userLanguageLocale(locale: String)

    val isDebugBuild: Boolean
}