package piuk.blockchain.android.withdraw

import android.content.Context
import android.content.Intent
import android.os.Bundle
import kotlinx.android.synthetic.main.toolbar_general.*
import piuk.blockchain.android.R
import piuk.blockchain.android.ui.base.BlockchainActivity
import piuk.blockchain.androidcore.utils.helperfunctions.consume
import piuk.blockchain.androidcore.utils.helperfunctions.unsafeLazy
import java.lang.IllegalStateException

class WithdrawActivity : BlockchainActivity(), WithdrawNavigator {

    private val currency: String by unsafeLazy {
        intent.getStringExtra(CURRENCY_KEY) ?: throw IllegalStateException("No currency provided")
    }
    override val alwaysDisableScreenshots: Boolean
        get() = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.fragment_activity)
        setSupportActionBar(toolbar_general)
        if (savedInstanceState == null) {
            launchWithdrawEnterAmountScreen()
        }
    }

    override fun goToCheckout() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.content_frame,
                WithdrawCheckoutFragment(),
                WithdrawCheckoutFragment::class.simpleName)
            .addToBackStack(WithdrawCheckoutFragment::class.simpleName)
            .commitAllowingStateLoss()
    }

    override fun exitFlow() {
        finish()
    }

    override fun goToCompleteWithdraw() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.content_frame,
                WithdrawCompleteFragment(),
                WithdrawCompleteFragment::class.simpleName)
            .addToBackStack(WithdrawCompleteFragment::class.simpleName)
            .commitAllowingStateLoss()
    }

    override fun hasMoreThanOneFragmentInTheStack() = supportFragmentManager.backStackEntryCount > 1

    private fun launchWithdrawEnterAmountScreen() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.content_frame,
                WithdrawEnterAmountFragment.newInstance(currency),
                WithdrawEnterAmountFragment::class.simpleName)
            .commitAllowingStateLoss()
    }

    override fun onSupportNavigateUp(): Boolean = consume {
        onBackPressed()
    }

    companion object {
        private const val CURRENCY_KEY = "CURRENCY_KEY"
        fun newInstance(context: Context, currency: String) =
            Intent(context, WithdrawActivity::class.java).apply {
                putExtra(CURRENCY_KEY, currency)
            }
    }
}