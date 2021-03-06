package piuk.blockchain.android.ui.activity

import com.blockchain.notifications.analytics.Analytics
import piuk.blockchain.android.simplebuy.SimpleBuyAnalytics
import com.blockchain.preferences.SimpleBuyPrefs
import com.blockchain.nabu.datamanagers.CustodialWalletManager
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.disposables.Disposable
import io.reactivex.rxkotlin.subscribeBy
import piuk.blockchain.android.coincore.ActivitySummaryList
import piuk.blockchain.android.coincore.BlockchainAccount
import piuk.blockchain.android.coincore.Coincore
import piuk.blockchain.android.repositories.AssetActivityRepository
import timber.log.Timber

class ActivitiesInteractor(
    private val coincore: Coincore,
    private val activityRepository: AssetActivityRepository,
    private val custodialWalletManager: CustodialWalletManager,
    private val simpleBuyPrefs: SimpleBuyPrefs,
    private val analytics: Analytics
) {
    fun getActivityForAccount(
        account: BlockchainAccount,
        isRefreshRequested: Boolean
    ): Observable<ActivitySummaryList> =
        activityRepository.fetch(account, isRefreshRequested)

    fun getDefaultAccount(): Single<BlockchainAccount> =
        coincore.allWallets().map { it as BlockchainAccount }

    fun cancelSimpleBuyOrder(orderId: String): Disposable? {
        return custodialWalletManager.deleteBuyOrder(orderId)
                .subscribeBy(
                    onComplete = { simpleBuyPrefs.clearState() },
                    onError = { error ->
                        analytics.logEvent(SimpleBuyAnalytics.BANK_DETAILS_CANCEL_ERROR)
                        Timber.e(error)
                    }
                )
    }
}
