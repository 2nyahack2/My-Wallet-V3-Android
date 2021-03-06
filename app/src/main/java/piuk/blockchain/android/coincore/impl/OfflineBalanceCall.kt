package piuk.blockchain.android.coincore.impl

import info.blockchain.api.blockexplorer.BlockExplorer
import info.blockchain.balance.CryptoCurrency
import info.blockchain.balance.CryptoValue
import info.blockchain.wallet.payload.BalanceCall
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers

class OfflineBalanceCall(
    private val blockExplorer: BlockExplorer
) {
    fun getBalanceOfAddresses(asset: CryptoCurrency, addresses: List<String>): Single<Map<String, CryptoValue>> =
        Single.fromCallable {
            BalanceCall(blockExplorer, asset).getBalancesFor(addresses.toSet())
        }.subscribeOn(Schedulers.io())
            .map { it.mapValues { v -> CryptoValue.fromMinor(asset, v.value) } }
}
