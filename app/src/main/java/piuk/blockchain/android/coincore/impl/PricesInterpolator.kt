package piuk.blockchain.android.coincore.impl

import com.blockchain.nabu.datamanagers.CurrencyPair
import com.blockchain.nabu.datamanagers.PriceTier
import info.blockchain.balance.Money
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.util.Currency

class PricesInterpolator(
    private val interpolator: Interpolator = LinearInterpolator(),
    private val pair: CurrencyPair,
    list: List<PriceTier>
) {
    private val prices: List<PriceTier>

    init {
        prices = list.toMutableList().apply {
            this.add(0, PriceTier(pair.toSourceMoney(BigInteger.ZERO), pair.toSourceMoney(BigInteger.ZERO)))
        }.toList()
    }

    fun getRate(amount: Money): Money {
        prices.forEachIndexed { index, priceTier ->
            if (index == prices.size - 1) return priceTier.price

            val nextTier = prices[index + 1]
            val thisVol = priceTier.volume
            val nextVol = nextTier.volume

            if (thisVol < amount && amount <= nextVol) {
                if (index == 0) {
                    return nextTier.price
                }
                return pair.toDestinationMoney(
                    interpolator.interpolate(
                        listOf(priceTier.volume.toBigDecimal(), nextTier.volume.toBigDecimal()),
                        listOf(priceTier.price.toBigDecimal(), nextTier.price.toBigDecimal()),
                        amount.toBigDecimal(),
                        when (pair) {
                            is CurrencyPair.CryptoCurrencyPair -> pair.destination.dp
                            is CurrencyPair.CryptoToFiatCurrencyPair ->
                                Currency.getInstance(pair.destination).defaultFractionDigits
                        }
                    ))
            }
        }
        return pair.toDestinationMoney(BigInteger.ZERO)
    }
}

class LinearInterpolator : Interpolator {

    override fun interpolate(x: List<BigDecimal>, y: List<BigDecimal>, xi: BigDecimal, scale: Int): BigDecimal {
        require(x.size == y.size) { "Should be same size" }
        require(x.size == 2) { "Should contain two points" }
        require(x.zipWithNext().all { it.first <= it.second }) { "$x Should be sorted" }
        require(xi >= x[0] && xi <= x[1]) { "$xi Should be between ${x[0]} and ${x[1]}" }
        return (((xi - x[0]) * (y[1] - y[0])).divide(x[1] - x[0], scale, RoundingMode.HALF_UP)) + y[0]
        // Formula：Y = ( ( X - X1 )( Y2 - Y1) / ( X2 - X1) ) + Y1
        // X1, Y1 = first value, X2, Y2 = second value, X = target value, Y = result
    }
}

interface Interpolator {
    fun interpolate(x: List<BigDecimal>, y: List<BigDecimal>, xi: BigDecimal, scale: Int): BigDecimal
}