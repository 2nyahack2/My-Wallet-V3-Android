package com.blockchain.remoteconfig

import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import io.reactivex.Single
import piuk.blockchain.androidcore.BuildConfig
import timber.log.Timber

interface ABTestExperiment {
    fun getABVariant(key: String): Single<String>

    companion object {
//        const val AB_THE_PIT_SIDE_NAV_VARIANT = "ab_the_pit_side_nav_variant"
//        const val AB_THE_PIT_ANNOUNCEMENT_VARIANT = "ab_the_pit_announcement_variant"
    }
}

interface RemoteConfig {

    fun getIfFeatureEnabled(key: String): Single<Boolean>

    fun getRawJson(key: String): Single<String>

    fun getFeatureCount(key: String): Single<Long>
}

class RemoteConfiguration(private val remoteConfig: FirebaseRemoteConfig) :
    RemoteConfig, ABTestExperiment {

    private val configuration: Single<FirebaseRemoteConfig> =
        Single.just(remoteConfig.fetch(if (BuildConfig.DEBUG) 0L else 14400L))
            .cache()
            .doOnSuccess { remoteConfig.activateFetched() }
            .doOnError { Timber.e(it, "Failed to load Firebase Remote Config") }
            .map { remoteConfig }

    override fun getRawJson(key: String): Single<String> =
        configuration.map {
            it.getString(key)
        }

    override fun getIfFeatureEnabled(key: String): Single<Boolean> =
        configuration.map { it.getBoolean(key) }

    override fun getABVariant(key: String): Single<String> =
        configuration.map { it.getString(key) }

    override fun getFeatureCount(key: String): Single<Long> =
        configuration.map { it.getLong(key) }
}

fun RemoteConfig.featureFlag(key: String): FeatureFlag = object :
    FeatureFlag {
    override val enabled: Single<Boolean> get() = getIfFeatureEnabled(key)
}
