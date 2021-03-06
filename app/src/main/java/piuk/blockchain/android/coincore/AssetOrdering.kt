package piuk.blockchain.android.coincore

import info.blockchain.balance.CryptoCurrency
import io.reactivex.Single

interface AssetOrdering {
    fun getAssetOrdering(): Single<List<CryptoCurrency>>
}
