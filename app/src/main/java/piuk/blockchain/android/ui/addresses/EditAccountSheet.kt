package piuk.blockchain.android.ui.addresses

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import androidx.annotation.StringRes
import androidx.annotation.UiThread
import com.blockchain.koin.scopedInject
import com.blockchain.notifications.analytics.WalletAnalytics
import com.google.zxing.WriterException
import info.blockchain.balance.CryptoCurrency
import io.reactivex.Completable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.rxkotlin.subscribeBy
import kotlinx.android.synthetic.main.dialog_account_edit.view.*
import piuk.blockchain.android.coincore.CryptoAccount
import piuk.blockchain.android.ui.base.SlidingModalBottomDialog
import piuk.blockchain.android.util.getAccount
import piuk.blockchain.android.util.putAccount
import piuk.blockchain.android.R
import piuk.blockchain.android.coincore.Coincore
import piuk.blockchain.android.coincore.bch.BchCryptoWalletAccount
import piuk.blockchain.android.coincore.btc.BtcCryptoWalletAccount
import piuk.blockchain.android.coincore.impl.CryptoNonCustodialAccount
import piuk.blockchain.android.scan.QRCodeEncoder
import piuk.blockchain.android.ui.customviews.dialogs.MaterialProgressDialog
import piuk.blockchain.androidcoreui.ui.customviews.ToastCustom
import piuk.blockchain.androidcoreui.ui.customviews.toast
import piuk.blockchain.android.util.gone
import piuk.blockchain.android.util.visible
import timber.log.Timber

class AccountEditSheet : SlidingModalBottomDialog() {

    interface Host : SlidingModalBottomDialog.Host {
        fun onStartTransferFunds(account: CryptoNonCustodialAccount)
    }

    override val host: Host by lazy {
        super.host as? Host
        ?: throw IllegalStateException("Host fragment is not a AccountEditSheet.Host")
    }

    override val layoutResource: Int = R.layout.dialog_account_edit

    val account: CryptoAccount?
        get() = arguments?.getAccount(PARAM_ACCOUNT) as? CryptoAccount

    override fun initControls(view: View) {
        (account as? CryptoNonCustodialAccount)?.let {
            configureUi(it)
        } ?: dismiss()
    }

    private fun configureUi(account: CryptoNonCustodialAccount) {
        configureTransfer(account)
        configureAccountLabel(account)
        configureMakeDefault(account)
        configureShowXpub(account)
        configureArchive(account)
    }

    @SuppressLint("CheckResult")
    private fun configureTransfer(account: CryptoNonCustodialAccount) {
        with(dialogView.transfer_container) {
            gone()
            isClickable = false
            setOnClickListener { }

            if (!account.isInternalAccount) {
                account.actionableBalance
                    .subscribeBy(
                        onSuccess = {
                            visible()
                            if (account.isArchived) {
                                alpha = DISABLED_ALPHA
                            } else {
                                alpha = ENABLED_ALPHA
                                isClickable = true
                                setOnClickListener { handleTransfer(account) }
                            }
                        },
                        onError = {
                            Timber.e("Failed getting balance for imported account: $it")
                        }
                    )
                }
        }
    }

    private fun handleTransfer(account: CryptoNonCustodialAccount) {
        dismiss()
        host.onStartTransferFunds(account)
    }

    private fun configureAccountLabel(account: CryptoNonCustodialAccount) {
        with(dialogView.account_name) {
            text = account.label
            if (account.isArchived) {
                alpha =
                    DISABLED_ALPHA
                isClickable = false
                setOnClickListener { }
            } else {
                alpha = ENABLED_ALPHA
                isClickable = true
                setOnClickListener {
                    promptForAccountLabel(
                        ctx = requireContext(),
                        title = R.string.edit_wallet_name,
                        msg = R.string.edit_wallet_name_helper_text,
                        initialText = account.label,
                        okAction = { s -> handleUpdateLabel(s, account) }
                    )
                }
            }
        }
    }

    private fun showError(@StringRes msgId: Int) =
        toast(msgId, ToastCustom.TYPE_ERROR)

    // This should all be in a model or presenter. Move it all once the updates are working
    private val disposables = CompositeDisposable()
    private val coincore: Coincore by scopedInject()

    private fun handleUpdateLabel(newLabel: String, account: CryptoNonCustodialAccount) {
        val labelCopy = newLabel.trim { it.isWhitespace() }
        if (labelCopy.isEmpty()) {
            showError(R.string.label_cant_be_empty)
        } else {
            disposables += coincore.isLabelUnique(newLabel)
                .flatMapCompletable {
                    if (it) {
                        account.updateLabel(labelCopy)
                    } else {
                        showError(R.string.label_name_match)
                        Completable.complete()
                    }
                }.observeOn(AndroidSchedulers.mainThread())
                .showProgress()
                .updateUi(account)
                .subscribeBy(
                    onComplete = {
                        analytics.logEvent(WalletAnalytics.EditWalletName)
                    },
                    onError = {
                        showError(R.string.remote_save_failed)
                    }
                )
        }
    }

    private fun configureMakeDefault(account: CryptoNonCustodialAccount) {
        with(dialogView.default_container) {
            when {
                account.isDefault -> gone()
                account.isArchived -> {
                    visible()
                    alpha =
                        DISABLED_ALPHA
                    isClickable = false
                }
                account.isInternalAccount -> {
                    visible()
                    alpha =
                        ENABLED_ALPHA
                    isClickable = true
                    setOnClickListener { makeDefault(account) }
                }
                else -> gone()
            }
        }
    }

    private fun makeDefault(account: CryptoNonCustodialAccount) {
        disposables += account.setAsDefault()
            .observeOn(AndroidSchedulers.mainThread())
            .showProgress()
            .updateUi(account)
            .subscribeBy(
                onComplete = {
                    analytics.logEvent(WalletAnalytics.ChangeDefault)
//                    updateReceiveAddressShortcuts()
                },
                onError = {
                    showError(R.string.remote_save_failed)
                }
            )
    }

    private fun configureShowXpub(account: CryptoNonCustodialAccount) {
        with(dialogView.xpub_container) {
            if (account.isInternalAccount) {
                tv_xpub.setText(R.string.extended_public_key)
                tv_xpub_description.visible()

                if (account.asset == CryptoCurrency.BCH) {
                    tv_xpub_description.setText(R.string.extended_public_key_description_bch_only)
                } else {
                    tv_xpub_description.setText(R.string.extended_public_key_description)
                }
            } else {
                tv_xpub.setText(R.string.address)
                tv_xpub_description.gone()
            }

            if (account.isArchived) {
                alpha =
                    DISABLED_ALPHA
                isClickable = false
                setOnClickListener { }
            } else {
                alpha = ENABLED_ALPHA
                isClickable = true
                setOnClickListener { handleShowXpub(account) }
            }
        }
    }

    private fun handleShowXpub(account: CryptoNonCustodialAccount) =
        if (account.isInternalAccount) {
            promptXpubShareWarning(requireContext()) { showXpubAddress(account) }
        } else {
            showAccountAddress(account)
        }

    private fun showXpubAddress(account: CryptoNonCustodialAccount) {
        require(account.isInternalAccount)

        val qrString: String = account.xpubAddress
        generateQrCode(qrString)?.let { bmp ->
            context?.let { ctx ->
                showAddressQrCode(
                    ctx,
                    R.string.extended_public_key,
                    R.string.scan_this_code,
                    R.string.copy_xpub,
                    bmp,
                    qrString
                )
            }
            analytics.logEvent(WalletAnalytics.ShowXpub)
        }
    }

    private fun showAccountAddress(account: CryptoNonCustodialAccount) {
        require(!account.isInternalAccount)

        val qrString: String = account.xpubAddress
        generateQrCode(qrString)?.let {
            showAddressQrCode(
                requireContext(),
                R.string.address,
                qrString,
                R.string.copy_address,
                it,
                qrString
            )
            analytics.logEvent(WalletAnalytics.ShowXpub)
        }
    }

    private fun generateQrCode(qrString: String) =
        try {
            QRCodeEncoder(qrString,
                QR_CODE_DIMENSION
            ).encodeAsBitmap()
        } catch (e: WriterException) {
            Timber.e(e)
            null
        }

    private fun configureArchive(account: CryptoNonCustodialAccount) {
        if (account.isArchived) {
            with(dialogView.archive_container) {
                tv_archive_header.setText(R.string.unarchive)
                tv_archive_description.setText(R.string.archived_description)
                alpha = ENABLED_ALPHA
                visibility = View.VISIBLE
                isClickable = true
                setOnClickListener { toggleArchived(account) }
            }
        } else {
            if (account.isDefault) {
                with(dialogView.archive_container) {
                    tv_archive_header.setText(R.string.archive)
                    tv_archive_description.setText(R.string.default_account_description)
                    alpha =
                        DISABLED_ALPHA
                    visibility = View.VISIBLE
                    isClickable = false
                    setOnClickListener { /* not clickable */ }
                }
            } else {
                with(dialogView.archive_container) {
                    tv_archive_header.setText(R.string.archive)
                    tv_archive_description.setText(R.string.not_archived_description)
                    alpha =
                        ENABLED_ALPHA
                    visibility = View.VISIBLE
                    isClickable = true
                    setOnClickListener { toggleArchived(account) }
                }
            }
        }
    }

    private fun toggleArchived(account: CryptoNonCustodialAccount) {
        val title = if (account.isArchived) R.string.unarchive else R.string.archive
        val msg = if (account.isArchived) R.string.unarchive_are_you_sure else R.string.archive_are_you_sure
        promptArchive(
            requireContext(),
            title,
            msg
        ) { handleToggleArchive(account) }
    }

    private fun handleToggleArchive(account: CryptoNonCustodialAccount) {
        disposables += if (account.isArchived) {
            account.unarchive()
        } else {
            account.archive()
        }.observeOn(AndroidSchedulers.mainThread())
        .showProgress()
        .updateUi(account)
        .subscribeBy(
            onError = { showError(R.string.remote_save_failed) },
            onComplete = {
                if (!account.isArchived)
                    analytics.logEvent(WalletAnalytics.UnArchiveWallet)
                else
                    analytics.logEvent(WalletAnalytics.ArchiveWallet)
            }
        )
    }

    companion object {
        private const val DISABLED_ALPHA = 0.5f
        private const val ENABLED_ALPHA = 1.0f

        private const val QR_CODE_DIMENSION = 260

        private const val PARAM_ACCOUNT = "PARAM_ACCOUNT"

        fun newInstance(account: CryptoAccount): AccountEditSheet =
            AccountEditSheet().apply {
                arguments = Bundle().apply {
                    putAccount(PARAM_ACCOUNT, account)
                }
            }
    }

    private var progressDialog: MaterialProgressDialog? = null

    @UiThread
    private fun doShowProgress() {
        doHideProgress()
        progressDialog = MaterialProgressDialog(requireContext()).apply {
            setCancelable(false)
            setMessage(R.string.please_wait)
            show()
        }
    }

    @UiThread
    private fun doHideProgress() {
        progressDialog?.dismiss()
        progressDialog = null
    }

    private fun Completable.showProgress() =
        this.doOnSubscribe { doShowProgress() }
            .doOnTerminate { doHideProgress() }

    private fun Completable.updateUi(account: CryptoNonCustodialAccount) =
        this.doOnComplete { configureUi(account) }
}

private val CryptoNonCustodialAccount.isInternalAccount: Boolean
    get() = (this as? BtcCryptoWalletAccount)?.isHDAccount
            ?: (this as? BchCryptoWalletAccount)?.let { true }
            ?: throw java.lang.IllegalStateException("Unexpected asset type")
