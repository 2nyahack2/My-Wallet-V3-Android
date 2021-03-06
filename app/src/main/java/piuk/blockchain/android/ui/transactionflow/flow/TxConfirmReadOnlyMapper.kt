package piuk.blockchain.android.ui.transactionflow.flow

import android.content.res.Resources
import info.blockchain.balance.Money
import piuk.blockchain.android.R
import piuk.blockchain.android.coincore.TxConfirmationValue

class TxConfirmReadOnlyMapper(private val formatters: List<TxOptionsFormatter>) {
    fun map(property: TxConfirmationValue): Pair<String, String> {
        for (formatter in formatters) {
            formatter.format(property)?.let {
                return it
            }
        }
        throw IllegalStateException("No formatter found")
    }
}

interface TxOptionsFormatter {
    fun format(property: TxConfirmationValue): Pair<String, String>?
}

class FromPropertyFormatter(private val resources: Resources) : TxOptionsFormatter {

    override fun format(property: TxConfirmationValue): Pair<String, String>? =
        if (property is TxConfirmationValue.From)
            resources.getString(R.string.common_from) to property.from
        else null
}

class FeedTotalFormatter(private val resources: Resources) : TxOptionsFormatter {
    override fun format(property: TxConfirmationValue): Pair<String, String>? =
        if (property is TxConfirmationValue.FeedTotal)
            resources.getString(R.string.common_total) to totalAmount(
                property.amount,
                property.fee,
                property.exchangeAmount,
                property.exchangeFee
            )
        else null

    private fun totalAmount(money: Money, fee: Money, exchangeAmount: Money?, exchangeFee: Money?): String {
        return if (money.symbol == fee.symbol) {
            (money + fee).formatWithExchange(exchangeAmount)
        } else {
            money.formatWithExchange(exchangeAmount).plus(System.lineSeparator())
                .plus(fee.formatWithExchange(exchangeFee))
        }
    }
}

class ExchangePriceFormatter(private val resources: Resources) : TxOptionsFormatter {

    override fun format(property: TxConfirmationValue): Pair<String, String>? =
        if (property is TxConfirmationValue.ExchangePriceConfirmation) {
            resources.getString(R.string.quote_price,
                property.asset.displayTicker) to property.money.toStringWithSymbol()
        } else {
            null
        }
}

class SwapExchangeRateFormatter(private val resources: Resources) : TxOptionsFormatter {

    override fun format(property: TxConfirmationValue): Pair<String, String>? =
        if (property is TxConfirmationValue.SwapExchangeRate) {
            resources.getString(R.string.exchange_rate) to resources.getString(R.string.current_unit_price,
                property.unitCryptoCurrency.toStringWithSymbol(), property.price.toStringWithSymbol())
        } else {
            null
        }
}

class SwapReceiveFormatter(private val resources: Resources) : TxOptionsFormatter {

    override fun format(property: TxConfirmationValue): Pair<String, String>? =
        if (property is TxConfirmationValue.SwapReceiveValue) {
            resources.getString(R.string.receive) to property.receiveAmount.toStringWithSymbol()
        } else {
            null
        }
}

class TotalFormatter(private val resources: Resources) : TxOptionsFormatter {

    override fun format(property: TxConfirmationValue): Pair<String, String>? =
        if (property is TxConfirmationValue.Total)
            resources.getString(R.string.common_total) to property.total.formatWithExchange(property.exchange)
        else null
}

class ToPropertyFormatter(private val resources: Resources) : TxOptionsFormatter {

    override fun format(property: TxConfirmationValue): Pair<String, String>? =
        if (property is TxConfirmationValue.To)
            resources.getString(R.string.common_to) to property.to
        else null
}

class SwapSourcePropertyFormatter(private val resources: Resources) : TxOptionsFormatter {

    override fun format(property: TxConfirmationValue): Pair<String, String>? =
        if (property is TxConfirmationValue.SwapSourceValue)
            resources.getString(R.string.common_swap) to property.swappingAssetValue.toStringWithSymbol()
        else null
}

class SwapDestinationPropertyFormatter(private val resources: Resources) : TxOptionsFormatter {

    override fun format(property: TxConfirmationValue): Pair<String, String>? =
        if (property is TxConfirmationValue.SwapDestinationValue)
            resources.getString(R.string.common_receive) to property.receivingAssetValue.toStringWithSymbol()
        else null
}

fun Money.formatWithExchange(exchange: Money?) =
    exchange?.let {
        "${this.toStringWithSymbol()} (${it.toStringWithSymbol()})"
    } ?: this.toStringWithSymbol()