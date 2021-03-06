package piuk.blockchain.android.ui.addresses

import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.view.MenuItem
import android.view.MotionEvent
import androidx.annotation.StringRes
import androidx.recyclerview.widget.LinearLayoutManager
import com.blockchain.koin.scopedInject
import com.blockchain.ui.password.SecondPasswordHandler
import info.blockchain.balance.CryptoCurrency
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.rxkotlin.subscribeBy
import kotlinx.android.synthetic.main.activity_accounts.*
import kotlinx.android.synthetic.main.toolbar_general.*
import org.koin.android.ext.android.inject
import piuk.blockchain.android.R
import piuk.blockchain.android.coincore.AssetAction
import piuk.blockchain.android.coincore.CryptoAccount
import piuk.blockchain.android.coincore.impl.CryptoNonCustodialAccount
import piuk.blockchain.android.ui.addresses.adapter.AccountAdapter
import piuk.blockchain.android.ui.addresses.adapter.AccountListItem
import piuk.blockchain.android.ui.base.MvpActivity
import piuk.blockchain.android.ui.customviews.BlockchainListDividerDecor
import piuk.blockchain.android.ui.transactionflow.DialogFlow
import piuk.blockchain.android.ui.transactionflow.TransactionFlow
import piuk.blockchain.android.ui.scan.QrScanActivity
import piuk.blockchain.android.ui.scan.QrExpected
import piuk.blockchain.android.ui.scan.QrScanActivity.Companion.getRawScanData
import piuk.blockchain.androidcore.data.events.ActionEvent
import piuk.blockchain.androidcore.data.rxjava.RxBus
import piuk.blockchain.androidcore.utils.helperfunctions.consume
import piuk.blockchain.androidcore.utils.helperfunctions.unsafeLazy
import piuk.blockchain.androidcoreui.ui.customviews.ToastCustom
import piuk.blockchain.androidcoreui.ui.customviews.toast
import timber.log.Timber

class AccountActivity : MvpActivity<AccountView, AccountPresenter>(),
                        AccountView,
                        AccountAdapter.Listener,
                        AccountEditSheet.Host,
                        DialogFlow.FlowHost {

    private val rxBus: RxBus by inject()
    private val secondPasswordHandler: SecondPasswordHandler by scopedInject()
    private val compositeDisposable = CompositeDisposable()

    private val accountsAdapter: AccountAdapter by unsafeLazy {
        AccountAdapter(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_accounts)
        setupToolbar(toolbar_general, R.string.drawer_addresses)

        with(currency_header) {
            setCurrentlySelectedCurrency(CryptoCurrency.BTC)
            setSelectionListener { presenter.refresh(it) }
        }

        with(recyclerview_accounts) {
            layoutManager = LinearLayoutManager(this@AccountActivity)
            itemAnimator = null
            setHasFixedSize(true)
            adapter = accountsAdapter
            addItemDecoration(
                BlockchainListDividerDecor(context)
            )
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        android.R.id.home -> consume { onBackPressed() }
        else -> super.onOptionsItemSelected(item)
    }

    override fun onBackPressed() {
        if (currency_header.isOpen()) {
            currency_header.close()
        } else {
            super.onBackPressed()
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            // Notify touchOutsideViewListeners if user tapped outside a given view
            if (currency_header != null) {
                val viewRect = Rect()
                currency_header.getGlobalVisibleRect(viewRect)
                if (!viewRect.contains(event.rawX.toInt(), event.rawY.toInt())) {
                    if (currency_header.isOpen()) {
                        currency_header.close()
                        return false
                    }
                }
            }
        }
        return super.dispatchTouchEvent(event)
    }

    override fun onCreateNewClicked() {
        Timber.d("Click new account")
        createNewAccount()
    }

    override fun onAccountClicked(account: CryptoAccount) {
        Timber.d("Click ${account.label}")
        showBottomSheet(AccountEditSheet.newInstance(account))
    }

    override fun onImportAddressClicked() {
        Timber.d("Click import account")
        compositeDisposable += secondPasswordHandler.secondPassword(this)
            .subscribeBy(
                onSuccess = { showScanActivity() },
                onComplete = { showScanActivity() },
                onError = { }
            )
    }

    private fun showScanActivity() {
        QrScanActivity.start(this, QrExpected.IMPORT_KEYS_QR)
    }

    private fun createNewAccount() {
        compositeDisposable += secondPasswordHandler.secondPassword(this)
            .subscribeBy(
                onSuccess = { password ->
                    promptForAccountLabel(
                        ctx = this@AccountActivity,
                        title = R.string.create_a_new_wallet,
                        msg = R.string.create_a_new_wallet_helper_text,
                        okAction = { presenter.createNewAccount(it, password) }
                    )
                },
                onComplete = {
                    promptForAccountLabel(
                        ctx = this@AccountActivity,
                        title = R.string.create_a_new_wallet,
                        msg = R.string.create_a_new_wallet_helper_text,
                        okAction = { presenter.createNewAccount(it) }
                    )
                },
                onError = { }
            )
    }

    override fun renderAccountList(
        asset: CryptoCurrency,
        internal: List<CryptoNonCustodialAccount>,
        imported: List<CryptoNonCustodialAccount>
    ) {
        accountsAdapter.items = mutableListOf<AccountListItem>().apply {
            if (internal.isNotEmpty()) {
                add(AccountListItem.InternalHeader(enableCreate = asset == CryptoCurrency.BTC))
                addAll(internal.map { AccountListItem.Account(it) })
            }

            if (imported.isNotEmpty()) {
                add(AccountListItem.ImportedHeader(enableImport = asset == CryptoCurrency.BTC))
                addAll(imported.map { AccountListItem.Account(it) })
            }
        }.toList()
    }

    private val event by unsafeLazy {
        rxBus.register(ActionEvent::class.java)
    }

    override fun onResume() {
        super.onResume()
        presenter.refresh(currency_header.getSelectedCurrency())
    }

    override fun onPause() {
        super.onPause()
        rxBus.unregister(ActionEvent::class.java, event)
        compositeDisposable.clear()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == RESULT_OK && requestCode == QrScanActivity.SCAN_URI_RESULT && data.getRawScanData() != null) {
            data.getRawScanData()?.let {
                handleImportScan(it)
            } ?: showError(R.string.privkey_error)
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    private fun handleImportScan(scanData: String) {
        val walletPassword = secondPasswordHandler.verifiedPassword
        if (presenter.importRequiresPassword(scanData)) {
            promptImportKeyPassword(this) { password ->
                presenter.importScannedAddress(scanData, password, walletPassword)
            }
        } else {
            presenter.importScannedAddress(scanData, walletPassword)
        }
    }

    override fun onStartTransferFunds(account: CryptoNonCustodialAccount) {
        launchFlow(account)
    }

    override fun onSheetClosed() {
        presenter.refresh(currency_header.getSelectedCurrency())
    }

    override fun showRenameImportedAddressDialog(account: CryptoNonCustodialAccount) =
        promptForAccountLabel(
            ctx = this,
            title = R.string.app_name,
            msg = R.string.label_address,
            initialText = account.label,
            okAction = { presenter.updateLegacyAddressLabel(it, account) },
            okBtnText = R.string.save_name,
            cancelText = R.string.polite_no
        )

    override fun showError(@StringRes message: Int) =
        toast(message, ToastCustom.TYPE_ERROR)

    override fun showSuccess(@StringRes message: Int) {
        toast(message, ToastCustom.TYPE_OK)
        presenter.refresh(currency_header.getSelectedCurrency())
    }

    override fun showTransferFunds(account: CryptoNonCustodialAccount) {
        promptTransferFunds(this) { launchFlow(account) }
    }

    private fun launchFlow(sourceAccount: CryptoAccount) {
        TransactionFlow(
            sourceAccount = sourceAccount,
            action = AssetAction.Send
        ).apply {
            startFlow(
                fragmentManager = supportFragmentManager,
                host = this@AccountActivity
            )
        }
    }

    override fun onFlowFinished() {
        presenter.refresh(currency_header.getSelectedCurrency())
    }

    override fun onDestroy() {
        super.onDestroy()
        dismissProgressDialog()
    }

    override val presenter: AccountPresenter by scopedInject()
    override val view: AccountView
        get() = this
}
