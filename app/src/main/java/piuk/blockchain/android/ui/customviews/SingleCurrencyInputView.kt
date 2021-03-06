package piuk.blockchain.android.ui.customviews

import android.content.Context
import android.text.Editable
import android.util.AttributeSet
import androidx.constraintlayout.widget.ConstraintLayout
import com.blockchain.preferences.CurrencyPrefs
import info.blockchain.balance.CryptoCurrency
import info.blockchain.balance.CryptoValue
import info.blockchain.balance.FiatValue
import info.blockchain.balance.Money
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import kotlinx.android.synthetic.main.enter_fiat_crypto_layout.view.*
import org.koin.core.KoinComponent
import org.koin.core.inject
import piuk.blockchain.android.R
import piuk.blockchain.android.ui.customviews.inputview.DecimalDigitsInputFilter
import piuk.blockchain.android.util.gone
import piuk.blockchain.android.util.visible
import piuk.blockchain.android.util.AfterTextChangedWatcher
import java.text.DecimalFormatSymbols
import java.util.Currency
import java.util.Locale
import kotlin.properties.Delegates

class SingleCurrencyInputView(context: Context, attrs: AttributeSet) : ConstraintLayout(context, attrs),
    KoinComponent {

    val onImeAction: Observable<PrefixedOrSuffixedEditText.ImeOptions> by lazy {
        enter_amount.onImeAction
    }

    private val amountSubject: PublishSubject<Money> = PublishSubject.create()
    private val currencyPrefs: CurrencyPrefs by inject()

    val amount: Observable<Money>
        get() = amountSubject

    val isConfigured: Boolean
        get() = configuration != SingleInputViewConfiguration.Undefined

    init {
        inflate(context, R.layout.enter_fiat_crypto_layout, this)
        exchange_amount.gone()

        enter_amount.addTextChangedListener(object : AfterTextChangedWatcher() {
            override fun afterTextChanged(s: Editable?) {
                configuration.let {
                    when (it) {
                        is SingleInputViewConfiguration.Fiat -> {
                            val fiatAmount = enter_amount.bigDecimalValue?.let { amount ->
                                FiatValue.fromMajor(it.fiatCurrency, amount)
                            } ?: FiatValue.zero(it.fiatCurrency)
                            amountSubject.onNext(fiatAmount)
                        }
                        is SingleInputViewConfiguration.Crypto -> {
                            val cryptoAmount = enter_amount.bigDecimalValue?.let { amount ->
                                CryptoValue.fromMajor(it.cryptoCurrency, amount)
                            } ?: CryptoValue.zero(it.cryptoCurrency)

                            amountSubject.onNext(cryptoAmount)
                        }
                        is SingleInputViewConfiguration.Undefined -> {
                        }
                    }
                }
            }
        })
    }

    var maxLimit by Delegates.observable<Money>(FiatValue.fromMinor(currencyPrefs.defaultFiatCurrency,
        Long.MAX_VALUE)) { _, oldValue, newValue ->
        if (newValue != oldValue)
            updateFilters(enter_amount.configuration.prefixOrSuffix)
    }

    private fun updateFilters(prefixOrSuffix: String) {
        val maxDecimalDigitsForAmount = maxLimit.userDecimalPlaces
        val maxIntegerDigitsForAmount = maxLimit.toStringParts().major.length
        enter_amount.addFilter(maxDecimalDigitsForAmount, maxIntegerDigitsForAmount, prefixOrSuffix)
    }

    var configuration by Delegates.observable<SingleInputViewConfiguration>(
        SingleInputViewConfiguration.Undefined) { _, _, newValue ->
        enter_amount.filters = emptyArray()

        when (newValue) {
            is SingleInputViewConfiguration.Fiat -> {
                val fiatSymbol = Currency.getInstance(newValue.fiatCurrency).getSymbol(Locale.getDefault())
                updateFilters(fiatSymbol)
                enter_amount.configuration = Configuration(
                    prefixOrSuffix = fiatSymbol,
                    isPrefix = true,
                    initialText = newValue.predefinedAmount.toStringWithoutSymbol()
                        .replace(DecimalFormatSymbols(Locale.getDefault()).groupingSeparator.toString(), "")
                        .removeSuffix("${DecimalFormatSymbols(Locale.getDefault()).decimalSeparator}00")
                )
                amountSubject.onNext(
                    newValue.predefinedAmount
                )
            }
            is SingleInputViewConfiguration.Crypto -> {
                val cryptoSymbol = newValue.cryptoCurrency.displayTicker
                updateFilters(cryptoSymbol)
                enter_amount.configuration = Configuration(
                    prefixOrSuffix = cryptoSymbol,
                    isPrefix = false,
                    initialText = newValue.predefinedAmount.toStringWithoutSymbol()
                        .replace(DecimalFormatSymbols(Locale.getDefault()).groupingSeparator.toString(), "")
                )
                amountSubject.onNext(
                    newValue.predefinedAmount
                )
            }
            SingleInputViewConfiguration.Undefined -> {
            }
        }
    }

    private fun PrefixedOrSuffixedEditText.addFilter(
        maxDecimalDigitsForAmount: Int,
        maxIntegerDigitsForAmount: Int,
        prefixOrSuffix: String
    ) {
        filters =
            arrayOf(
                DecimalDigitsInputFilter(
                    digitsAfterZero = maxDecimalDigitsForAmount,
                    prefixOrSuffix = prefixOrSuffix
                )
            )
    }

    fun showError(errorMessage: String) {
        error.text = errorMessage
        error.visible()
        exchange_amount.gone()
        currency_swap.let {
            it.isEnabled = false
            it.alpha = .6f
        }
    }

    fun hideError() {
        error.gone()
        exchange_amount.visible()
        currency_swap.let {
            it.isEnabled = true
            it.alpha = 1f
        }
    }
}

sealed class SingleInputViewConfiguration {
    object Undefined : SingleInputViewConfiguration()
    data class Fiat(
        val fiatCurrency: String,
        val predefinedAmount: FiatValue = FiatValue.zero(fiatCurrency)
    ) : SingleInputViewConfiguration()

    data class Crypto(
        val cryptoCurrency: CryptoCurrency,
        val predefinedAmount: CryptoValue = CryptoValue.zero(cryptoCurrency)
    ) : SingleInputViewConfiguration()
}