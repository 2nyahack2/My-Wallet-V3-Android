package piuk.blockchain.android.ui.transactionflow.flow

import android.view.View
import io.reactivex.Single
import kotlinx.android.synthetic.main.dialog_sheet_account_selector.view.*
import org.koin.android.ext.android.inject
import piuk.blockchain.android.R
import piuk.blockchain.android.coincore.BlockchainAccount
import piuk.blockchain.android.coincore.SingleAccount
import piuk.blockchain.android.ui.customviews.account.CellDecorator
import piuk.blockchain.android.ui.transactionflow.engine.TransactionIntent
import piuk.blockchain.android.ui.transactionflow.engine.TransactionState
import piuk.blockchain.android.util.gone
import piuk.blockchain.android.util.visible
import piuk.blockchain.android.util.visibleIf

class SelectTargetAccountSheet : TransactionFlowSheet() {

    private val customiser: TransactionFlowCustomiser by inject()

    override fun render(newState: TransactionState) {
        with(dialogView) {
            account_list.initialise(
                source = Single.just(newState.availableTargets.map { it as SingleAccount }),
                status = ::statusDecorator
            )
            account_list_title.text = customiser.selectTargetAccountTitle(newState)
            account_list_subtitle.text = customiser.selectTargetAccountDescription(newState)
            account_list_subtitle.visible()
            account_list_back.visibleIf { newState.canGoBack }
        }
    }

    private fun statusDecorator(account: BlockchainAccount): CellDecorator =
        customiser.selectTargetStatusDecorator(state, account)

    override val layoutResource: Int
        get() = R.layout.dialog_sheet_account_selector

    override fun initControls(view: View) {
        view.apply {
            account_list.onAccountSelected = ::doOnAccountSelected
            account_list.onListLoaded = ::doOnListLoaded
            account_list.onLoadError = ::doOnLoadError
            account_list_back.setOnClickListener {
                model.process(TransactionIntent.ReturnToPreviousStep)
            }
        }
    }

    private fun doOnListLoaded(isEmpty: Boolean) {
        dialogView.progress.gone()
    }

    private fun doOnAccountSelected(account: BlockchainAccount) {
        require(account is SingleAccount)
        model.process(TransactionIntent.TargetAccountSelected(account))
    }

    private fun doOnLoadError(it: Throwable) {
        dialogView.account_list_empty.visible()
        dialogView.progress.gone()
    }
}