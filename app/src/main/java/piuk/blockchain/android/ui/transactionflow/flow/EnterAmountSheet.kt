package piuk.blockchain.android.ui.transactionflow.flow

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.fragment.app.DialogFragment
import info.blockchain.balance.CryptoValue
import info.blockchain.balance.ExchangeRate
import info.blockchain.balance.FiatValue
import info.blockchain.balance.Money
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import kotlinx.android.synthetic.main.dialog_tx_flow_enter_amount.view.*
import org.koin.android.ext.android.inject
import piuk.blockchain.android.R
import piuk.blockchain.android.campaign.CampaignType
import piuk.blockchain.android.coincore.CryptoAccount
import piuk.blockchain.android.coincore.NullAddress
import piuk.blockchain.android.ui.customviews.CurrencyType
import piuk.blockchain.android.ui.customviews.FiatCryptoInputView
import piuk.blockchain.android.ui.customviews.FiatCryptoViewConfiguration
import piuk.blockchain.android.ui.customviews.PrefixedOrSuffixedEditText
import piuk.blockchain.android.ui.kyc.navhost.KycNavHostActivity
import piuk.blockchain.android.ui.transactionflow.engine.TransactionIntent
import piuk.blockchain.android.ui.transactionflow.engine.TransactionState
import piuk.blockchain.android.util.setAssetIconColours
import piuk.blockchain.android.util.setCoinIcon
import piuk.blockchain.android.util.gone
import piuk.blockchain.android.util.visible
import timber.log.Timber
import java.math.RoundingMode

class EnterAmountSheet : TransactionFlowSheet() {
    override val layoutResource: Int = R.layout.dialog_tx_flow_enter_amount

    private val customiser: TransactionFlowCustomiser by inject()
    private val compositeDisposable = CompositeDisposable()

    private val imm: InputMethodManager by lazy {
        requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(DialogFragment.STYLE_NORMAL, R.style.FloatingBottomSheet)
    }

    @SuppressLint("SetTextI18n")
    override fun render(newState: TransactionState) {
        Timber.d("!TRANSACTION!> Rendering! EnterAmountSheet")
        cacheState(newState)
        with(dialogView) {
            amount_sheet_cta_button.isEnabled = newState.nextEnabled

            if (!amount_sheet_input.isConfigured) {
                amount_sheet_input.configure(newState, customiser.defInputType(state))
            }

            val availableBalance = newState.availableBalance
            if (availableBalance.isPositive || availableBalance.isZero) {
                // The maxLimit set here controls the number of digits that can be entered,
                // but doesn't restrict the input to be always under that value. Which might be
                // strange UX, but is currently by design.
                if (amount_sheet_input.isConfigured) {
                    amount_sheet_input.maxLimit = newState.availableBalance
                    if (amount_sheet_input.exchangeRate != newState.fiatRate)
                        amount_sheet_input.exchangeRate = newState.fiatRate
                }

                newState.fiatRate?.let { rate ->
                    amount_sheet_max_available.text =
                        "${rate.convert(availableBalance).toStringWithSymbol()} " +
                            "(${availableBalance.toStringWithSymbol()})"
                }
            }

            amount_sheet_title.text = customiser.enterAmountTitle(newState)
            amount_sheet_use_max.text = customiser.enterAmountMaxButton(newState)
            if (customiser.shouldDisableInput(state.errorState)) {
                amount_sheet_use_max.gone()
            }
            updatePendingTxDetails(newState)

            customiser.issueFlashMessage(newState, amount_sheet_input.configuration.input)?.let {
                when (customiser.selectIssueType(newState)) {
                    IssueType.ERROR -> amount_sheet_input.showError(it, customiser.shouldDisableInput(state.errorState))
                    IssueType.INFO -> amount_sheet_input.showInfo(it) {
                        dismiss()
                        KycNavHostActivity.start(requireActivity(), CampaignType.Swap, true)
                    }
                }
            } ?: amount_sheet_input.hideLabels()

            if (!newState.canGoBack) {
                amount_sheet_back.gone()
            }
        }
    }

    override fun initControls(view: View) {
        view.apply {
            amount_sheet_use_max.setOnClickListener {
                analyticsHooks.onMaxClicked(state)
                onUseMaxClick()
            }
            amount_sheet_cta_button.setOnClickListener {
                analyticsHooks.onEnterAmountCtaClick(state)
                onCtaClick()
            }
            amount_sheet_back.setOnClickListener {
                analyticsHooks.onStepBackClicked(state)
                model.process(TransactionIntent.InvalidateTransaction)
            }

            amount_sheet_use_max.gone()
        }

        compositeDisposable += view.amount_sheet_input.amount.subscribe { amount ->
            state.fiatRate?.let { rate ->
                val px = state.pendingTx ?: throw IllegalStateException("Px is not initialised yet")
                model.process(
                    TransactionIntent.AmountChanged(
                        if (!state.allowFiatInput && amount is FiatValue) {
                            convertFiatToCrypto(amount, rate, state).also {
                                view.amount_sheet_input.fixExchange(it)
                            }
                        } else {
                            amount
                        }
                    )
                )
            }
        }

        compositeDisposable += view.amount_sheet_input.onImeAction.subscribe {
            when (it) {
                PrefixedOrSuffixedEditText.ImeOptions.NEXT -> {
                    if (state.nextEnabled) {
                        onCtaClick()
                    }
                }
                PrefixedOrSuffixedEditText.ImeOptions.BACK -> {
                    hideKeyboard()
                    dismiss()
                }
                else -> {
                    // do nothing
                }
            }
        }

        compositeDisposable += view.amount_sheet_input.onInputToggle.subscribe {
            analyticsHooks.onCryptoToggle(it, state)
        }
    }

    // in this method we try to convert the fiat value coming out from
    // the view to a crypto which is withing the min and max limits allowed.
    // We use floor rounding for max and ceiling for min just to make sure that we wont have problem with rounding once
    // the amount reach the engine where the comparison with limits will happen.

    private fun convertFiatToCrypto(
        amount: FiatValue,
        rate: ExchangeRate.CryptoToFiat,
        state: TransactionState
    ): Money {
        val min = state.pendingTx?.minLimit ?: return rate.inverse().convert(amount)
        val max = state.maxSpendable
        val roundedUpAmount = rate.inverse(RoundingMode.CEILING, state.asset.userDp)
            .convert(amount)
        val roundedDownAmount = rate.inverse(RoundingMode.FLOOR, state.asset.userDp)
            .convert(amount)
        return if (roundedUpAmount >= min && roundedUpAmount <= max)
            roundedUpAmount
        else roundedDownAmount
    }

    private fun updatePendingTxDetails(state: TransactionState) {
        with(dialogView) {
            amount_sheet_asset_icon.setCoinIcon(state.sendingAccount.asset)

            if (customiser.showTargetIcon(state)) {
                (state.selectedTarget as? CryptoAccount)?.let {
                    amount_sheet_target_icon.setCoinIcon(it.asset)
                }
            } else {
                amount_sheet_target_icon.gone()
            }

            amount_sheet_asset_direction.setImageResource(customiser.enterAmountActionIcon(state))
            if (customiser.enterAmountActionIconCustomisation(state)) {
                amount_sheet_asset_direction.setAssetIconColours(state.asset, requireContext())
            }
        }

        updateSourceAndTargetDetails(state)
    }

    private fun updateSourceAndTargetDetails(state: TransactionState) {
        if (state.selectedTarget is NullAddress)
            return
        with(dialogView) {
            amount_sheet_from.text = customiser.enterAmountSourceLabel(state)
            amount_sheet_to.text = customiser.enterAmountTargetLabel(state)
        }
    }

    private fun onUseMaxClick() {
        if (dialogView.amount_sheet_input.configuration.input == CurrencyType.Fiat &&
            state.maxSpendable is CryptoValue
        ) {
            dialogView.amount_sheet_input.configuration =
                dialogView.amount_sheet_input.configuration.copy(
                    input = CurrencyType.Crypto,
                    output = CurrencyType.Crypto
                )
        }
        dialogView.amount_sheet_input.showValue(
            state.maxSpendable
        )
    }

    private fun onCtaClick() {
        hideKeyboard()
        model.process(TransactionIntent.PrepareTransaction)
    }

    private fun hideKeyboard() {
        imm.hideSoftInputFromWindow(dialogView.windowToken, 0)
    }

    private fun FiatCryptoInputView.configure(newState: TransactionState, input: CurrencyType) {
        if (input == CurrencyType.Crypto || newState.amount.isPositive) {
            val selectedFiat = newState.pendingTx?.selectedFiat ?: return
            configuration = FiatCryptoViewConfiguration(
                input = CurrencyType.Crypto,
                output = CurrencyType.Crypto,
                fiatCurrency = selectedFiat,
                cryptoCurrency = newState.sendingAccount.asset,
                predefinedAmount = newState.amount
            )
        } else {
            val selectedFiat = newState.pendingTx?.selectedFiat ?: return
            val fiatRate = newState.fiatRate ?: return
            val amount = newState.amount as? CryptoValue ?: return
            configuration = FiatCryptoViewConfiguration(
                input = CurrencyType.Fiat,
                output = CurrencyType.Fiat,
                fiatCurrency = selectedFiat,
                cryptoCurrency = newState.sendingAccount.asset,
                predefinedAmount = fiatRate.applyRate(amount)
            )
        }
        showKeyboard()
        dialogView.amount_sheet_use_max.visible()
    }

    private fun showKeyboard() {
        val inputView = dialogView.amount_sheet_input.findViewById<PrefixedOrSuffixedEditText>(
            R.id.enter_amount
        )
        inputView?.run {
            requestFocus()
            imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
        }
    }
}