package piuk.blockchain.android.ui.transfer

import io.reactivex.Single
import piuk.blockchain.android.coincore.AssetOrdering
import piuk.blockchain.android.coincore.CryptoAccount
import piuk.blockchain.android.coincore.NonCustodialAccount
import piuk.blockchain.android.coincore.SingleAccount

interface AccountsSorting {
    fun sorter(): AccountsSorter
}

typealias AccountsSorter = (List<SingleAccount>) -> Single<List<SingleAccount>>

class DefaultAccountsSorting(private val assetsOrdering: AssetOrdering) : AccountsSorting {
    override fun sorter(): AccountsSorter {
        return { list ->
            assetsOrdering.getAssetOrdering().map { orderedAssets ->
                val sortedList = list.sortedWith(compareBy({
                    (it as? CryptoAccount)?.let { cryptoAccount ->
                        orderedAssets.indexOf(cryptoAccount.asset)
                    } ?: 0
                },
                    { it !is NonCustodialAccount },
                    { !it.isDefault }
                ))
                sortedList
            }
        }
    }
}