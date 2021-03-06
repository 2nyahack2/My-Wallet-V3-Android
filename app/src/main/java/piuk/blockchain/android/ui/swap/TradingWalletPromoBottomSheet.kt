package piuk.blockchain.android.ui.swap

import android.view.View
import kotlinx.android.synthetic.main.dialog_sheet_swap_trading_wallet.view.*
import piuk.blockchain.android.R
import piuk.blockchain.android.ui.base.SlidingModalBottomDialog

class TradingWalletPromoBottomSheet : SlidingModalBottomDialog() {
    interface Host : SlidingModalBottomDialog.Host {
        fun startNewSwap()
    }

    override val host: Host by lazy {
        super.host as? Host ?: throw IllegalStateException(
            "Host fragment is not a TradingWalletPromoBottomSheet.Host")
    }

    override val layoutResource: Int
        get() = R.layout.dialog_sheet_swap_trading_wallet

    override fun initControls(view: View) {
        view.swap_trading_cta.setOnClickListener {
            dismiss()
            host.startNewSwap()
        }
    }

    companion object {
        fun newInstance(): TradingWalletPromoBottomSheet = TradingWalletPromoBottomSheet()
    }
}