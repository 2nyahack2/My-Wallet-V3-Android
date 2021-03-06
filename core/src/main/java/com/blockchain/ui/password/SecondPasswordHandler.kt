package com.blockchain.ui.password

import android.content.Context
import io.reactivex.Maybe

interface SecondPasswordHandler {

    val hasSecondPasswordSet: Boolean
    val verifiedPassword: String?

    interface ResultListener {
        fun onNoSecondPassword()
        fun onSecondPasswordValidated(validatedSecondPassword: String)
        fun onCancelled() {}
    }

    fun validate(ctx: Context, listener: ResultListener)
    @Deprecated(message = "Context access is deprecated. Use validate(ctx, listener) instead")
    fun validate(listener: ResultListener)

    fun secondPassword(ctx: Context): Maybe<String>
    @Deprecated(message = "Context access is deprecated. Use secondPassword(ctx) instead")
    fun secondPassword(): Maybe<String>
}
