package piuk.blockchain.android.ui.kyc.navhost

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.navigation.NavDestination
import androidx.navigation.NavDirections
import androidx.navigation.fragment.NavHostFragment.findNavController
import com.blockchain.koin.scopedInject
import piuk.blockchain.android.KycNavXmlDirections
import piuk.blockchain.android.R
import piuk.blockchain.android.campaign.CampaignType
import piuk.blockchain.android.ui.kyc.complete.ApplicationCompleteFragment
import piuk.blockchain.androidcore.utils.helperfunctions.consume
import piuk.blockchain.androidcore.utils.helperfunctions.unsafeLazy
import piuk.blockchain.androidcoreui.ui.base.BaseMvpActivity
import piuk.blockchain.androidcoreui.ui.customviews.ToastCustom
import piuk.blockchain.androidcoreui.ui.customviews.toast
import piuk.blockchain.android.util.invisibleIf
import kotlinx.android.synthetic.main.activity_kyc_nav_host.frame_layout_fragment_wrapper as fragmentWrapper
import kotlinx.android.synthetic.main.activity_kyc_nav_host.nav_host as navHostFragment
import kotlinx.android.synthetic.main.activity_kyc_nav_host.progress_bar_loading_user as progressLoadingUser
import kotlinx.android.synthetic.main.activity_kyc_nav_host.toolbar_kyc as toolBar

interface StartKyc {
    fun startKycActivity(context: Any)
}

internal class KycStarter : StartKyc {
    override fun startKycActivity(context: Any) {
        KycNavHostActivity.start(context as Context, CampaignType.Swap, true)
    }
}

class KycNavHostActivity : BaseMvpActivity<KycNavHostView, KycNavHostPresenter>(),
    KycProgressListener, KycNavHostView {

    private val presenter: KycNavHostPresenter by scopedInject()
    private var navInitialDestination: NavDestination? = null
    private val navController by unsafeLazy { findNavController(navHostFragment) }
    private val currentFragment: Fragment?
        get() = navHostFragment.childFragmentManager.findFragmentById(R.id.nav_host)

    override val campaignType by unsafeLazy {
        intent.getSerializableExtra(EXTRA_CAMPAIGN_TYPE) as CampaignType
    }
    override val showTiersLimitsSplash by unsafeLazy {
        intent.getBooleanExtra(EXTRA_SHOW_TIERS_LIMITS_SPLASH, false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_kyc_nav_host)
        val title = when (campaignType) {
            CampaignType.Swap -> R.string.kyc_splash_title
            CampaignType.Sunriver,
            CampaignType.SimpleBuy,
            CampaignType.Blockstack,
            CampaignType.Resubmission,
            CampaignType.FiatFunds,
            CampaignType.Interest -> R.string.sunriver_splash_title
        }
        setupToolbar(toolBar, title)

        navController.setGraph(R.navigation.kyc_nav, intent.extras)

        onViewReady()
    }

    override fun setHostTitle(title: Int) {
        toolBar.title = getString(title)
    }

    override fun displayLoading(loading: Boolean) {
        fragmentWrapper.invisibleIf(loading)
        progressLoadingUser.invisibleIf(!loading)
    }

    override fun showErrorToastAndFinish(message: Int) {
        toast(message, ToastCustom.TYPE_ERROR)
        finish()
    }

    override fun navigate(directions: NavDirections) {
        navController.navigate(directions)
        navInitialDestination = navController.currentDestination
    }

    override fun navigateToKycSplash() {
        navController.navigate(KycNavXmlDirections.actionDisplayKycSplash())
        navInitialDestination = navController.currentDestination
    }

    override fun navigateToResubmissionSplash() {
        navController.navigate(KycNavXmlDirections.actionDisplayResubmissionSplash())
        navInitialDestination = navController.currentDestination
    }

    override fun hideBackButton() {
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        supportFragmentManager.fragments.forEach { fragment ->
            fragment.childFragmentManager.fragments.forEach {
                it.onActivityResult(
                    requestCode,
                    resultCode,
                    data
                )
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean = consume {

        if (flowShouldBeClosedAfterBackAction() || !navController.navigateUp()) {
            finish()
        }
    }

    override fun onBackPressed() {
        if (flowShouldBeClosedAfterBackAction()) {
            finish()
        } else {
            super.onBackPressed()
        }
    }

    private fun flowShouldBeClosedAfterBackAction() =
        // If on final page, close host Activity on navigate up
        currentFragment is ApplicationCompleteFragment ||
                // If not coming from settings, we want the 1st launched screen to be the 1st screen in the stack
                (navInitialDestination != null && navInitialDestination?.id == navController.currentDestination?.id)

    override fun createPresenter(): KycNavHostPresenter = presenter

    override fun getView(): KycNavHostView = this

    override fun startLogoutTimer() = Unit

    companion object {

//        const val RESULT_KYC_STX_COMPLETE = 5

        private const val EXTRA_CAMPAIGN_TYPE = "piuk.blockchain.android.EXTRA_CAMPAIGN_TYPE"
        const val EXTRA_SHOW_TIERS_LIMITS_SPLASH = "piuk.blockchain.android.EXTRA_SHOW_TIERS_LIMITS_SPLASH"

        @JvmStatic
        fun start(context: Context, campaignType: CampaignType) {
            intentArgs(context, campaignType)
                .run { context.startActivity(this) }
        }

        @JvmStatic
        fun start(context: Context, campaignType: CampaignType, showLimits: Boolean) {
            intentArgs(context, campaignType, showLimits)
                .run { context.startActivity(this) }
        }

        @JvmStatic
        fun startForResult(activity: Activity, campaignType: CampaignType, requestCode: Int) {
            intentArgs(activity, campaignType)
                .run { activity.startActivityForResult(this, requestCode) }
        }

        @JvmStatic
        fun startForResult(fragment: Fragment, campaignType: CampaignType, requestCode: Int) {
            intentArgs(fragment.requireContext(), campaignType)
                .run { fragment.startActivityForResult(this, requestCode) }
        }

        @JvmStatic
        private fun intentArgs(
            context: Context,
            campaignType: CampaignType,
            showTiersLimitsSplash: Boolean = false
        ): Intent =
            Intent(context, KycNavHostActivity::class.java)
                .apply {
                    putExtra(EXTRA_CAMPAIGN_TYPE, campaignType)
                    putExtra(EXTRA_SHOW_TIERS_LIMITS_SPLASH, showTiersLimitsSplash)
                }
    }
}

interface KycProgressListener {

    val campaignType: CampaignType

    fun setHostTitle(@StringRes title: Int)

    fun hideBackButton()
}