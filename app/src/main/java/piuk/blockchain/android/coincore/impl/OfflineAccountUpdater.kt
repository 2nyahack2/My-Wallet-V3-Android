package piuk.blockchain.android.coincore.impl

import android.annotation.SuppressLint
import com.google.gson.Gson
import info.blockchain.balance.CryptoCurrency
import info.blockchain.wallet.api.WalletApi
import io.reactivex.Single
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import piuk.blockchain.android.coincore.OfflineAccountCache
import piuk.blockchain.android.coincore.OfflineCachedAccount
import piuk.blockchain.android.data.api.NotificationReceiveAddresses
import piuk.blockchain.androidcore.data.payload.PayloadDataManager
import timber.log.Timber
import java.lang.IllegalStateException

// Update the BE with the current address sets for assets, used to
// send notifications back to the app when Tx's complete
// Chains with the local offline cache, which supports swipe to receive
class OfflineAccountUpdater(
    private val localCache: OfflineAccountCache,
    private val payloadManager: PayloadDataManager,
    private val walletApi: WalletApi
) : OfflineAccountCache {

    private val addressMap = mutableMapOf<String, OfflineCachedAccount>()

    override fun updateOfflineAddresses(fetchItem: Single<OfflineCachedAccount>) {
        localCache.updateOfflineAddresses(
            fetchItem.doOnSuccess { checkUpdateNotificationBackend(it) }
        )
    }

    @SuppressLint("CheckResult")
    @Synchronized
    private fun checkUpdateNotificationBackend(item: OfflineCachedAccount) {
        addressMap[item.networkTicker] = item
        if (item.networkTicker in REQUIRED_ASSETS && requiredAssetsUpdated()) {
            // This is a fire and forget operation.
            // We don't want this call to delay the main rx chain, and we don't care about errors,
            updateBackend()
                .subscribeOn(Schedulers.io())
                .subscribeBy(onError = { Timber.e("Notification Update failed: $it") })
        }
    }

    private fun requiredAssetsUpdated(): Boolean {
        REQUIRED_ASSETS.forEach { if (!addressMap.containsKey(it)) return@requiredAssetsUpdated false }
        return true
    }

    @Synchronized
    private fun updateBackend() =
        walletApi.submitCoinReceiveAddresses(
            payloadManager.guid,
            payloadManager.sharedKey,
            coinReceiveAddresses()
        ).ignoreElements()

    private fun coinReceiveAddresses(): String =
        Gson().toJson(
            REQUIRED_ASSETS.map { key ->
                val addresses = addressMap[key]?.rawAddressList ?: throw IllegalStateException("Required Asset missing")
                NotificationReceiveAddresses(key, addresses)
            }
        )

    companion object {
        private val REQUIRED_ASSETS = setOf(
            CryptoCurrency.BTC.networkTicker,
            CryptoCurrency.BCH.networkTicker,
            CryptoCurrency.ETHER.networkTicker
        )
    }
}