package piuk.blockchain.android.coincore

import io.reactivex.Maybe
import io.reactivex.Single
import kotlinx.serialization.Serializable
import piuk.blockchain.android.coincore.impl.OfflineBalanceCall

interface OfflineAccountCache {
    fun updateOfflineAddresses(fetchItem: Single<OfflineCachedAccount>)
}

@Serializable
data class CachedAddress(
    val address: String,
    val addressUri: String
)

interface OfflineCachedAccount {
    val networkTicker: String
    val accountLabel: String
    fun nextAddress(balanceCall: OfflineBalanceCall): Maybe<CachedAddress>
    val rawAddressList: List<String>
}

@Serializable
data class SimpleOfflineCacheItem(
    override val networkTicker: String,
    override val accountLabel: String,
    internal val address: CachedAddress
) : OfflineCachedAccount {
    override fun nextAddress(balanceCall: OfflineBalanceCall) = Maybe.just(address)

    override val rawAddressList: List<String>
        get() = listOf(address.address)
}