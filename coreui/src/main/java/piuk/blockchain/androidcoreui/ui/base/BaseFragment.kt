package piuk.blockchain.androidcoreui.ui.base

import androidx.annotation.CallSuper
import androidx.viewpager.widget.ViewPager
import piuk.blockchain.androidcoreui.BuildConfig
import piuk.blockchain.androidcoreui.utils.logging.Logging

/**
 * Logs Fragments that have been visited for statistics purposes using Crashlytics' answers.
 */
@Deprecated("Use the newer Kotlin MVP or move to MVI/MVVM")
abstract class BaseFragment<VIEW : View, PRESENTER : BasePresenter<VIEW>> :
    BaseMvpFragment<VIEW, PRESENTER>() {

    private var logged: Boolean = false

    @CallSuper
    override fun setUserVisibleHint(isVisibleToUser: Boolean) {
        super.setUserVisibleHint(isVisibleToUser)

        /* Ensure that pages are only logged as being seen if they are actually visible, and only
         * once. This is important for fragments in ViewPagers where they might be instantiated, but
         * not actually visible or being accessed. For example: Swipe to receive.
         *
         *  Note that this isn't triggered if a Fragment isn't in a ViewPager */
        if (isVisibleToUser) {
            logContentView()
        }
    }

    @CallSuper
    override fun onResume() {
        super.onResume()
        // In ViewPager, don't log here as Fragment might not be visible. Use setUserVisibleHint
        // to log in these situations.
        if (!parentIsViewPager()) {
            logContentView()
        }
    }

    private fun parentIsViewPager(): Boolean =
        (view != null && view!!.parent != null && view!!.parent is ViewPager)

    private fun logContentView() {
        if (!logged) {
            logged = true

            if (!BuildConfig.DEBUG) {
                Logging.logContentView(javaClass.simpleName)
            }
        }
    }
}
