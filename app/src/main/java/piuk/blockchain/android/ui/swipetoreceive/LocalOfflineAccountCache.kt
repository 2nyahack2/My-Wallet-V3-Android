package piuk.blockchain.android.ui.swipetoreceive

import android.annotation.SuppressLint
import com.blockchain.preferences.OfflineCachePrefs
import io.reactivex.Completable
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.modules.SerializersModule
import piuk.blockchain.android.coincore.OfflineAccountCache
import piuk.blockchain.android.coincore.OfflineCachedAccount
import piuk.blockchain.androidcore.utils.extensions.emptySubscribe
import piuk.blockchain.androidcore.utils.extensions.then
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import kotlinx.serialization.encodeToString
import piuk.blockchain.android.coincore.AssetOrdering
import piuk.blockchain.android.coincore.SimpleOfflineCacheItem
import piuk.blockchain.android.coincore.bch.BchOfflineAccountItem
import piuk.blockchain.android.coincore.btc.BtcOfflineAccountItem
import piuk.blockchain.androidcore.utils.helperfunctions.unsafeLazy

class LocalOfflineAccountCache(
    private val prefs: OfflineCachePrefs,
    private val ordering: AssetOrdering
) : OfflineAccountCache {

    private val serializers = SerializersModule {
        polymorphic(OfflineCachedAccount::class) {
            subclass(SimpleOfflineCacheItem::class)
            subclass(BtcOfflineAccountItem::class)
            subclass(BchOfflineAccountItem::class)
        }
    }

    private val json = Json { serializersModule = serializers }

    private val cache: MutableMap<String, OfflineCachedAccount> by unsafeLazy {
        loadCache().toMutableMap()
    }

    @SuppressLint("CheckResult")
    override fun updateOfflineAddresses(fetchItem: Single<OfflineCachedAccount>) {
        fetchItem.subscribeOn(Schedulers.io())
        .flatMapCompletable {
            updateCacheAndSave(it.networkTicker, it)
        }.emptySubscribe()
    }

    @Synchronized
    private fun updateCacheAndSave(key: String, data: OfflineCachedAccount) =
        if (prefs.offlineCacheEnabled) {
            Completable.fromCallable { cache.put(key, data) }.then { storeCache() }
        } else {
            Completable.complete()
        }

    fun availableAssets(): Single<List<String>> =
        ordering.getAssetOrdering().map { it.map { a -> a.networkTicker } }
            .map { orderList ->
                val assetList = cache.keys.toList()
                assetList.sortedBy { i -> orderList.indexOf(i) }
            }

    fun getCacheForAsset(assetNetworkTicker: String): OfflineCachedAccount? =
        cache[assetNetworkTicker]

    private fun storeCache(): Completable {
        return Completable.fromAction { prefs.offlineCacheData = json.encodeToString(cache) }
    }

    private fun loadCache(): Map<String, OfflineCachedAccount> =
        prefs.offlineCacheData?.let {
            try {
                json.decodeFromString<Map<String, OfflineCachedAccount>>(it)
            } catch (t: Throwable) {
                prefs.offlineCacheData = null
                emptyMap<String, OfflineCachedAccount>()
            }
        } ?: emptyMap()
}