package piuk.blockchain.android.ui.linkbank.yodlee

import com.blockchain.nabu.models.data.YodleeAttributes
import piuk.blockchain.android.simplebuy.ErrorState

interface YodleeLinkingFlowNavigator {
    fun launchYodleeSplash(attributes: YodleeAttributes, bankId: String)
    fun launchYodleeWebview(attributes: YodleeAttributes, bankId: String)
    fun launchBankLinking(accountProviderId: String, accountId: String, bankId: String)
    fun launchBankLinkingWithError(errorState: ErrorState)
    fun retry()
    fun bankLinkingFinished(bankId: String)
    fun bankLinkingCancelled()
}