package piuk.blockchain.android.ui.sell

import android.content.Context
import android.view.View
import io.reactivex.Maybe
import io.reactivex.Single
import piuk.blockchain.android.coincore.BlockchainAccount
import piuk.blockchain.android.ui.customviews.account.CellDecorator

class SellCellDecorator(private val account: BlockchainAccount) : CellDecorator {
    override fun view(context: Context): Maybe<View> = Maybe.empty()

    override fun isEnabled(): Single<Boolean> = Single.just(account.isFunded)
}