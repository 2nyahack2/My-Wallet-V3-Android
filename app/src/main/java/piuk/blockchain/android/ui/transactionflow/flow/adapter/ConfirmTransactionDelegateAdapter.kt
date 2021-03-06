package piuk.blockchain.android.ui.transactionflow.flow.adapter

import android.app.Activity
import info.blockchain.balance.ExchangeRates
import piuk.blockchain.android.ui.adapters.AdapterDelegatesManager
import piuk.blockchain.android.ui.adapters.DelegationAdapter
import piuk.blockchain.android.ui.transactionflow.analytics.TxFlowAnalytics
import piuk.blockchain.android.ui.transactionflow.engine.TransactionModel
import piuk.blockchain.android.ui.transactionflow.flow.TxConfirmReadOnlyMapper
import piuk.blockchain.android.util.StringUtils

class ConfirmTransactionDelegateAdapter(
    stringUtils: StringUtils,
    activityContext: Activity,
    model: TransactionModel,
    analytics: TxFlowAnalytics,
    mapper: TxConfirmReadOnlyMapper,
    exchangeRates: ExchangeRates,
    selectedCurrency: String
) : DelegationAdapter<Any>(AdapterDelegatesManager(), emptyList()) {
    init {
        // Add all necessary AdapterDelegate objects here
        with(delegatesManager) {
            addAdapterDelegate(ConfirmInfoItemDelegate(mapper))
            addAdapterDelegate(ConfirmNoteItemDelegate(model))
            addAdapterDelegate(ConfirmXlmMemoItemDelegate(model, stringUtils, activityContext))
            addAdapterDelegate(ConfirmAgreementWithTAndCsItemDelegate(model, stringUtils, activityContext))
            addAdapterDelegate(ConfirmAgreementToTransferItemDelegate(model, exchangeRates, selectedCurrency))
            addAdapterDelegate(LargeTransactionWarningItemDelegate(model))
            addAdapterDelegate(InvoiceCountdownTimerDelegate())
            addAdapterDelegate(ConfirmInfoItemValidationStatusDelegate())
            addAdapterDelegate(ConfirmInfoItemFeeOptionDelegate(model, analytics, activityContext, stringUtils))
            addAdapterDelegate(ConfirmNetworkFeeItemDelegate(activityContext, stringUtils))
        }
    }
}