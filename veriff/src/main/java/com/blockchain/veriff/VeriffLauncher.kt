package com.blockchain.veriff

import android.app.Activity
import androidx.core.app.ActivityCompat.startActivityForResult
import androidx.core.content.ContextCompat
import com.veriff.VeriffBranding
import com.veriff.VeriffConfiguration
import com.veriff.VeriffSdk
import timber.log.Timber

class VeriffLauncher {

    fun launchVeriff(activity: Activity, applicant: VeriffApplicantAndToken, requestCode: Int) {
        val sessionToken = applicant.token
        Timber.d("Veriff session token: $sessionToken")

        val branding = VeriffBranding.Builder()
            .themeColor(ContextCompat.getColor(activity, R.color.primary_blue_accent))
            .build()

        val configuration = VeriffConfiguration.Builder()
            .branding(branding)
            .build()

        val intent = VeriffSdk.createLaunchIntent(activity, sessionToken, configuration)
        startActivityForResult(activity, intent, requestCode, null)
    }
}
