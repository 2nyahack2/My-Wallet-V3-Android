package piuk.blockchain.android.coincore.bch

import info.blockchain.balance.CryptoCurrency
import info.blockchain.balance.CryptoValue
import info.blockchain.balance.Money
import info.blockchain.wallet.multiaddress.TransactionSummary
import io.reactivex.Observable
import piuk.blockchain.android.coincore.CryptoAccount
import piuk.blockchain.android.coincore.NonCustodialActivitySummaryItem
import piuk.blockchain.androidcore.data.exchangerate.ExchangeRateDataManager

internal class BchActivitySummaryItem(
    private val transactionSummary: TransactionSummary,
    override val exchangeRates: ExchangeRateDataManager,
    override val account: CryptoAccount
) : NonCustodialActivitySummaryItem() {

    override val cryptoCurrency = CryptoCurrency.BCH
    override val transactionType: TransactionSummary.TransactionType = transactionSummary.transactionType
    override val timeStampMs: Long = transactionSummary.time * 1000

    override val value: Money = CryptoValue.fromMinor(CryptoCurrency.BCH, transactionSummary.total)

    override val description: String? = null

    override val fee: Observable<CryptoValue>
        get() = Observable.just(CryptoValue.fromMinor(CryptoCurrency.BCH, transactionSummary.fee))

    override val txId: String =
        transactionSummary.hash

    override val inputsMap: Map<String, CryptoValue>
        get() = transactionSummary.inputsMap.mapValues { CryptoValue.fromMinor(CryptoCurrency.BCH, it.value) }

    override val outputsMap: Map<String, CryptoValue>
        get() = transactionSummary.outputsMap.mapValues { CryptoValue.fromMinor(CryptoCurrency.BCH, it.value) }

    override val confirmations: Int
        get() = transactionSummary.confirmations

    override val doubleSpend: Boolean
        get() = transactionSummary.isDoubleSpend

    override val isPending: Boolean
        get() = transactionSummary.isPending
}
