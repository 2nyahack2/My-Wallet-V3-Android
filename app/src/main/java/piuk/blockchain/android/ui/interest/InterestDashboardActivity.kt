package piuk.blockchain.android.ui.interest

import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.blockchain.notifications.analytics.InterestAnalytics
import info.blockchain.balance.CryptoCurrency
import io.reactivex.Single
import kotlinx.android.synthetic.main.toolbar_general.*
import piuk.blockchain.android.R
import piuk.blockchain.android.campaign.CampaignType
import piuk.blockchain.android.coincore.AssetAction
import piuk.blockchain.android.coincore.BlockchainAccount
import piuk.blockchain.android.coincore.CryptoAccount
import piuk.blockchain.android.coincore.SingleAccount
import piuk.blockchain.android.ui.base.BlockchainActivity
import piuk.blockchain.android.ui.customviews.account.AccountSelectSheet
import piuk.blockchain.android.ui.kyc.navhost.KycNavHostActivity
import piuk.blockchain.android.ui.transactionflow.DialogFlow
import piuk.blockchain.android.ui.transactionflow.TransactionFlow
import piuk.blockchain.android.util.putAccount
import piuk.blockchain.androidcore.utils.helperfunctions.consume

class InterestDashboardActivity : BlockchainActivity(),
    InterestSummarySheet.Host,
    InterestDashboardFragment.InterestDashboardHost,
    DialogFlow.FlowHost {

    override val alwaysDisableScreenshots: Boolean
        get() = false

    private val fragment: InterestDashboardFragment by lazy { InterestDashboardFragment.newInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_interest_dashboard)
        setSupportActionBar(toolbar_general)
        setTitle(R.string.interest_dashboard_title)

        supportFragmentManager.beginTransaction()
            .replace(R.id.content_frame, fragment, InterestDashboardFragment::class.simpleName)
            .commitAllowingStateLoss()
    }

    override fun onSupportNavigateUp(): Boolean = consume {
        onBackPressed()
    }

    override fun gotoActivityFor(account: BlockchainAccount) {
        val b = Bundle()
        b.putAccount(ACTIVITY_ACCOUNT, account)
        setResult(RESULT_FIRST_USER, Intent().apply {
            putExtras(b)
        })
        finish()
    }

    override fun goToDeposit(fromAccount: SingleAccount, toAccount: SingleAccount, action: AssetAction) {
        clearBottomSheet()
        startDeposit(fromAccount, toAccount, action)
    }

    override fun onSheetClosed() {
        // do nothing
    }

    override fun startKyc() {
        analytics.logEvent(InterestAnalytics.INTEREST_DASHBOARD_KYC)
        KycNavHostActivity.start(this, CampaignType.Interest)
    }

    override fun showInterestSummarySheet(account: SingleAccount, cryptoCurrency: CryptoCurrency) {
        showBottomSheet(InterestSummarySheet.newInstance(account, cryptoCurrency))
    }

    override fun startDepositFlow(fromAccount: SingleAccount, toAccount: SingleAccount) {
        analytics.logEvent(InterestAnalytics.INTEREST_DASHBOARD_ACTION)
        startDeposit(fromAccount, toAccount, AssetAction.Deposit)
    }

    override fun startAccountSelection(
        filter: Single<List<BlockchainAccount>>,
        toAccount: SingleAccount
    ) {
        showBottomSheet(
            AccountSelectSheet.newInstance(object : AccountSelectSheet.SelectionHost {
                override fun onAccountSelected(account: BlockchainAccount) {
                    startDeposit(account as SingleAccount, toAccount, AssetAction.Deposit)
                }

                override fun onSheetClosed() {
                    // do nothing
                }
            }, filter, R.string.select_deposit_source_title))
    }

    private fun startDeposit(
        fromAccount: SingleAccount,
        toAccount: SingleAccount,
        assetAction: AssetAction
    ) {
        TransactionFlow(
            sourceAccount = fromAccount as CryptoAccount,
            target = toAccount,
            action = assetAction
        ).startFlow(supportFragmentManager, this)
    }

    companion object {
        const val ACTIVITY_ACCOUNT = "ACTIVITY_ACCOUNT"

        fun newInstance(context: Context) =
            Intent(context, InterestDashboardActivity::class.java)
    }

    override fun onFlowFinished() {
        fragment.refreshBalances()
    }
}