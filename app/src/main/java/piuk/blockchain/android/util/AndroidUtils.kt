package piuk.blockchain.android.util

import android.os.Build

object AndroidUtils {
    fun is25orHigher(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1
    fun is26orHigher(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
}