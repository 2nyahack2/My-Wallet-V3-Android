package piuk.blockchain.android.ui.transactionflow.flow

import android.view.View
import io.reactivex.Single
import kotlinx.android.synthetic.main.dialog_sheet_account_selector.view.*
import org.koin.android.ext.android.inject
import piuk.blockchain.android.R
import piuk.blockchain.android.coincore.CryptoAccount
import piuk.blockchain.android.ui.transactionflow.engine.TransactionIntent
import piuk.blockchain.android.ui.transactionflow.engine.TransactionState
import piuk.blockchain.android.util.gone
import piuk.blockchain.android.util.visible
import piuk.blockchain.android.util.visibleIf

class SelectSourceAccountSheet : TransactionFlowSheet() {

    private val customiser: TransactionFlowCustomiser by inject()
    private var availableSources = emptyList<CryptoAccount>()
    override fun render(newState: TransactionState) {
        if (availableSources == newState.availableSources) {
            // If list displays already the same accounts return so to avoid the annoying flickering
            return
        }
        with(dialogView) {
            account_list.initialise(
                source = Single.just(newState.availableSources.map { it }),
                status = customiser.sourceAccountSelectionStatusDecorator(newState)
            )
            account_list_title.text = customiser.selectSourceAccountTitle(newState)
            account_list_subtitle.text = customiser.selectSourceAccountSubtitle(newState)
            account_list_subtitle.visible()
            account_list_back.visibleIf { newState.canGoBack }
        }
        availableSources = newState.availableSources
    }

    override val layoutResource: Int
        get() = R.layout.dialog_sheet_account_selector

    override fun initControls(view: View) {
        view.apply {
            account_list.onAccountSelected = {
                require(it is CryptoAccount)
                model.process(TransactionIntent.SourceAccountSelected(it))
            }
            account_list_back.setOnClickListener {
                model.process(TransactionIntent.ReturnToPreviousStep)
            }
            account_list.onListLoaded = ::doOnListLoaded
            account_list.onLoadError = ::doOnLoadError
            account_list.onListLoading = ::doOnListLoading
        }
    }

    private fun doOnListLoaded(isEmpty: Boolean) {
        dialogView.account_list_empty.visibleIf { isEmpty }
        dialogView.progress.gone()
    }

    private fun doOnLoadError(it: Throwable) {
        dialogView.account_list_empty.visible()
        dialogView.progress.gone()
    }

    private fun doOnListLoading() {
        dialogView.account_list_empty.gone()
        dialogView.progress.visible()
    }
}