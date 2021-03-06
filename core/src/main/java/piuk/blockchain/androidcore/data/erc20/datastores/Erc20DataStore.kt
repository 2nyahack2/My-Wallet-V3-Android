package piuk.blockchain.androidcore.data.erc20.datastores

import info.blockchain.balance.CryptoCurrency
import piuk.blockchain.androidcore.data.datastores.SimpleDataStore
import piuk.blockchain.androidcore.data.erc20.Erc20DataModel

class Erc20DataStore : SimpleDataStore {

    val erc20DataModel = mutableMapOf<CryptoCurrency, Erc20DataModel>()

    override fun clearData() {
        erc20DataModel.clear()
    }
}