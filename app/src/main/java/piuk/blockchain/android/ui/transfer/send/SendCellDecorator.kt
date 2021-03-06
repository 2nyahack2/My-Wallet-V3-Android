package piuk.blockchain.android.ui.transfer.send

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

class SendCellDecorator(private val cryptoAccount: CryptoAccount) : CellDecorator {

    override fun view(context: Context): Maybe<View> = cryptoAccount.sourceState.flatMapMaybe {
        when (it) {
            TxSourceState.NO_FUNDS -> statusInfoText(context.getString(R.string.send_state_no_funds), context)
            TxSourceState.NOT_SUPPORTED -> statusInfoText(context.getString(R.string.send_state_not_supported), context)
            TxSourceState.FUNDS_LOCKED -> statusInfoText(context.getString(R.string.send_state_locked_funds_1), context)
            TxSourceState.NOT_ENOUGH_GAS -> statusInfoText(context.getString(R.string.send_state_not_enough_gas),
                context)
            TxSourceState.TRANSACTION_IN_FLIGHT -> statusInfoText(context.getString(R.string.send_state_send_in_flight),
                context)
            else -> Maybe.empty<View>()
        }
    }

    private fun statusInfoText(text: String, context: Context): Maybe<View> {
        val view = LayoutInflater.from(context).inflate(
            R.layout.status_line_info,
            null,
            false
        )
        view.message.text = text
        return Maybe.just(view)
    }

    override fun isEnabled(): Single<Boolean> = cryptoAccount.sourceState.map {
        it == TxSourceState.CAN_TRANSACT
    }
}