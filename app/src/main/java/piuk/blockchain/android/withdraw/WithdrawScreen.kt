package piuk.blockchain.android.withdraw

import piuk.blockchain.android.ui.base.FlowFragment

interface WithdrawScreen : FlowFragment {
    fun navigator(): WithdrawNavigator
}