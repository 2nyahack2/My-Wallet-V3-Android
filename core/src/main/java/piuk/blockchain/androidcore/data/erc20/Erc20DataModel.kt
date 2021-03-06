package piuk.blockchain.androidcore.data.erc20

import info.blockchain.balance.CryptoCurrency
import info.blockchain.balance.CryptoValue
import info.blockchain.wallet.ethereum.data.Erc20AddressResponse

data class Erc20DataModel(
    val totalBalance: CryptoValue,
    val transfers: List<Erc20Transfer>,
    val accountHash: String
) {
    companion object {
        operator fun invoke(
            addressResponse: Erc20AddressResponse,
            cryptoCurrency: CryptoCurrency
        ): Erc20DataModel =
            Erc20DataModel(CryptoValue(cryptoCurrency, addressResponse.balance),
                addressResponse.transfers.map { Erc20Transfer(it) },
                addressResponse.accountHash)
    }
}