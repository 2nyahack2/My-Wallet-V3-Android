package piuk.blockchain.android.simplebuy

import com.blockchain.nabu.models.data.LinkBankTransfer
import info.blockchain.balance.CryptoCurrency
import piuk.blockchain.android.ui.base.FlowFragment
import piuk.blockchain.android.ui.base.SlidingModalBottomDialog
import piuk.blockchain.android.ui.linkbank.yodlee.YodleeLinkingFlowNavigator

interface SimpleBuyScreen : SlidingModalBottomDialog.Host, FlowFragment {
    fun navigator(): SimpleBuyNavigator

    override fun onSheetClosed() {}
}

interface SimpleBuyNavigator : SlidingModalBottomDialog.Host, YodleeLinkingFlowNavigator {
    fun exitSimpleBuyFlow()
    fun goToBuyCryptoScreen(addToBackStack: Boolean = true, preselectedCrypto: CryptoCurrency)
    fun goToCheckOutScreen(addToBackStack: Boolean = true)
    fun goToKycVerificationScreen(addToBackStack: Boolean = true)
    fun goToPendingOrderScreen()
    fun startKyc()
    fun pop()
    fun linkBankWithPartner(bankTransfer: LinkBankTransfer)
    fun hasMoreThanOneFragmentInTheStack(): Boolean
    fun goToPaymentScreen(addToBackStack: Boolean = true)
    fun launchIntro()
}