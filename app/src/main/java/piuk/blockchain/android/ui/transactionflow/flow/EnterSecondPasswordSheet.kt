package piuk.blockchain.android.ui.transactionflow.flow

import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import kotlinx.android.synthetic.main.dialog_tx_flow_password.view.*
import piuk.blockchain.android.R
import piuk.blockchain.android.ui.transactionflow.engine.TransactionErrorState
import piuk.blockchain.android.ui.transactionflow.engine.TransactionIntent
import piuk.blockchain.android.ui.transactionflow.engine.TransactionState
import piuk.blockchain.android.ui.transactionflow.engine.TransactionStep
import timber.log.Timber

class EnterSecondPasswordSheet : TransactionFlowSheet() {

    override val layoutResource: Int = R.layout.dialog_tx_flow_password

    override fun render(newState: TransactionState) {
        require(newState.currentStep == TransactionStep.ENTER_PASSWORD)

        if (newState.errorState == TransactionErrorState.INVALID_PASSWORD) {
            Toast.makeText(requireContext(), getString(R.string.invalid_password), Toast.LENGTH_SHORT).show()
        }

        Timber.d("!TRANSACTION!> Rendering! EnterSecondPasswordSheet")
        cacheState(newState)
    }

    override fun initControls(view: View) {
        view.cta_button.setOnClickListener { onCtaClick(view) }
        view.password_input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                onCtaClick(view)
            }
            true
        }
    }

    private fun onCtaClick(view: View) {
        model.process(TransactionIntent.ValidatePassword(view.password_input.text.toString()))
    }
}
