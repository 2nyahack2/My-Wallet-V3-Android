package piuk.blockchain.android.withdraw

interface WithdrawNavigator {
    fun goToCheckout()
    fun exitFlow()
    fun goToCompleteWithdraw()
    fun hasMoreThanOneFragmentInTheStack(): Boolean
}