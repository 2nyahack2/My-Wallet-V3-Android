package piuk.blockchain.android.ui.transactionflow.flow

import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import com.blockchain.koin.scopedInject
import com.blockchain.preferences.CurrencyPrefs
import info.blockchain.balance.ExchangeRates
import kotlinx.android.synthetic.main.dialog_tx_flow_confirm.view.*
import org.koin.android.ext.android.inject
import piuk.blockchain.android.R
import piuk.blockchain.android.ui.customviews.BlockchainListDividerDecor
import piuk.blockchain.android.ui.transactionflow.engine.TransactionIntent
import piuk.blockchain.android.ui.transactionflow.engine.TransactionState
import piuk.blockchain.android.ui.transactionflow.engine.TransactionStep
import piuk.blockchain.android.ui.transactionflow.flow.adapter.ConfirmTransactionDelegateAdapter
import piuk.blockchain.android.util.StringUtils
import piuk.blockchain.android.util.visible
import piuk.blockchain.android.util.visibleIf
import timber.log.Timber

class ConfirmTransactionSheet : TransactionFlowSheet() {
    override val layoutResource: Int = R.layout.dialog_tx_flow_confirm

    private val stringUtils: StringUtils by inject()
    private val exchangeRates: ExchangeRates by scopedInject()
    private val prefs: CurrencyPrefs by scopedInject()
    private val mapper: TxConfirmReadOnlyMapper by scopedInject()
    private val customiser: TransactionFlowCustomiser by inject()

    private val listAdapter: ConfirmTransactionDelegateAdapter by lazy {
        ConfirmTransactionDelegateAdapter(
            model = model,
            stringUtils = stringUtils,
            activityContext = requireActivity(),
            analytics = analyticsHooks,
            mapper = mapper,
            selectedCurrency = prefs.selectedFiatCurrency,
            exchangeRates = exchangeRates
        )
    }

    override fun render(newState: TransactionState) {
        Timber.d("!TRANSACTION!> Rendering! ConfirmTransactionSheet")
        require(newState.currentStep == TransactionStep.CONFIRM_DETAIL)

        // We _should_ always have a pending Tx when we get here
        newState.pendingTx?.let {
            listAdapter.items = newState.pendingTx.confirmations.toList()
            listAdapter.notifyDataSetChanged()
            dialogView.amount.text = newState.pendingTx.amount.toStringWithSymbol()
            dialogView.amount.visibleIf { customiser.amountHeaderConfirmationVisible(newState) }
        }

        with(dialogView) {
            confirm_cta_button.text = customiser.confirmCtaText(newState)
            confirm_sheet_title.text = customiser.confirmTitle(newState)
            confirm_cta_button.isEnabled = newState.nextEnabled
            confirm_sheet_back.visibleIf { newState.canGoBack }

            if (customiser.confirmDisclaimerVisibility(newState.action)) {
                confirm_disclaimer.visible()
                confirm_disclaimer.text = customiser.confirmDisclaimerBlurb(newState.action)
            }
        }
        cacheState(newState)
    }

    override fun initControls(view: View) {
        view.confirm_cta_button.setOnClickListener { onCtaClick() }

        with(view.confirm_details_list) {
            addItemDecoration(BlockchainListDividerDecor(requireContext()))

            layoutManager = LinearLayoutManager(
                requireContext(),
                LinearLayoutManager.VERTICAL,
                false
            )
            adapter = listAdapter
        }

        view.confirm_sheet_back.setOnClickListener {
            analyticsHooks.onStepBackClicked(state)
            model.process(TransactionIntent.ReturnToPreviousStep)
        }

        model.process(TransactionIntent.ValidateTransaction)
    }

    private fun onCtaClick() {
        analyticsHooks.onConfirmationCtaClick(state)
        model.process(TransactionIntent.ExecuteTransaction)
    }
}
