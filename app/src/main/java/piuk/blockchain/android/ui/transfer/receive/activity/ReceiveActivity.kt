package piuk.blockchain.android.ui.transfer.receive.activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import kotlinx.android.synthetic.main.toolbar_general.*
import piuk.blockchain.android.R
import piuk.blockchain.android.coincore.CryptoAccount
import piuk.blockchain.android.coincore.NullCryptoAccount
import piuk.blockchain.android.ui.base.BlockchainActivity
import piuk.blockchain.android.util.getAccount
import piuk.blockchain.android.util.putAccount
import piuk.blockchain.androidcore.utils.helperfunctions.consume

class ReceiveActivity : BlockchainActivity(),
    ReceiveFragment.ReceiveFragmentHost {

    override val alwaysDisableScreenshots = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.temp_activity_receive)
        setSupportActionBar(toolbar_general)
        title = getString(R.string.common_receive)
    }

    override fun onStart() {
        super.onStart()

        intent.getAccount(PARAM_ACCOUNT)?.let {
            val f = ReceiveFragment.newInstance(it as CryptoAccount)
            supportFragmentManager.beginTransaction().replace(R.id.content, f).commit()
        } ?: finish()
        // TODO: Find a better solution than finish() for this case.
    }

    override fun onSupportNavigateUp(): Boolean = consume {
        finish()
    }

    override fun actionBackPress() = finish()

    companion object {

        private const val PARAM_ACCOUNT = "PARAM_ACCOUNT"
        fun start(ctx: Context, account: CryptoAccount) {
            require(account !is NullCryptoAccount)
            ctx.startActivity(
                Intent(ctx, ReceiveActivity::class.java).apply {
                    putAccount(PARAM_ACCOUNT, account)
                }
            )
        }
    }
}
