package piuk.blockchain.android.ui.swipetoreceive

import android.graphics.Bitmap
import androidx.annotation.VisibleForTesting
import info.blockchain.balance.CryptoCurrency
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.rxkotlin.subscribeBy
import piuk.blockchain.android.coincore.impl.OfflineBalanceCall
import piuk.blockchain.android.scan.QrCodeDataManager
import piuk.blockchain.androidcoreui.ui.base.BasePresenter
import piuk.blockchain.androidcoreui.ui.base.UiState
import piuk.blockchain.androidcoreui.ui.base.View

interface SwipeToReceiveView : View {
    fun initPager(assetList: List<CryptoCurrency>)
    fun displayQrCode(bitmap: Bitmap)
    fun displayReceiveAddress(address: String)
    fun displayReceiveAccount(accountName: String)
    fun displayAsset(cryptoCurrency: CryptoCurrency)
    fun setUiState(@UiState.UiStateDef uiState: Int)
}

class SwipeToReceivePresenter(
    private val qrGenerator: QrCodeDataManager,
    private val addressCache: LocalOfflineAccountCache,
    private val offlineBalance: OfflineBalanceCall
) : BasePresenter<SwipeToReceiveView>() {

    private lateinit var selectedAsset: CryptoCurrency

    override fun onViewReady() {
        compositeDisposable += addressCache.availableAssets()
            .map { list ->
                list.mapNotNull { CryptoCurrency.fromNetworkTicker(it) }
            }.observeOn(AndroidSchedulers.mainThread())
            .subscribeBy {
                view.initPager(it)
            }
    }

    fun onCurrencySelected(asset: CryptoCurrency) {
        selectedAsset = asset
        view.displayAsset(asset)
        view.setUiState(UiState.LOADING)

        addressCache.getCacheForAsset(asset.networkTicker)?.let { assetInfo ->
            view.displayReceiveAccount(assetInfo.accountLabel)

            compositeDisposable += assetInfo.nextAddress(offlineBalance)
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSuccess { address ->
                    view.displayReceiveAddress(address.address)
                }.toSingle()
                .flatMap { address ->
                    qrGenerator.generateQrCode(address.addressUri, DIMENSION_QR_CODE)
                }.subscribeBy(
                    onSuccess = {
                        view.displayQrCode(it)
                        view.setUiState(UiState.CONTENT)
                    },
                    onError = {
                        view.setUiState(UiState.EMPTY)
                    }
                )
        } ?: view.setUiState(UiState.EMPTY)
    }

    fun refresh() {
        if (::selectedAsset.isInitialized) {
            onCurrencySelected(selectedAsset)
        }
    }

    companion object {
        @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
        const val DIMENSION_QR_CODE = 600
    }
}
