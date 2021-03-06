package piuk.blockchain.android.ui.addresses

import android.annotation.SuppressLint
import androidx.annotation.StringRes
import com.blockchain.notifications.analytics.AddressAnalytics
import com.blockchain.notifications.analytics.Analytics
import com.blockchain.notifications.analytics.AnalyticsEvents
import com.blockchain.notifications.analytics.WalletAnalytics
import info.blockchain.balance.CryptoCurrency
import info.blockchain.wallet.exceptions.DecryptionException
import info.blockchain.wallet.util.PrivateKeyFactory
import io.reactivex.Completable
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.rxkotlin.subscribeBy
import piuk.blockchain.android.R
import piuk.blockchain.android.coincore.AssetFilter
import piuk.blockchain.android.coincore.Coincore
import piuk.blockchain.android.coincore.SingleAccount
import piuk.blockchain.android.coincore.SingleAccountList
import piuk.blockchain.android.coincore.bch.BchAsset
import piuk.blockchain.android.coincore.bch.BchCryptoWalletAccount
import piuk.blockchain.android.coincore.btc.BtcAsset
import piuk.blockchain.android.coincore.btc.BtcCryptoWalletAccount
import piuk.blockchain.android.coincore.impl.CryptoNonCustodialAccount
import piuk.blockchain.android.ui.base.MvpPresenter
import piuk.blockchain.android.ui.base.MvpView
import timber.log.Timber

interface AccountView : MvpView {
    fun showError(@StringRes message: Int)
    fun showSuccess(@StringRes message: Int)
    fun showRenameImportedAddressDialog(account: CryptoNonCustodialAccount)
    fun renderAccountList(
        asset: CryptoCurrency,
        internal: List<CryptoNonCustodialAccount>,
        imported: List<CryptoNonCustodialAccount>
    )
    fun showTransferFunds(account: CryptoNonCustodialAccount)
}

class AccountPresenter internal constructor(
    private val privateKeyFactory: PrivateKeyFactory,
    private val coincore: Coincore,
    private val analytics: Analytics
) : MvpPresenter<AccountView>() {

    override fun onViewAttached() {
        analytics.logEvent(AnalyticsEvents.AccountsAndAddresses)
    }

    override fun onViewDetached() { }

    fun refresh(asset: CryptoCurrency) {
        fetchAccountList(asset)
    }

    override val alwaysDisableScreenshots: Boolean = false
    override val enableLogoutTimer: Boolean = true

    /**
     * Derive new Account from seed
     *
     * @param accountLabel A label for the account to be created
     */
    private class NameInUseException() : Exception()

    @SuppressLint("CheckResult")
    internal fun createNewAccount(accountLabel: String, secondPassword: String? = null) {
        val btcAsset = coincore[CryptoCurrency.BTC] as BtcAsset
        val bchAsset = coincore[CryptoCurrency.BCH] as BchAsset

        compositeDisposable += coincore.isLabelUnique(accountLabel)
            .flatMap { isUnique ->
                if (!isUnique) {
                    Single.error(NameInUseException())
                } else {
                    btcAsset.createAccount(accountLabel, secondPassword)
                }
            }.flatMapCompletable {
                bchAsset.createAccount(it.xpubAddress)
            }.observeOn(AndroidSchedulers.mainThread())
            .showProgress()
            .subscribeBy(
                onComplete = {
                    view?.showSuccess(R.string.remote_save_ok)
                    analytics.logEvent(WalletAnalytics.AddNewWallet)
                },
                onError = { throwable ->
                    Timber.e(throwable)
                    when (throwable) {
                        is DecryptionException -> view?.showError(R.string.double_encryption_password_error)
                        is NameInUseException -> view?.showError(R.string.label_name_match)
                        else -> view?.showError(R.string.unexpected_error)
                    }
                }
            )
    }

    internal fun updateLegacyAddressLabel(newLabel: String, account: CryptoNonCustodialAccount) {
        compositeDisposable += account.updateLabel(newLabel)
            .observeOn(AndroidSchedulers.mainThread())
            .showProgress()
            .subscribeBy(
                onComplete = {
                    view?.showSuccess(R.string.remote_save_ok)
                    checkBalanceForTransfer(account)
                },
                onError = {
                    view?.showError(R.string.remote_save_failed)
                }
            )
    }

    internal fun checkBalanceForTransfer(account: CryptoNonCustodialAccount) {
        compositeDisposable += account.actionableBalance
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeBy {
            if (it.isPositive) {
                view?.showTransferFunds(account)
            }
        }
    }

    internal fun importRequiresPassword(data: String): Boolean =
        privateKeyFactory.getFormat(data) == PrivateKeyFactory.BIP38

    internal fun importScannedAddress(
        keyData: String,
        walletSecondPassword: String?
    ) {
        val format = privateKeyFactory.getFormat(keyData)
        if (checkCanImport(keyData, format)) {
            require(format != PrivateKeyFactory.BIP38)
            importAddress(keyData, format, null, walletSecondPassword)
        }
    }

    internal fun importScannedAddress(
        keyData: String,
        keyPassword: String,
        walletSecondPassword: String?
    ) {
        val format = privateKeyFactory.getFormat(keyData)
        if (checkCanImport(keyData, format)) {
            require(format == PrivateKeyFactory.BIP38)
            importAddress(keyData, format, keyPassword, walletSecondPassword)
        }
    }

    private fun checkCanImport(keyData: String, format: String?) =
        if (format == null) {
            val btcAsset = coincore[CryptoCurrency.BTC]
            view?.showError(
                if (btcAsset.isValidAddress(keyData))
                    R.string.watch_only_not_supported
                else
                    R.string.privkey_error
            )
            false
        } else {
            true
        }

    private fun importAddress(
        keyData: String,
        keyFormat: String,
        keyPassword: String?,
        walletSecondPassword: String?
    ) {
        val btcAsset = coincore[CryptoCurrency.BTC] as BtcAsset
        compositeDisposable += btcAsset.importLegacyAddressFromKey(
            keyData,
            keyFormat,
            keyPassword,
            walletSecondPassword
        ).showProgress()
        .subscribeBy(
            onSuccess = {
                view?.showSuccess(R.string.private_key_successfully_imported)
                view?.showRenameImportedAddressDialog(it)
                analytics.logEvent(AddressAnalytics.ImportBTCAddress)
            },
            onError = {
                view?.showError(R.string.no_private_key)
            }
        )
    }

    private fun fetchAccountList(asset: CryptoCurrency) {
        require(asset.hasFeature(CryptoCurrency.MULTI_WALLET))

        compositeDisposable += coincore[asset].accountGroup(AssetFilter.NonCustodial)
            .map {
                it.accounts
            }.subscribeBy(
                onSuccess = { processCoincoreList(asset, it) },
                onError = { e ->
                    Timber.e("Failed to get account list for asset: $e")
                }
            )
        }

    private fun processCoincoreList(asset: CryptoCurrency, list: SingleAccountList) {
        val internal = mutableListOf<CryptoNonCustodialAccount>()
        val imported = mutableListOf<CryptoNonCustodialAccount>()

        list.filterIsInstance<CryptoNonCustodialAccount>()
            .forEach {
            if (it.isInternalAccount) {
                internal.add(it)
            } else {
                imported.add(it)
            }
        }
        view?.renderAccountList(asset, internal, imported)
    }

    private fun <T> Single<T>.showProgress() =
        this.doOnSubscribe { view?.showProgressDialog(R.string.please_wait) }
        .doAfterTerminate { view?.dismissProgressDialog() }

    private fun Completable.showProgress() =
        this.doOnSubscribe { view?.showProgressDialog(R.string.please_wait) }
            .doAfterTerminate { view?.dismissProgressDialog() }

    // TODO: Find a better way!
    private val SingleAccount.isInternalAccount: Boolean
        get() = (this as? BtcCryptoWalletAccount)?.isHDAccount
            ?: (this as? BchCryptoWalletAccount)?.let { true }
            ?: throw java.lang.IllegalStateException("Unexpected asset type")
}
