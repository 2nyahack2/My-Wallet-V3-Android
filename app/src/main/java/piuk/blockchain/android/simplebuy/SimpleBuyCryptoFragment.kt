package piuk.blockchain.android.simplebuy

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.TextView
import com.blockchain.extensions.exhaustive
import com.blockchain.koin.scopedInject
import com.blockchain.nabu.datamanagers.OrderState
import com.blockchain.nabu.datamanagers.PaymentMethod
import com.blockchain.nabu.datamanagers.custodialwalletimpl.PaymentMethodType
import com.blockchain.preferences.CurrencyPrefs
import info.blockchain.balance.CryptoCurrency
import info.blockchain.balance.FiatValue
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import kotlinx.android.synthetic.main.fragment_simple_buy_buy_crypto.*
import org.koin.android.ext.android.inject
import piuk.blockchain.android.R
import piuk.blockchain.android.cards.CardDetailsActivity
import piuk.blockchain.android.cards.CardDetailsActivity.Companion.ADD_CARD_REQUEST_CODE
import piuk.blockchain.android.cards.icon
import piuk.blockchain.android.ui.base.ErrorSlidingBottomDialog
import piuk.blockchain.android.ui.base.mvi.MviFragment
import piuk.blockchain.android.ui.base.setupToolbar
import piuk.blockchain.android.ui.customviews.CurrencyType
import piuk.blockchain.android.ui.customviews.FiatCryptoViewConfiguration
import piuk.blockchain.android.ui.customviews.PrefixedOrSuffixedEditText
import piuk.blockchain.android.ui.dashboard.sheets.LinkBankAccountDetailsBottomSheet
import piuk.blockchain.android.ui.linkbank.LinkBankActivity
import piuk.blockchain.android.util.assetName
import piuk.blockchain.android.util.drawableResFilled
import piuk.blockchain.android.util.gone
import piuk.blockchain.android.util.inflate
import piuk.blockchain.android.util.setAssetIconColours
import piuk.blockchain.android.util.visible
import piuk.blockchain.android.util.visibleIf
import piuk.blockchain.androidcore.data.exchangerate.ExchangeRateDataManager
import piuk.blockchain.androidcore.utils.helperfunctions.unsafeLazy

class SimpleBuyCryptoFragment : MviFragment<SimpleBuyModel, SimpleBuyIntent, SimpleBuyState>(),
    SimpleBuyScreen,
    PaymentMethodChangeListener,
    ChangeCurrencyHost {

    override val model: SimpleBuyModel by scopedInject()
    private val exchangeRateDataManager: ExchangeRateDataManager by scopedInject()

    private var lastState: SimpleBuyState? = null
    private val compositeDisposable = CompositeDisposable()

    private val cryptoCurrency: CryptoCurrency by unsafeLazy {
        arguments?.getSerializable(ARG_CRYPTO_CURRENCY) as? CryptoCurrency
            ?: throw IllegalArgumentException("No cryptoCurrency specified")
    }

    override fun navigator(): SimpleBuyNavigator =
        (activity as? SimpleBuyNavigator)
            ?: throw IllegalStateException("Parent must implement SimpleBuyNavigator")

    private val currencyPrefs: CurrencyPrefs by inject()

    override fun onBackPressed(): Boolean = true
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = container?.inflate(R.layout.fragment_simple_buy_buy_crypto)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)
        activity.setupToolbar(R.string.simple_buy_buy_crypto_title)

        model.process(SimpleBuyIntent.FetchBuyLimits(currencyPrefs.selectedFiatCurrency, cryptoCurrency))
        model.process(SimpleBuyIntent.FlowCurrentScreen(FlowScreen.ENTER_AMOUNT))
        model.process(SimpleBuyIntent.FetchSuggestedPaymentMethod(currencyPrefs.selectedFiatCurrency))
        model.process(SimpleBuyIntent.FetchSupportedFiatCurrencies)
        analytics.logEvent(SimpleBuyAnalytics.BUY_FORM_SHOWN)

        compositeDisposable += input_amount.amount.subscribe {
            when (it) {
                is FiatValue -> model.process(SimpleBuyIntent.AmountUpdated(it))
                else -> throw IllegalStateException("CryptoValue is not supported as input yet")
            }
        }

        btn_continue.setOnClickListener {
            startBuy()
        }

        payment_method_details_root.setOnClickListener {
            showPaymentMethodsBottomSheet(
                if (lastState?.paymentOptions?.availablePaymentMethods?.any { it.canUsedForPaying() } == true)
                    PaymentMethodsChooserState.AVAILABLE_TO_PAY
                else PaymentMethodsChooserState.AVAILABLE_TO_ADD
            )
        }

        compositeDisposable += input_amount.onImeAction.subscribe {
            when (it) {
                PrefixedOrSuffixedEditText.ImeOptions.NEXT -> {
                    startBuy()
                }
                else -> {
                    // do nothing
                }
            }
        }
    }

    override fun showAvailableToAddPaymentMethods() {
        showPaymentMethodsBottomSheet(PaymentMethodsChooserState.AVAILABLE_TO_ADD)
    }

    private fun showPaymentMethodsBottomSheet(state: PaymentMethodsChooserState) {
        lastState?.paymentOptions?.let {
            showBottomSheet(PaymentMethodChooserBottomSheet.newInstance(
                when (state) {
                    PaymentMethodsChooserState.AVAILABLE_TO_PAY -> it.availablePaymentMethods.filter { method ->
                        method.canUsedForPaying()
                    }
                    PaymentMethodsChooserState.AVAILABLE_TO_ADD -> it.availablePaymentMethods.filter { method ->
                        method.canBeAdded()
                    }
                }))
        }
    }

    private fun startBuy() {
        lastState?.let {
            if (canContinue(it)) {
                model.process(SimpleBuyIntent.BuyButtonClicked)
                model.process(SimpleBuyIntent.CancelOrderIfAnyAndCreatePendingOne)
                analytics.logEvent(
                    buyConfirmClicked(
                        lastState?.order?.amount?.valueMinor.toString(),
                        lastState?.fiatCurrency ?: "",
                        lastState?.selectedPaymentMethod?.paymentMethodType?.toAnalyticsString() ?: ""
                    )
                )
            }
        }
    }

    override fun onFiatCurrencyChanged(fiatCurrency: String) {
        if (fiatCurrency == lastState?.fiatCurrency) return
        model.process(SimpleBuyIntent.FiatCurrencyUpdated(fiatCurrency))
        model.process(
            SimpleBuyIntent.FetchBuyLimits(
                fiatCurrency,
                lastState?.selectedCryptoCurrency ?: return
            )
        )
        model.process(SimpleBuyIntent.FetchSuggestedPaymentMethod(currencyPrefs.selectedFiatCurrency))
        analytics.logEvent(CurrencyChangedFromBuyForm(fiatCurrency))
    }

    override fun render(newState: SimpleBuyState) {
        lastState = newState

        if (newState.errorState != null) {
            handleErrorState(newState.errorState)
            return
        }

        newState.selectedCryptoCurrency?.let {
            if (!input_amount.isConfigured) {
                input_amount.configuration = FiatCryptoViewConfiguration(
                    input = CurrencyType.Fiat,
                    output = CurrencyType.Fiat,
                    fiatCurrency = newState.fiatCurrency,
                    cryptoCurrency = it,
                    canSwap = false,
                    predefinedAmount = newState.order.amount ?: FiatValue.zero(newState.fiatCurrency)
                )
            }
            buy_icon.setAssetIconColours(it, activity)
        }
        newState.selectedCryptoCurrency?.let {
            crypto_icon.setImageResource(it.drawableResFilled())
            crypto_text.setText(it.assetName())
        }

        newState.exchangePrice?.let {
            crypto_exchange_rate.text = it.toStringWithSymbol()
        }

        input_amount.maxLimit = newState.maxFiatAmount

        newState.selectedPaymentMethodDetails?.let {
            renderPaymentMethod(it)
        } ?: hidePaymentMethod()

        btn_continue.isEnabled = canContinue(newState)
        newState.error?.let {
            handleError(it, newState)
        } ?: kotlin.run {
            clearError()
        }

        if (newState.confirmationActionRequested &&
            newState.kycVerificationState != null &&
            newState.orderState == OrderState.PENDING_CONFIRMATION
        ) {
            when (newState.kycVerificationState) {
                // Kyc state unknown because error, or gold docs unsubmitted
                KycState.PENDING -> {
                    startKyc()
                }
                // Awaiting results state
                KycState.IN_REVIEW,
                KycState.UNDECIDED -> {
                    navigator().goToKycVerificationScreen()
                }
                // Got results, kyc verification screen will show error
                KycState.VERIFIED_BUT_NOT_ELIGIBLE,
                KycState.FAILED -> {
                    navigator().goToKycVerificationScreen()
                }
                // We have done kyc and are verified
                KycState.VERIFIED_AND_ELIGIBLE -> {
                    if (newState.selectedPaymentMethod?.isActive() == true) {
                        navigator().goToCheckOutScreen()
                    } else
                        newState.selectedPaymentMethod?.paymentMethodType?.let {
                            goToAddNewPaymentMethod(it)
                        }
                }
            }.exhaustive
        }

        if (
            newState.depositFundsRequested &&
            newState.kycVerificationState != null
        ) {
            when (newState.kycVerificationState) {
                // Kyc state unknown because error, or gold docs unsubmitted
                KycState.PENDING -> {
                    startKyc()
                }
                // Awaiting results state
                KycState.IN_REVIEW,
                KycState.UNDECIDED,
                KycState.VERIFIED_BUT_NOT_ELIGIBLE,
                KycState.FAILED -> {
                    navigator().goToKycVerificationScreen()
                }
                // We have done kyc and are verified
                KycState.VERIFIED_AND_ELIGIBLE -> {
                    showBottomSheet(
                        LinkBankAccountDetailsBottomSheet.newInstance(
                            lastState?.fiatCurrency ?: return
                        )
                    )
                }
            }.exhaustive
            model.process(SimpleBuyIntent.DepositFundsHandled)
        }

        checkForPossibleBankLinkingRequest(newState)

        newState.linkBankTransfer?.let {
            model.process(SimpleBuyIntent.ResetLinkBankTransfer)
            startActivityForResult(
                LinkBankActivity.newInstance(it, requireContext()), LinkBankActivity.LINK_BANK_REQUEST_CODE
            )
        }
    }

    /**
     * Once User selects the option to Link a bank then his/her Kyc status is checked.
     * If is VERIFIED_AND_ELIGIBLE then we try to link a bank and if the fetched partner is supported
     * then the LinkBankActivity is launched.
     * In case that user is not VERIFIED_AND_ELIGIBLE then we just select the payment method and when
     * user presses Continue the KYC flow is launched
     */

    private fun checkForPossibleBankLinkingRequest(newState: SimpleBuyState) {
        if (newState.linkBankRequested) {
            if (newState.kycVerificationState == KycState.VERIFIED_AND_ELIGIBLE) {
                model.process(SimpleBuyIntent.LinkBankTransferRequested)
            } else {
                model.process(SimpleBuyIntent.SelectedPaymentMethodUpdate(
                    newState.paymentOptions.availablePaymentMethods.first {
                        it.id == PaymentMethod.UNDEFINED_BANK_TRANSFER_PAYMENT_ID
                    }
                ))
            }
        }
    }

    private fun startKyc() {
        model.process(SimpleBuyIntent.NavigationHandled)
        model.process(SimpleBuyIntent.KycStarted)
        navigator().startKyc()
        analytics.logEvent(SimpleBuyAnalytics.START_GOLD_FLOW)
    }

    private fun goToAddNewPaymentMethod(paymentMethod: PaymentMethodType) {
        when (paymentMethod) {
            PaymentMethodType.PAYMENT_CARD -> {
                addPaymentMethod(PaymentMethodType.PAYMENT_CARD)
            }
            PaymentMethodType.FUNDS -> {
                addPaymentMethod(PaymentMethodType.FUNDS)
            }
            PaymentMethodType.BANK_TRANSFER -> {
                addPaymentMethod(PaymentMethodType.BANK_TRANSFER)
            }
            else -> {
            }
        }
    }

    private fun hidePaymentMethod() {
        payment_method.gone()
        payment_method_separator.gone()
        payment_method_details_root.gone()
    }

    private fun canContinue(state: SimpleBuyState) =
        state.isAmountValid && state.selectedPaymentMethod?.id != PaymentMethod.UNDEFINED_PAYMENT_ID && !state.isLoading

    private fun renderPaymentMethod(selectedPaymentMethod: PaymentMethod) {
        when (selectedPaymentMethod) {
            is PaymentMethod.Undefined -> {
                payment_method_icon.setImageResource(R.drawable.ic_add_payment_method)
            }
            is PaymentMethod.Card -> renderCardPayment(selectedPaymentMethod)
            is PaymentMethod.Funds -> renderFundsPayment(selectedPaymentMethod)
            is PaymentMethod.Bank -> renderBankPayment(selectedPaymentMethod)
            is PaymentMethod.UndefinedCard -> renderUndefinedCardPayment(selectedPaymentMethod)
            is PaymentMethod.UndefinedBankTransfer -> renderUndefinedBankTransfer(selectedPaymentMethod)
            else -> {
            }
        }
        payment_method.visible()
        payment_method_separator.visible()
        payment_method_details_root.visible()
        undefined_payment_text.showIfPaymentMethodUndefined(selectedPaymentMethod)
        payment_method_title.showIfPaymentMethodDefined(selectedPaymentMethod)
        payment_method_limit.showIfPaymentMethodDefined(selectedPaymentMethod)
    }

    private fun renderFundsPayment(paymentMethod: PaymentMethod.Funds) {
        payment_method_bank_info.gone()
        payment_method_icon.setImageResource(
            paymentMethod.icon()
        )
        payment_method_title.text = getString(paymentMethod.label())

        payment_method_limit.text = paymentMethod.limits.max.toStringWithSymbol()
    }

    private fun renderBankPayment(paymentMethod: PaymentMethod.Bank) {
        payment_method_icon.setImageResource(R.drawable.ic_bank_transfer)
        payment_method_title.text = paymentMethod.bankName
        payment_method_bank_info.text =
            requireContext().getString(
                R.string.payment_method_type_account_info, paymentMethod.uiAccountType,
                paymentMethod.accountEnding
            )
        payment_method_bank_info.visible()
        payment_method_limit.text =
            getString(R.string.payment_method_limit, paymentMethod.limits.max.toStringWithSymbol())
    }

    private fun renderUndefinedCardPayment(selectedPaymentMethod: PaymentMethod.UndefinedCard) {
        payment_method_bank_info.gone()
        payment_method_icon.setImageResource(R.drawable.ic_payment_card)
        payment_method_title.text = getString(R.string.credit_or_debit_card)
        payment_method_limit.text =
            getString(R.string.payment_method_limit, selectedPaymentMethod.limits.max.toStringWithSymbol())
    }

    private fun renderUndefinedBankTransfer(selectedPaymentMethod: PaymentMethod.UndefinedBankTransfer) {
        payment_method_bank_info.gone()
        payment_method_icon.setImageResource(R.drawable.ic_bank_transfer)
        payment_method_title.text = getString(R.string.link_a_bank)
        payment_method_limit.text =
            getString(R.string.payment_method_limit, selectedPaymentMethod.limits.max.toStringWithSymbol())
    }

    private fun renderCardPayment(selectedPaymentMethod: PaymentMethod.Card) {
        payment_method_bank_info.gone()
        payment_method_icon.setImageResource(selectedPaymentMethod.cardType.icon())
        payment_method_title.text = selectedPaymentMethod.detailedLabel()
        payment_method_limit.text =
            getString(R.string.payment_method_limit, selectedPaymentMethod.limits.max.toStringWithSymbol())
    }

    private fun clearError() {
        input_amount.hideLabels()
    }

    private fun handleErrorState(errorState: ErrorState) {
        if (errorState == ErrorState.LinkedBankNotSupported) {
            model.process(SimpleBuyIntent.ClearError)
            model.process(SimpleBuyIntent.ClearAnySelectedPaymentMethods)
            navigator().launchBankLinkingWithError(errorState)
        } else {
            showBottomSheet(ErrorSlidingBottomDialog.newInstance(activity))
        }
    }

    private fun handleError(error: InputError, state: SimpleBuyState) {
        when (error) {
            InputError.ABOVE_MAX -> {
                input_amount.showError(
                    if (input_amount.configuration.input == CurrencyType.Fiat)
                        resources.getString(R.string.maximum_buy, state.maxFiatAmount.toStringWithSymbol())
                    else
                        resources.getString(
                            R.string.maximum_buy,
                            state.maxCryptoAmount(exchangeRateDataManager)?.toStringWithSymbol()
                        )
                )
            }
            InputError.BELOW_MIN -> {
                input_amount.showError(
                    if (input_amount.configuration.input == CurrencyType.Fiat)
                        resources.getString(R.string.minimum_buy, state.minFiatAmount.toStringWithSymbol())
                    else
                        resources.getString(
                            R.string.minimum_buy,
                            state.minCryptoAmount(exchangeRateDataManager)?.toStringWithSymbol()
                        )
                )
            }
        }
    }

    override fun onPause() {
        super.onPause()
        model.process(SimpleBuyIntent.NavigationHandled)
    }

    override fun onSheetClosed() {
        model.process(SimpleBuyIntent.ClearError)
    }

    override fun onPaymentMethodChanged(paymentMethod: PaymentMethod) {
        when (paymentMethod) {
            is PaymentMethod.UndefinedFunds -> depositFundsSelected()
            is PaymentMethod.UndefinedBankTransfer -> linkBankSelected()
            else -> {
                model.process(SimpleBuyIntent.SelectedPaymentMethodUpdate(paymentMethod))
                analytics.logEvent(
                    PaymentMethodSelected(
                        paymentMethod.toAnalyticsString()
                    )
                )
            }
        }
    }

    private fun linkBankSelected() {
        model.process(SimpleBuyIntent.LinkBankSelected)
    }

    private fun addPaymentMethod(type: PaymentMethodType) {
        when (type) {
            PaymentMethodType.PAYMENT_CARD -> {
                val intent = Intent(activity, CardDetailsActivity::class.java)
                startActivityForResult(intent, ADD_CARD_REQUEST_CODE)
            }
            PaymentMethodType.FUNDS -> {
                showBottomSheet(
                    LinkBankAccountDetailsBottomSheet.newInstance(
                        lastState?.fiatCurrency ?: return
                    )
                )
            }
            PaymentMethodType.BANK_TRANSFER -> {
                model.process(SimpleBuyIntent.LinkBankTransferRequested)
            }
            else -> {
            }
        }
        analytics.logEvent(PaymentMethodSelected(type.toAnalyticsString()))
    }

    private fun depositFundsSelected() {
        model.process(SimpleBuyIntent.DepositFundsRequested)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == ADD_CARD_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            model.process(
                SimpleBuyIntent.FetchSuggestedPaymentMethod(
                    currencyPrefs.selectedFiatCurrency,
                    (data?.extras?.getSerializable(CardDetailsActivity.CARD_KEY) as? PaymentMethod.Card)?.id
                )
            )
        }
        if (requestCode == LinkBankActivity.LINK_BANK_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            model.process(
                SimpleBuyIntent.FetchSuggestedPaymentMethod(
                    currencyPrefs.selectedFiatCurrency,
                    (data?.extras?.getString(LinkBankActivity.LINKED_BANK_ID_KEY))
                )
            )
        }
    }

    private fun TextView.showIfPaymentMethodDefined(paymentMethod: PaymentMethod) {
        visibleIf {
            paymentMethod !is PaymentMethod.Undefined
        }
    }

    private fun TextView.showIfPaymentMethodUndefined(paymentMethod: PaymentMethod) {
        visibleIf {
            paymentMethod is PaymentMethod.Undefined
        }
    }

    companion object {
        private const val ARG_CRYPTO_CURRENCY = "crypto"
        fun newInstance(cryptoCurrency: CryptoCurrency): SimpleBuyCryptoFragment {
            return SimpleBuyCryptoFragment().apply {
                arguments = Bundle().apply {
                    putSerializable(ARG_CRYPTO_CURRENCY, cryptoCurrency)
                }
            }
        }
    }

    private enum class PaymentMethodsChooserState {
        AVAILABLE_TO_PAY, AVAILABLE_TO_ADD
    }
}

interface PaymentMethodChangeListener {
    fun onPaymentMethodChanged(paymentMethod: PaymentMethod)
    fun showAvailableToAddPaymentMethods()
}

interface ChangeCurrencyHost : SimpleBuyScreen {
    fun onFiatCurrencyChanged(fiatCurrency: String)
}

fun PaymentMethod.Funds.icon() =
    when (fiatCurrency) {
        "GBP" -> R.drawable.ic_funds_gbp
        "EUR" -> R.drawable.ic_funds_euro
        "USD" -> R.drawable.ic_funds_usd
        else -> throw IllegalStateException("Unsupported currency")
    }

fun PaymentMethod.Funds.label() =
    when (fiatCurrency) {
        "GBP" -> R.string.pounds
        "EUR" -> R.string.euros
        "USD" -> R.string.us_dollars
        else -> throw IllegalStateException("Unsupported currency")
    }