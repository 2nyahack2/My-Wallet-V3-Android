package piuk.blockchain.android.ui.transactionflow.flow

import android.app.Activity
import android.content.Intent
import android.text.Editable
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.blockchain.koin.scopedInject
import info.blockchain.balance.CryptoCurrency
import io.reactivex.Single
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.rxkotlin.subscribeBy
import kotlinx.android.synthetic.main.dialog_tx_flow_enter_address.view.*
import org.koin.android.ext.android.inject
import piuk.blockchain.android.R
import piuk.blockchain.android.coincore.BlockchainAccount
import piuk.blockchain.android.coincore.CryptoAddress
import piuk.blockchain.android.coincore.SingleAccount
import piuk.blockchain.android.scan.QrScanResultProcessor
import piuk.blockchain.android.ui.customviews.account.DefaultCellDecorator
import piuk.blockchain.android.ui.scan.QrExpected
import piuk.blockchain.android.ui.transactionflow.engine.TransactionIntent
import piuk.blockchain.android.ui.transactionflow.engine.TransactionState
import piuk.blockchain.android.ui.scan.QrScanActivity
import piuk.blockchain.android.ui.scan.QrScanActivity.Companion.getRawScanData
import piuk.blockchain.androidcoreui.ui.customviews.ToastCustom
import piuk.blockchain.android.util.getTextString
import piuk.blockchain.android.util.gone
import piuk.blockchain.android.util.invisible
import piuk.blockchain.android.util.visible
import piuk.blockchain.android.util.visibleIf
import piuk.blockchain.android.util.AfterTextChangedWatcher
import timber.log.Timber

class EnterTargetAddressSheet : TransactionFlowSheet() {
    override val layoutResource: Int = R.layout.dialog_tx_flow_enter_address

    private val customiser: TransactionFlowCustomiser by inject()
    private val qrProcessor: QrScanResultProcessor by scopedInject()

    private val disposables = CompositeDisposable()

    private val addressTextWatcher = object : AfterTextChangedWatcher() {
        override fun afterTextChanged(s: Editable?) {
            val address = s.toString()

            if (address.isEmpty()) {
                model.process(TransactionIntent.EnteredAddressReset)
            } else {
                if (customiser.enterTargetAddressSheetState(state) is
                        TargetAddressSheetState.SelectAccountWhenOverMaxLimitSurpassed
                ) {
                    dialogView.select_an_account.visible()
                } else {
                    dialogView.wallet_select.clearSelectedAccount()
                }
                addressEntered(address, state.asset)
            }
        }
    }

    override fun render(newState: TransactionState) {
        Timber.d("!TRANSACTION!> Rendering! EnterTargetAddressSheet")

        with(dialogView) {
            if (state.sendingAccount != newState.sendingAccount) {
                from_details.updateAccount(
                    newState.sendingAccount,
                    {},
                    DefaultCellDecorator()
                )

                setupTransferList(customiser.enterTargetAddressSheetState(newState))
                setupLabels(newState)
            }

            if (customiser.enterTargetAddressSheetState(newState) is
                    TargetAddressSheetState.SelectAccountWhenOverMaxLimitSurpassed
            ) {
                select_an_account.visible()
            }

            if (customiser.selectTargetShowManualEnterAddress(newState)) {
                showManualAddressEntry(newState)
            } else {
                hideManualAddressEntry(newState)
            }

            customiser.issueFlashMessage(newState, null)?.let {
                address_entry.setBackgroundColor(
                    ContextCompat.getColor(requireContext(), R.color.red_000)
                )
                error_msg.apply {
                    text = it
                    visible()
                }
            } ?: hideErrorState()

            address_sheet_back.visibleIf { newState.canGoBack }

            cta_button.isEnabled = newState.nextEnabled
            cacheState(newState)
        }
    }

    override fun initControls(view: View) {
        with(dialogView) {
            address_entry.addTextChangedListener(addressTextWatcher)
            btn_scan.setOnClickListener { onLaunchAddressScan() }
            cta_button.setOnClickListener { onCtaClick() }
            select_an_account.setOnClickListener { showMoreAccounts() }
            wallet_select.apply {
                onLoadError = {
                    hideTransferList()
                }
                onListLoaded = {
                    if (it) hideTransferList()
                }
            }
            address_sheet_back.setOnClickListener {
                model.process(TransactionIntent.ReturnToPreviousStep)
            }
        }
    }

    private fun setupLabels(state: TransactionState) {
        with(dialogView) {
            title_from.text = customiser.selectTargetSourceLabel(state)
            title_to.text = customiser.selectTargetDestinationLabel(state)
            title.text = customiser.selectTargetAddressTitle(state)
            subtitle.visibleIf { customiser.selectTargetShouldShowSubtitle(state) }
            subtitle.text = customiser.selectTargetSubtitle(state)
        }
    }

    private fun hideErrorState() {
        dialogView.error_msg.invisible()
        dialogView.address_entry.setBackgroundColor(
            ContextCompat.getColor(requireContext(), R.color.grey_000)
        )
    }

    private fun showManualAddressEntry(newState: TransactionState) {
        val address = if (newState.selectedTarget is CryptoAddress) {
            newState.selectedTarget.address
        } else {
            ""
        }

        with(dialogView) {
            if (address.isNotEmpty() && address != address_entry.getTextString()) {
                address_entry.setText(address, TextView.BufferType.EDITABLE)
            }
            address_entry.hint = customiser.selectTargetAddressInputHint(newState)
            // set visibility of component here so bottom sheet grows to the correct height
            input_switcher.visible()
            input_switcher.displayedChild = NONCUSTODIAL_INPUT
        }
    }

    private fun hideManualAddressEntry(newState: TransactionState) {
        val msg = customiser.selectTargetNoAddressMessageText(newState)

        with(dialogView) {
            if (msg != null) {
                input_switcher.visible()
                no_manual_enter_msg.text = msg

                internal_send_close.setOnClickListener {
                    input_switcher.gone()
                }

                title_pick.gone()
                pick_separator.gone()
                input_switcher.displayedChild = CUSTODIAL_INPUT
            } else {
                input_switcher.gone()
                pick_separator.gone()
                title_pick.gone()
            }
        }
    }

    private fun showMoreAccounts() {
        model.process(TransactionIntent.ShowMoreAccounts)
    }

    private fun setupTransferList(targetAddressSheetState: TargetAddressSheetState) {
        with(dialogView.wallet_select) {
            initialise(
                Single.just(targetAddressSheetState.accounts.filterIsInstance<BlockchainAccount>()),
                shouldShowSelectionStatus = true
            )
            // set visibility of component here so bottom sheet grows to the correct height
            visible()
            when (targetAddressSheetState) {
                is TargetAddressSheetState.SelectAccountWhenWithinMaxLimit -> {
                    onAccountSelected = {
                        accountSelected(it)
                    }
                }
                is TargetAddressSheetState.TargetAccountSelected -> {
                    updatedSelectedAccount(
                        targetAddressSheetState.accounts.filterIsInstance<BlockchainAccount>().first()
                    )
                    onAccountSelected = {
                        model.process(TransactionIntent.ShowTargetSelection)
                    }
                }
                else -> {
                    // do nothing
                }
            }
        }
    }

    private fun hideTransferList() {
        dialogView.title_pick.gone()
        dialogView.wallet_select.gone()
    }

    private fun accountSelected(account: BlockchainAccount) {
        require(account is SingleAccount)
        analyticsHooks.onAccountSelected(account, state)

        dialogView.wallet_select.updatedSelectedAccount(account)
        // TODO update the selected target (account type) instead so the render method knows what to show  & hide
        setAddressValue("")
        model.process(TransactionIntent.TargetSelectionUpdated(account))
    }

    private fun onLaunchAddressScan() {
        analyticsHooks.onScanQrClicked(state)
        QrScanActivity.start(this, QrExpected.ASSET_ADDRESS_QR(state.asset))
    }

    private fun addressEntered(address: String, asset: CryptoCurrency) {
        analyticsHooks.onManualAddressEntered(state)
        model.process(TransactionIntent.ValidateInputTargetAddress(address, asset))
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) =
        when (requestCode) {
            QrScanActivity.SCAN_URI_RESULT -> handleScanResult(resultCode, data)
            else -> super.onActivityResult(requestCode, resultCode, data)
        }

    private fun setAddressValue(value: String) {
        with(dialogView.address_entry) {
            removeTextChangedListener(addressTextWatcher)
            setText(value, TextView.BufferType.EDITABLE)
            setSelection(value.length)
            addTextChangedListener(addressTextWatcher)
        }
    }

    private fun handleScanResult(resultCode: Int, data: Intent?) {
        Timber.d("Got QR scan result!")
        if (resultCode == Activity.RESULT_OK) {
            data.getRawScanData()?.let { rawScan ->
                disposables += qrProcessor.processScan(rawScan, false)
                    .flatMapMaybe { qrProcessor.selectAssetTargetFromScan(state.asset, it) }
                    .subscribeBy(
                        onSuccess = {
                            // TODO update the selected target (address type) instead so the render method knows what to show  & hide
                            setAddressValue(it.address)
                            dialogView.wallet_select.clearSelectedAccount()
                            model.process(TransactionIntent.TargetSelectionUpdated(it))
                        },
                        onComplete = {
                            ToastCustom.makeText(
                                requireContext(),
                                getString(R.string.scan_mismatch_transaction_target, state.asset.displayTicker),
                                ToastCustom.LENGTH_SHORT,
                                ToastCustom.TYPE_GENERAL
                            )
                        },
                        onError = {
                            ToastCustom.makeText(
                                requireContext(),
                                getString(R.string.scan_failed),
                                ToastCustom.LENGTH_SHORT,
                                ToastCustom.TYPE_GENERAL
                            )
                        }
                    )
            }
        }
    }

    private fun onCtaClick() {
        analyticsHooks.onEnterAddressCtaClick(state)
        model.process(TransactionIntent.TargetSelected)
    }

    companion object {
        private const val NONCUSTODIAL_INPUT = 0
        private const val CUSTODIAL_INPUT = 1
    }
}
