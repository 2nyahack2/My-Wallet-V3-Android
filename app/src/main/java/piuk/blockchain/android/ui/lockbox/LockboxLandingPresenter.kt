package piuk.blockchain.android.ui.lockbox

import com.blockchain.lockbox.LockboxDataManager
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.rxkotlin.subscribeBy
import piuk.blockchain.androidcore.data.walletoptions.WalletOptionsDataManager
import piuk.blockchain.androidcoreui.ui.base.BasePresenter
import piuk.blockchain.androidcoreui.ui.base.View
import timber.log.Timber

interface LockboxLandingView : View {
    fun renderUiState(uiState: LockboxUiState)
}

class LockboxLandingPresenter(
    private val lockboxDataManager: LockboxDataManager,
    private val walletOptionsDataManager: WalletOptionsDataManager
) : BasePresenter<LockboxLandingView>() {

    override fun onViewReady() {
        compositeDisposable +=
            lockboxDataManager.hasLockbox()
                .map {
                    if (it) {
                        LockboxUiState.LockboxPaired
                    } else {
                        LockboxUiState.NoLockbox
                    }
                }
                .toObservable()
                .startWith(LockboxUiState.Loading)
                .doOnError { Timber.e(it) }
                .onErrorReturn { LockboxUiState.Error }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeBy(onNext = { view.renderUiState(it) })
    }

    internal fun getWalletLink(): String = walletOptionsDataManager.getWalletLink()

    internal fun getComRootLink(): String = walletOptionsDataManager.getComRootLink()
}

sealed class LockboxUiState {

    object Loading : LockboxUiState()
    object NoLockbox : LockboxUiState()
    object LockboxPaired : LockboxUiState()
    object Error : LockboxUiState()
}
