package piuk.blockchain.android.util

import android.content.Context

@Deprecated("Only used for SecondPasswordHandler")
class CurrentContextAccess {
    var context: Context? = null
        private set

    fun contextOpen(ctx: Context) {
        context = ctx
    }

    fun contextClose(ctx: Context) {
        if (context == ctx) {
            context = null
        }
    }
}
