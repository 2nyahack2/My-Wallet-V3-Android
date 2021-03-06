package piuk.blockchain.android.ui.customviews.account

import android.view.View
import androidx.annotation.StringRes
import com.blockchain.koin.scopedInject
import com.blockchain.notifications.analytics.activityShown
import io.reactivex.Single
import io.reactivex.disposables.CompositeDisposable
import kotlinx.android.synthetic.main.dialog_sheet_account_selector.*
import kotlinx.android.synthetic.main.dialog_sheet_account_selector.view.*
import piuk.blockchain.android.R
import piuk.blockchain.android.coincore.BlockchainAccount
import piuk.blockchain.android.coincore.Coincore
import piuk.blockchain.android.ui.base.SlidingModalBottomDialog
import piuk.blockchain.android.util.gone
import piuk.blockchain.android.util.visible
import piuk.blockchain.android.util.visibleIf

class AccountSelectSheet(
    override val host: Host
) : SlidingModalBottomDialog() {

    interface SelectionHost : Host {
        fun onAccountSelected(account: BlockchainAccount)
    }

    interface SelectAndBackHost : SelectionHost {
        fun onAccountSelectorBack()
    }

    override val layoutResource: Int
        get() = R.layout.dialog_sheet_account_selector

    private val coincore: Coincore by scopedInject()
    private val disposables = CompositeDisposable()

    private var accountList: Single<List<BlockchainAccount>> =
        coincore.allWallets()
            .map { listOf(it) + it.accounts }
            .map { it.filter { a -> a.hasTransactions } }

    private var sheetTitle: Int = R.string.select_account_sheet_title
    private var sheetSubtitle: Int = R.string.empty
    private var statusDecorator: StatusDecorator = { DefaultCellDecorator() }

    private fun doOnAccountSelected(account: BlockchainAccount) {
        analytics.logEvent(activityShown(account.label))
        (host as SelectionHost).onAccountSelected(account)
        dismiss()
    }

    private fun doOnListLoaded(isEmpty: Boolean) {
        dialogView.account_list_empty.visibleIf { isEmpty }
        dialogView.progress.gone()
    }

    private fun doOnLoadError(it: Throwable) {
        dialogView.progress.gone()
        dismiss()
    }

    private fun doOnListLoading() {
        dialogView.progress.visible()
    }

    override fun initControls(view: View) {
        with(view) {
            account_list.onAccountSelected = ::doOnAccountSelected
            account_list.onListLoaded = ::doOnListLoaded
            account_list.onLoadError = ::doOnLoadError
            account_list.onListLoading = ::doOnListLoading
            account_list_title.text = getString(sheetTitle)
            account_list_subtitle.text = getString(sheetSubtitle)
            account_list_subtitle.visibleIf { getString(sheetSubtitle).isNotEmpty() }

            if (host is SelectAndBackHost) {
                showBackArrow(view)
            } else {
                view.account_list_back.gone()
            }

            account_list.initialise(accountList, statusDecorator)
        }
    }

    private fun showBackArrow(view: View) {
        view.account_list_back.visible()
        view.account_list_back.setOnClickListener {
            (host as SelectAndBackHost).onAccountSelectorBack()
        }
    }

    override fun onSheetHidden() {
        super.onSheetHidden()
        disposables.dispose()
    }

    companion object {
        fun newInstance(host: Host): AccountSelectSheet = AccountSelectSheet(host)

        fun newInstance(
            host: SelectionHost,
            accountList: Single<List<BlockchainAccount>>,
            @StringRes sheetTitle: Int
        ): AccountSelectSheet =
            AccountSelectSheet(host).apply {
                this.accountList = accountList
                this.sheetTitle = sheetTitle
            }

        fun newInstance(
            host: Host,
            accountList: Single<List<BlockchainAccount>>,
            @StringRes sheetTitle: Int,
            @StringRes sheetSubtitle: Int,
            statusDecorator: StatusDecorator
        ): AccountSelectSheet =
            AccountSelectSheet(host).apply {
                this.accountList = accountList
                this.sheetTitle = sheetTitle
                this.sheetSubtitle = sheetSubtitle
                this.statusDecorator = statusDecorator
            }
    }
}
