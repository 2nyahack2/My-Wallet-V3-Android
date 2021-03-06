package piuk.blockchain.android.ui.transactionflow.flow

import android.util.DisplayMetrics
import android.view.View
import kotlinx.android.synthetic.main.dialog_tx_flow_in_progress.view.*
import org.koin.android.ext.android.inject
import piuk.blockchain.android.R
import piuk.blockchain.android.ui.transactionflow.engine.TransactionState
import piuk.blockchain.android.ui.transactionflow.engine.TransactionStep
import piuk.blockchain.android.ui.transactionflow.engine.TxExecutionStatus
import timber.log.Timber

class TransactionProgressSheet : TransactionFlowSheet() {
    override val layoutResource: Int = R.layout.dialog_tx_flow_in_progress

    private val customiser: TransactionFlowCustomiser by inject()
    private val MAX_STACKTRACE_CHARS = 400

    override fun render(newState: TransactionState) {
        Timber.d("!TRANSACTION!> Rendering! TransactionProgressSheet")
        require(newState.currentStep == TransactionStep.IN_PROGRESS)

        dialogView.tx_progress_view.setAssetIcon(customiser.transactionProgressIcon(newState))

        when (newState.executionStatus) {
            is TxExecutionStatus.InProgress -> dialogView.tx_progress_view.showTxInProgress(
                customiser.transactionProgressTitle(newState),
                customiser.transactionProgressMessage(newState)
            )
            is TxExecutionStatus.Completed -> {
                analyticsHooks.onTransactionSuccess(newState)
                dialogView.tx_progress_view.showTxSuccess(
                    customiser.transactionCompleteTitle(newState),
                    customiser.transactionCompleteMessage(newState)
                )
            }
            is TxExecutionStatus.Error -> {
                analyticsHooks.onTransactionFailure(
                    newState, collectStackTraceString(newState.executionStatus.exception)
                )
                dialogView.tx_progress_view.showTxError(
                    customiser.transactionProgressExceptionMessage(newState),
                    getString(R.string.send_progress_error_subtitle)
                )
            }
            else -> {
                // do nothing
            }
        }
        cacheState(newState)
    }

    private fun collectStackTraceString(e: Throwable): String {
        var stackTraceString = ""
        var index = 0
        while (stackTraceString.length <= MAX_STACKTRACE_CHARS && index < e.stackTrace.size) {
            stackTraceString += "${e.stackTrace[index]}\n"
            index++
        }
        Timber.d("Sending trace to analytics: $stackTraceString")
        return stackTraceString
    }

    override fun initControls(view: View) {
        view.tx_progress_view.onCtaClick {
            dismiss()
        }

        // this is needed to show the expanded dialog, with space at the top and bottom
        val metrics = DisplayMetrics()
        requireActivity().windowManager?.defaultDisplay?.getMetrics(metrics)
        dialogView.layoutParams.height = (metrics.heightPixels - (48 * metrics.density)).toInt()
        dialogView.requestLayout()
    }
}