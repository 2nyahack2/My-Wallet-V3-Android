package piuk.blockchain.android.coincore.btc

import info.blockchain.balance.CryptoCurrency
import info.blockchain.balance.CryptoValue
import info.blockchain.wallet.multiaddress.TransactionSummary
import io.reactivex.Completable
import io.reactivex.Observable
import piuk.blockchain.android.coincore.CryptoAccount
import piuk.blockchain.android.coincore.NonCustodialActivitySummaryItem
import piuk.blockchain.androidcore.data.exchangerate.ExchangeRateDataManager
import piuk.blockchain.androidcore.data.payload.PayloadDataManager
import piuk.blockchain.androidcore.utils.helperfunctions.unsafeLazy

internal class BtcActivitySummaryItem(
    private val transactionSummary: TransactionSummary,
    private val payloadDataManager: PayloadDataManager,
    override val exchangeRates: ExchangeRateDataManager,
    override val account: CryptoAccount
) : NonCustodialActivitySummaryItem() {

    override val cryptoCurrency = CryptoCurrency.BTC

    override val transactionType: TransactionSummary.TransactionType
        get() = transactionSummary.transactionType

    override val timeStampMs = transactionSummary.time * 1000

    override val value: CryptoValue by unsafeLazy {
        CryptoValue.fromMinor(CryptoCurrency.BTC, transactionSummary.total)
    }

    override val description: String?
        get() = payloadDataManager.getTransactionNotes(txId)

    override val fee: Observable<CryptoValue>
        get() = Observable.just(CryptoValue.fromMinor(CryptoCurrency.BTC, transactionSummary.fee))

    override val txId: String
        get() = transactionSummary.hash

    override val inputsMap: Map<String, CryptoValue>
        get() = transactionSummary.inputsMap
            .mapValues {
                CryptoValue.fromMinor(CryptoCurrency.BTC, it.value)
            }

    override val outputsMap: Map<String, CryptoValue>
        get() = transactionSummary.outputsMap
            .mapValues {
                CryptoValue.fromMinor(CryptoCurrency.BTC, it.value)
            }

    override val confirmations: Int
        get() = transactionSummary.confirmations

    override val doubleSpend: Boolean
        get() = transactionSummary.isDoubleSpend

    override val isPending: Boolean
        get() = transactionSummary.isPending

    override fun updateDescription(description: String): Completable =
        payloadDataManager.updateTransactionNotes(txId, description)
}
