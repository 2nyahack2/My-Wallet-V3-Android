package piuk.blockchain.android.ui.base

import androidx.annotation.StringRes
import io.reactivex.disposables.CompositeDisposable

interface MvpView {
    fun showProgressDialog(@StringRes messageId: Int, onCancel: (() -> Unit)? = null)
    fun dismissProgressDialog()
}

abstract class MvpPresenter<T : MvpView> {

    val compositeDisposable = CompositeDisposable()

    protected var view: T? = null
        private set

    fun attachView(view: T) {
        this.view = view
        onViewAttached()
    }

    fun detachView(view: T) {
        require(this.view === view)

        onViewDetached()
        compositeDisposable.clear()
        this.view = null
    }

    // These 3 methods are provided for compatibility with existing
    // code. Try not to use them in new code, because they are
    // going once the send and receive screen have been updated
    open fun onViewResumed() { }
    open fun onViewPaused() { }
    open fun onViewReady() { }

    protected abstract fun onViewAttached() // initView() in old framework
    protected abstract fun onViewDetached() // onViewDestroyed() in old framework

    abstract val alwaysDisableScreenshots: Boolean
    abstract val enableLogoutTimer: Boolean
}
