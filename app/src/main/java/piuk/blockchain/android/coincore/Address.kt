package piuk.blockchain.android.coincore

import info.blockchain.balance.CryptoCurrency
import info.blockchain.balance.CryptoValue
import io.reactivex.Completable
import io.reactivex.Maybe
import io.reactivex.Single
import timber.log.Timber

class AddressParseError(val error: Error) : Exception("Error Parsing address") {

    enum class Error {
        ETH_UNEXPECTED_CONTRACT_ADDRESS
    }
}

interface TransactionTarget {
    val label: String
    val onTxCompleted: (TxResult) -> Completable
        get() = { _ ->
            Completable.complete()
        }
}

// An invoice has a fixed amount
interface InvoiceTarget

interface ReceiveAddress : TransactionTarget {
    val address: String
}

object NullAddress : ReceiveAddress {
    override val label: String = ""
    override val address: String = ""
}

interface CryptoTarget : TransactionTarget {
    val asset: CryptoCurrency
}

interface CryptoAddress : CryptoTarget, ReceiveAddress {
    fun toUrl(amount: CryptoValue = CryptoValue.zero(asset)) = address
}

interface AddressFactory {
    fun parse(address: String): Single<Set<ReceiveAddress>>
    fun parse(address: String, ccy: CryptoCurrency): Maybe<ReceiveAddress>
}

class AddressFactoryImpl(
    private val coincore: Coincore
) : AddressFactory {

    /** Build the set of possible address for a given input string.
     * If the string is not a valid address fir any available tokens, then return
     * an empty set
     **/
    override fun parse(address: String): Single<Set<ReceiveAddress>> =
        Maybe.merge(
            coincore.allAssets.map { asset ->
                asset.parseAddress(address)
                    .doOnError { Timber.e("**** ERROR: $asset") }
                    .onErrorComplete()
            }
        ).toList().map { it.toSet() }

    override fun parse(address: String, ccy: CryptoCurrency): Maybe<ReceiveAddress> =
        coincore[ccy].parseAddress(address)
}
