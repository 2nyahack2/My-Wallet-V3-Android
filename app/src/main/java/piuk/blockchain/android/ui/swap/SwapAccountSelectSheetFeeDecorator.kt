package piuk.blockchain.android.ui.swap

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import io.reactivex.Maybe
import piuk.blockchain.android.R
import piuk.blockchain.android.coincore.BlockchainAccount
import piuk.blockchain.android.coincore.TradingAccount
import piuk.blockchain.android.ui.customviews.account.CellDecorator

class SwapAccountSelectSheetFeeDecorator(private val account: BlockchainAccount) : CellDecorator {

    override fun view(context: Context): Maybe<View> =
        if (account is TradingAccount) {
            Maybe.just(tradingAccountBadgesView(context))
        } else
            Maybe.empty()

    private fun tradingAccountBadgesView(context: Context) =
        LayoutInflater.from(context).inflate(
            R.layout.trading_account_badges_layout,
            null,
            false
        )
}