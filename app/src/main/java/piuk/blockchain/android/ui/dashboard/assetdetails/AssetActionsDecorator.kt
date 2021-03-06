package piuk.blockchain.android.ui.dashboard.assetdetails

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import io.reactivex.Maybe
import io.reactivex.Single
import kotlinx.android.synthetic.main.status_line_info.view.*
import piuk.blockchain.android.R
import piuk.blockchain.android.coincore.CryptoAccount
import piuk.blockchain.android.coincore.TxSourceState
import piuk.blockchain.android.ui.customviews.account.CellDecorator

class AssetActionsDecorator(private val account: CryptoAccount) :
    CellDecorator {
    override fun view(context: Context): Maybe<View> =
        account.sourceState.flatMapMaybe {
            when (it) {
                TxSourceState.TRANSACTION_IN_FLIGHT ->
                    viewWithText(context.getString(R.string.send_state_send_in_flight), context)
                TxSourceState.NOT_ENOUGH_GAS -> viewWithText(context.getString(R.string.send_state_not_enough_gas),
                    context)
                else -> Maybe.empty()
            }
        }

    private fun viewWithText(text: String, context: Context): Maybe<View> {
        val view = LayoutInflater.from(context).inflate(
            R.layout.status_line_info,
            null,
            false
        )
        view.message.text = text
        return Maybe.just(view)
    }

    override fun isEnabled(): Single<Boolean> = account.sourceState.map {
            it != TxSourceState.TRANSACTION_IN_FLIGHT && it != TxSourceState.NOT_ENOUGH_GAS
    }
}