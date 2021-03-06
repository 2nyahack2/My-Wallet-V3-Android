package piuk.blockchain.android.ui.transfer.receive.activity

import android.graphics.Bitmap
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import info.blockchain.balance.CryptoCurrency
import info.blockchain.balance.CryptoValue
import info.blockchain.balance.FiatValue
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.rxkotlin.subscribeBy
import piuk.blockchain.android.R
import piuk.blockchain.android.coincore.CryptoAccount
import piuk.blockchain.android.coincore.CryptoAddress
import piuk.blockchain.android.coincore.NullCryptoAccount
import piuk.blockchain.android.coincore.NullCryptoAddress
import piuk.blockchain.android.scan.QrCodeDataManager
import piuk.blockchain.android.ui.base.MvpPresenter
import piuk.blockchain.android.ui.base.MvpView
import piuk.blockchain.androidcore.data.exchangerate.ExchangeRateDataManager
import piuk.blockchain.androidcore.data.exchangerate.toCrypto
import piuk.blockchain.androidcore.utils.PersistentPrefs
import piuk.blockchain.androidcoreui.ui.customviews.ToastCustom
import timber.log.Timber

interface ReceiveView : MvpView {
    fun showQrLoading()
    fun showQrCode(bitmap: Bitmap?)
    fun showToast(@StringRes message: Int, @ToastCustom.ToastType toastType: String)
    fun updateAmountField(value: FiatValue)
    fun updateAmountField(amount: CryptoValue)
    fun updateReceiveAddress(address: CryptoAddress)
    fun showShareSheet(asset: CryptoCurrency, uri: String)
    fun finishPage()
}

class ReceivePresenter(
    private val prefs: PersistentPrefs,
    private val qrCodeDataManager: QrCodeDataManager,
    private val exchangeRates: ExchangeRateDataManager
) : MvpPresenter<ReceiveView>() {

    override val alwaysDisableScreenshots = false
    override val enableLogoutTimer = true

    @VisibleForTesting
    internal var selectedAddress: CryptoAddress = NullCryptoAddress
    @VisibleForTesting
    internal var selectedAccount: CryptoAccount = NullCryptoAccount()

    private val asset: CryptoCurrency
        get() = selectedAccount.asset

    private var amount: CryptoValue = CryptoValue.zero(CryptoCurrency.BTC)

    override fun onViewAttached() {}
    override fun onViewDetached() {}

    internal fun onResume(account: CryptoAccount) {
        compositeDisposable.clear()
        selectedAccount = account

        compositeDisposable += selectedAccount.receiveAddress
            .doOnSubscribe { view?.showQrLoading() }
            .map { it as CryptoAddress }
            .subscribeBy(
                onSuccess = {
                    selectedAddress = it
                    view?.updateReceiveAddress(it)
                    generateQrCode(it.toUrl())
                },
                onError = {
                    Timber.e("Unable to fetch ${selectedAccount.asset} address from account")
                    view?.showToast(R.string.unexpected_error, ToastCustom.TYPE_ERROR)
                    view?.finishPage()
                }
            )
        }

    internal fun onAmountChanged(value: CryptoValue) {
        val fiatValue = value.toFiat(exchangeRates, prefs.selectedFiatCurrency)
        view?.updateAmountField(fiatValue)
        amount = value
        generateQrCode(selectedAddress.toUrl(value))
    }

    internal fun onAmountChanged(value: FiatValue) {
        val cryptoValue = value.toCrypto(exchangeRates, asset)
        view?.updateAmountField(cryptoValue)
        amount = cryptoValue
        generateQrCode(selectedAddress.toUrl(cryptoValue))
    }

    internal fun onShowBottomShareSheetSelected() {
        val v = view ?: return
        if (asset == CryptoCurrency.BTC) {
            v.showShareSheet(CryptoCurrency.BTC, selectedAddress.toUrl(amount))
        } else {
            v.showShareSheet(asset, selectedAddress.toUrl())
        }
    }

    private fun generateQrCode(uri: String) {
        compositeDisposable.clear()
        compositeDisposable += qrCodeDataManager.generateQrCode(uri, DIMENSION_QR_CODE)
            .doOnSubscribe { view?.showQrLoading() }
            .subscribeBy(
                onSuccess = { view?.showQrCode(it) },
                onError = { view?.showQrCode(null) }
            )
    }

    companion object {
        @VisibleForTesting
        const val KEY_WARN_WATCH_ONLY_SPEND = "warn_watch_only_spend"
        private const val DIMENSION_QR_CODE = 600
    }
}
