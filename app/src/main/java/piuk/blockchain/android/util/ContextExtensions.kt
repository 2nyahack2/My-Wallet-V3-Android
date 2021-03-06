package piuk.blockchain.android.util

import android.content.Context
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import piuk.blockchain.android.R

fun Context.loadInterMedium(): Typeface =
    ResourcesCompat.getFont(this, R.font.inter_medium)!!

fun Context.loadInterSemibold(): Typeface =
    ResourcesCompat.getFont(this, R.font.inter_semibold)!!