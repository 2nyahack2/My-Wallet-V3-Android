package piuk.blockchain.android.ui.customviews

import android.content.Context
import android.text.Editable
import android.util.AttributeSet
import androidx.constraintlayout.widget.ConstraintLayout
import com.blockchain.koin.scopedInject
import com.blockchain.preferences.CurrencyPrefs
import info.blockchain.balance.CryptoCurrency
import info.blockchain.balance.CryptoValue
import info.blockchain.balance.ExchangeRate
import info.blockchain.balance.FiatValue
import info.blockchain.balance.Money
import io.reactivex.Observable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.subjects.PublishSubject
import kotlinx.android.synthetic.main.enter_fiat_crypto_layout.view.*
import org.koin.core.KoinComponent
import org.koin.core.inject
import piuk.blockchain.android.R
import piuk.blockchain.androidcore.data.exchangerate.ExchangeRateDataManager
import piuk.blockchain.androidcore.utils.helperfunctions.unsafeLazy
import piuk.blockchain.android.ui.customviews.inputview.DecimalDigitsInputFilter
import piuk.blockchain.android.util.afterMeasured
import piuk.blockchain.android.util.gone
import piuk.blockchain.android.util.visible
import piuk.blockchain.android.util.visibleIf
import piuk.blockchain.android.util.AfterTextChangedWatcher
import java.math.RoundingMode
import java.text.DecimalFormatSymbols
import java.util.Locale
import java.util.Currency
import kotlin.properties.Delegates

class FiatCryptoInputView(context: Context, attrs: AttributeSet) : ConstraintLayout(context, attrs), KoinComponent {

    val onImeAction: Observable<PrefixedOrSuffixedEditText.ImeOptions> by lazy {
        enter_amount.onImeAction
    }

    private val amountSubject: PublishSubject<Money> = PublishSubject.create()

    private val inputToggleSubject: PublishSubject<CurrencyType> = PublishSubject.create()

    val onInputToggle: Observable<CurrencyType>
        get() = inputToggleSubject

    val amount: Observable<Money>
        get() = amountSubject

    private val defExchangeRateDataManager: ExchangeRateDataManager by scopedInject()

    private val defCryptoToFiat: ExchangeRate.CryptoToFiat
        get() = ExchangeRate.CryptoToFiat(
            cryptoCurrency,
            configuration.fiatCurrency,
            defExchangeRateDataManager.getLastPrice(cryptoCurrency, configuration.fiatCurrency)
        )

    private val currencyPrefs: CurrencyPrefs by inject()

    var exchangeRate: ExchangeRate.CryptoToFiat? = null
        set(value) {
            field = value
            updateExchangeAmountAndOutput()
        }

    private val cryptoToFiatRate: ExchangeRate.CryptoToFiat
        get() = exchangeRate ?: defCryptoToFiat

    val cryptoCurrency: CryptoCurrency
        get() = configuration.cryptoCurrency ?: throw IllegalStateException("Cryptocurrency not set")

    private val compositeDisposable = CompositeDisposable()

    init {
        inflate(context, R.layout.enter_fiat_crypto_layout, this)

        compositeDisposable += enter_amount.textSize.subscribe { textSize ->
            if (enter_amount.text.toString() == enter_amount.configuration.prefixOrSuffix) {
                placeFakeHint(textSize, enter_amount.configuration.isPrefix)
            } else
                fake_hint.gone()
        }

        enter_amount.addTextChangedListener(object : AfterTextChangedWatcher() {
            override fun afterTextChanged(s: Editable?) {
                if (configuration.isInitialised.not())
                    return
                updateExchangeAmountAndOutput()
            }
        })

        currency_swap.setOnClickListener {
            configuration =
                configuration.copy(
                    input = configuration.input.swap(),
                    output = configuration.output.swap(),
                    predefinedAmount = getLastEnteredAmount(configuration)
                )
            inputToggleSubject.onNext(configuration.input)
        }
    }

    private fun placeFakeHint(textSize: Int, hasPrefix: Boolean) {
        fake_hint.visible()
        fake_hint.afterMeasured {
            it.translationX =
                if (hasPrefix) (enter_amount.width / 2f + textSize / 2f) +
                        resources.getDimensionPixelOffset(R.dimen.smallest_margin) else
                    enter_amount.width / 2f - textSize / 2f - it.width -
                            resources.getDimensionPixelOffset(R.dimen.smallest_margin)
        }
    }

    val isConfigured: Boolean
        get() = configuration.isInitialised

    private fun getLastEnteredAmount(configuration: FiatCryptoViewConfiguration): Money =
        enter_amount.bigDecimalValue?.let { enterAmount ->
            if (configuration.input == CurrencyType.Fiat) FiatValue.fromMajor(configuration.fiatCurrency, enterAmount)
            else CryptoValue.fromMajor(cryptoCurrency, enterAmount)
        } ?: FiatValue.zero(configuration.fiatCurrency)

    var configuration: FiatCryptoViewConfiguration by Delegates.observable(
        FiatCryptoViewConfiguration(
            input = CurrencyType.Fiat,
            output = CurrencyType.Crypto,
            fiatCurrency = currencyPrefs.selectedFiatCurrency,
            cryptoCurrency = null)
    ) { _, oldValue, newValue ->
        if (oldValue != newValue) {

            enter_amount.filters = emptyArray()
            val fiatSymbol = Currency.getInstance(newValue.fiatCurrency).getSymbol(Locale.getDefault())
            val cryptoSymbol = cryptoCurrency.displayTicker
            currency_swap.visibleIf { newValue.canSwap }

            if (newValue.input == CurrencyType.Fiat) {
                updateFilters(fiatSymbol)
                fake_hint.text = FiatValue.zero(newValue.fiatCurrency).toStringWithoutSymbol()
                enter_amount.configuration = Configuration(
                    prefixOrSuffix = fiatSymbol,
                    isPrefix = true,
                    initialText = newValue.predefinedAmount.inFiat().toStringWithoutSymbol()
                        .replace(DecimalFormatSymbols(Locale.getDefault()).groupingSeparator.toString(), "")
                        .removeSuffix("${DecimalFormatSymbols(Locale.getDefault()).decimalSeparator}00")
                )
            } else {
                updateFilters(cryptoSymbol)
                fake_hint.text = newValue.cryptoCurrency?.let { CryptoValue.zero(it).toStringWithoutSymbol() }
                enter_amount.configuration = Configuration(
                    prefixOrSuffix = cryptoSymbol,
                    isPrefix = false,
                    initialText = newValue.predefinedAmount.inCrypto().toStringWithoutSymbol()
                        .replace(DecimalFormatSymbols(Locale.getDefault()).groupingSeparator.toString(), "")
                )
            }
            enter_amount.resetForTyping()
        }
    }

    var maxLimit by Delegates.observable<Money?>(null) { _, oldValue, newValue ->
        if (newValue != oldValue)
            updateFilters(enter_amount.configuration.prefixOrSuffix)
    }

    fun showError(errorMessage: String, shouldDisableInput: Boolean = false) {
        error.text = errorMessage
        error.visible()
        info.gone()
        hideExchangeAmount()
        exchange_amount.isEnabled = !shouldDisableInput
    }

    fun showInfo(infoMessage: String, onClick: () -> Unit) {
        info.text = infoMessage
        error.gone()
        info.visible()
        info.setOnClickListener {
            onClick()
        }
        hideExchangeAmount()
    }

    private fun hideExchangeAmount() {
        exchange_amount.gone()
    }

    fun hideLabels() {
        error.gone()
        info.gone()
        showExchangeAmount()
    }

    private fun showExchangeAmount() {
        exchange_amount.visible()
    }

    fun showValue(money: Money) {
        configuration = configuration.copy(
            predefinedAmount = money
        )
    }

    private fun updateFilters(prefixOrSuffix: String) {
        if (configuration.input == CurrencyType.Fiat) {
            val maxDecimalDigitsForAmount = maxLimit?.inFiat()?.userDecimalPlaces ?: return
            val maxIntegerDigitsForAmount = maxLimit?.inFiat()?.toStringParts()?.major?.length ?: return
            enter_amount.addFilter(maxDecimalDigitsForAmount, maxIntegerDigitsForAmount, prefixOrSuffix)
        } else {
            val maxDecimalDigitsForAmount = maxLimit?.inCrypto()?.userDecimalPlaces ?: return
            val maxIntegerDigitsForAmount = maxLimit?.inCrypto()?.toStringParts()?.major?.length ?: return
            enter_amount.addFilter(maxDecimalDigitsForAmount, maxIntegerDigitsForAmount, prefixOrSuffix)
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

    private fun Money.inFiat(): FiatValue =
        when (this) {
            is CryptoValue -> cryptoToFiatRate.convert(this) as FiatValue
            is FiatValue -> this
            else -> throw IllegalStateException("Illegal money type")
        }

    private fun Money.inCrypto(): CryptoValue =
        when (this) {
            is FiatValue -> cryptoToFiatRate.inverse(RoundingMode.CEILING, cryptoCurrency.userDp)
                .convert(this) as CryptoValue
            is CryptoValue -> this
            else -> throw IllegalStateException("Illegal money type")
        }

    private fun CurrencyType.swap(): CurrencyType =
        if (this == CurrencyType.Fiat) CurrencyType.Crypto else CurrencyType.Fiat

    private fun updateExchangeAmountAndOutput() {
        if (configuration.input == CurrencyType.Fiat) {

            val fiatAmount = enter_amount.bigDecimalValue?.let { amount ->
                FiatValue.fromMajor(configuration.fiatCurrency, amount)
            } ?: FiatValue.zero(configuration.fiatCurrency)

            val cryptoAmount = cryptoToFiatRate.inverse(RoundingMode.CEILING, cryptoCurrency.userDp).convert(fiatAmount)
            exchange_amount.text = cryptoAmount.toStringWithSymbol()

            amountSubject.onNext(
                if (configuration.output == CurrencyType.Fiat) fiatAmount else cryptoAmount
            )
        } else {
            val cryptoAmount = enter_amount.bigDecimalValue?.let { amount ->
                CryptoValue.fromMajor(cryptoCurrency, amount)
            } ?: CryptoValue.zero(cryptoCurrency)

            val fiatAmount = cryptoToFiatRate.convert(cryptoAmount)

            exchange_amount.text = fiatAmount.toStringWithSymbol()
            amountSubject.onNext(
                if (configuration.output == CurrencyType.Fiat) fiatAmount else cryptoAmount
            )
        }
    }

    fun fixExchange(it: Money) {
        exchange_amount.text = it.toStringWithSymbol()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        compositeDisposable.clear()
    }
}

data class FiatCryptoViewConfiguration(
    val fiatCurrency: String,
    val cryptoCurrency: CryptoCurrency?,
    val predefinedAmount: Money = FiatValue.zero(fiatCurrency),
    val input: CurrencyType = (predefinedAmount as? FiatValue)?.let { CurrencyType.Fiat } ?: CurrencyType.Crypto,
    val output: CurrencyType = (predefinedAmount as? FiatValue)?.let { CurrencyType.Fiat } ?: CurrencyType.Crypto,
    val canSwap: Boolean = true
) {
    val isInitialised: Boolean by unsafeLazy {
        cryptoCurrency != null
    }
}

enum class CurrencyType {
    Fiat,
    Crypto
}