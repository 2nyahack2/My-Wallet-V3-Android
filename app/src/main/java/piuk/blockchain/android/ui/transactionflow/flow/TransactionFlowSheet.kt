package piuk.blockchain.android.ui.transactionflow.flow

import android.content.DialogInterface
import android.os.Bundle
import androidx.annotation.StringRes
import org.koin.android.ext.android.inject
import piuk.blockchain.android.ui.base.mvi.MviBottomSheet
import piuk.blockchain.android.ui.transactionflow.analytics.TxFlowAnalytics
import piuk.blockchain.android.ui.transactionflow.engine.TransactionIntent
import piuk.blockchain.android.ui.transactionflow.engine.TransactionModel
import piuk.blockchain.android.ui.transactionflow.engine.TransactionState
import piuk.blockchain.android.ui.transactionflow.transactionInject
import piuk.blockchain.androidcoreui.ui.customviews.ToastCustom

abstract class TransactionFlowSheet : MviBottomSheet<TransactionModel, TransactionIntent, TransactionState>() {

    override val model: TransactionModel by transactionInject()
    private val activeTransactionFlow: ActiveTransactionFlow by transactionInject()

    protected var state: TransactionState = TransactionState()
        private set

    protected val analyticsHooks: TxFlowAnalytics by inject()

    override val host: Host
        get() = activeTransactionFlow.getFlow()

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        savedInstanceState?.let {
            dismiss()
            model.process(TransactionIntent.ResetFlow)
        }
    }

    protected fun showErrorToast(@StringRes msgId: Int) {
        ToastCustom.makeText(
            activity,
            getString(msgId),
            ToastCustom.LENGTH_LONG,
            ToastCustom.TYPE_ERROR
        )
    }

    override fun onCancel(dialog: DialogInterface) {
        analyticsHooks.onFlowCanceled(state)
        super.onCancel(dialog)
    }

    protected fun cacheState(newState: TransactionState) {
        state = newState
    }
}