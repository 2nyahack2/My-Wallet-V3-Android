package piuk.blockchain.android.simplebuy

import android.os.Bundle
import android.view.View
import com.blockchain.koin.scopedInject
import kotlinx.android.synthetic.main.simple_buy_cancel_order_bottom_sheet.view.*
import piuk.blockchain.android.R
import piuk.blockchain.android.ui.base.SlidingModalBottomDialog
import piuk.blockchain.android.util.setOnClickListenerDebounced
import java.lang.IllegalStateException

class SimpleBuyCancelOrderBottomSheet : SlidingModalBottomDialog() {

    interface Host : SlidingModalBottomDialog.Host {
        fun cancelOrderConfirmAction(cancelOrder: Boolean, orderId: String?)
    }

    override val host: Host by lazy {
        super.host as? Host
            ?: throw IllegalStateException("Host fragment is not a SimpleBuyCancelOrderBottomSheet.Host")
    }

    private val stateFactory: SimpleBuySyncFactory by scopedInject()

    override val layoutResource: Int = R.layout.simple_buy_cancel_order_bottom_sheet

    override fun initControls(view: View) {
        val state = stateFactory.currentState()

        if (state?.selectedCryptoCurrency != null) {
            with(view) {

                if (arguments.fromDashboard()) {
                    cancel_order.text = getString(R.string.cancel_order_do_cancel_dashboard)
                    go_back.text = getString(R.string.cancel_order_go_back_dashboard)
                }

                cancel_order_token.text = getString(
                    R.string.cancel_token_instruction,
                    state.selectedCryptoCurrency.displayTicker
                )
                cancel_order.setOnClickListenerDebounced {
                    dismiss()
                    host.cancelOrderConfirmAction(true, state.id)
                }
                go_back.setOnClickListenerDebounced {
                    dismiss()
                    host.cancelOrderConfirmAction(false, null)
                }
            }
        } else {
            dismiss()
        }
    }

    companion object {
        private const val FROM_DASHBOARD = "from_dashboard"

        fun newInstance(fromDashboard: Boolean = false): SimpleBuyCancelOrderBottomSheet =
            SimpleBuyCancelOrderBottomSheet().apply {
                arguments = Bundle().also {
                    it.putBoolean(FROM_DASHBOARD, fromDashboard)
                }
            }

        private fun Bundle?.fromDashboard(): Boolean =
            this?.getBoolean(FROM_DASHBOARD, false) ?: false
    }
}
