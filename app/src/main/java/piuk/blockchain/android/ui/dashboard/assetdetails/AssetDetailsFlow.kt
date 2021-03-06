package piuk.blockchain.android.ui.dashboard.assetdetails

import androidx.fragment.app.FragmentManager
import com.blockchain.koin.scopedInject
import info.blockchain.balance.CryptoCurrency
import io.reactivex.Single
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.rxkotlin.subscribeBy
import org.koin.core.KoinComponent
import piuk.blockchain.android.R
import piuk.blockchain.android.coincore.AccountGroup
import piuk.blockchain.android.coincore.AssetAction
import piuk.blockchain.android.coincore.AssetFilter
import piuk.blockchain.android.coincore.BlockchainAccount
import piuk.blockchain.android.coincore.Coincore
import piuk.blockchain.android.coincore.CryptoAccount
import piuk.blockchain.android.coincore.InterestAccount
import piuk.blockchain.android.coincore.NullCryptoAccount
import piuk.blockchain.android.coincore.SingleAccount
import piuk.blockchain.android.coincore.TxSourceState
import piuk.blockchain.android.coincore.impl.CryptoAccountCustodialGroup
import piuk.blockchain.android.coincore.impl.CryptoAccountNonCustodialGroup
import piuk.blockchain.android.ui.customviews.account.AccountSelectSheet
import piuk.blockchain.android.ui.transactionflow.DialogFlow
import timber.log.Timber

enum class AssetDetailsStep {
    ZERO,
    CUSTODY_INTRO_SHEET,
    ASSET_DETAILS,
    ASSET_ACTIONS,
    SELECT_ACCOUNT
}

class AssetDetailsFlow(
    val cryptoCurrency: CryptoCurrency,
    val coincore: Coincore
) : DialogFlow(), KoinComponent, AccountSelectSheet.SelectAndBackHost {

    interface AssetDetailsHost : FlowHost {
        fun launchNewSendFor(account: SingleAccount, action: AssetAction)
        fun goToReceiveFor(account: SingleAccount)
        fun gotoActivityFor(account: BlockchainAccount)
        fun gotoSwap(account: SingleAccount)
        fun goToSellFrom(account: CryptoAccount)
        fun goToDeposit(
            fromAccount: SingleAccount,
            toAccount: SingleAccount,
            action: AssetAction
        )

        fun goToSummary(account: SingleAccount, asset: CryptoCurrency)
        fun goToInterestDashboard()
    }

    private var currentStep: AssetDetailsStep = AssetDetailsStep.ZERO
    private var localState: AssetDetailsState = AssetDetailsState()
    private val disposables = CompositeDisposable()
    private val model: AssetDetailsModel by scopedInject()
    private lateinit var assetFlowHost: AssetDetailsHost

    override fun startFlow(fragmentManager: FragmentManager, host: FlowHost) {
        super.startFlow(fragmentManager, host)

        assetFlowHost = host as? AssetDetailsHost
            ?: throw IllegalStateException("Flow Host is not an AssetDetailsHost")

        model.apply {
            disposables += state.subscribeBy(
                onNext = { handleStateChange(it) },
                onError = { Timber.e("Asset details state is broken: $it") }
            )
        }

        model.process(ShowRelevantAssetDetailsSheet(cryptoCurrency))
    }

    private fun handleStateChange(
        newState: AssetDetailsState
    ) {

        if (currentStep != newState.assetDetailsCurrentStep) {
            currentStep = newState.assetDetailsCurrentStep
            if (currentStep == AssetDetailsStep.ZERO) {
                finishFlow()
            } else {
                showFlowStep(currentStep)
            }
        }

        if (newState.hostAction != null && localState.hostAction != newState.hostAction) {
            handleHostAction(newState, assetFlowHost)
        }

        if (newState.navigateToInterestDashboard) {
            assetFlowHost.goToInterestDashboard()
        }

        localState = newState
    }

    private fun showFlowStep(step: AssetDetailsStep) {
        replaceBottomSheet(
            when (step) {
                AssetDetailsStep.ZERO -> null
                AssetDetailsStep.CUSTODY_INTRO_SHEET -> CustodyWalletIntroSheet.newInstance()
                AssetDetailsStep.ASSET_DETAILS -> AssetDetailSheet.newInstance(cryptoCurrency)
                AssetDetailsStep.ASSET_ACTIONS -> AssetActionsSheet.newInstance()
                AssetDetailsStep.SELECT_ACCOUNT -> AccountSelectSheet.newInstance(
                    this,
                    filterNonCustodialAccounts(localState.hostAction),
                    when (localState.hostAction) {
                        AssetAction.Deposit -> R.string.select_deposit_source_title
                        AssetAction.Send -> R.string.select_send_sheet_title
                        else -> R.string.select_account_sheet_title
                    }
                )
            }
        )
    }

    private fun filterNonCustodialAccounts(
        action: AssetAction?
    ): Single<List<BlockchainAccount>> =
        coincore[cryptoCurrency].accountGroup(AssetFilter.NonCustodial)
            .map { it.accounts }.toSingle(emptyList())
            .flattenAsObservable { it }
            .flatMapSingle { account ->
                account.actions.map { actions ->
                    if (
                        actions.contains(action) ||
                        (action == AssetAction.Deposit && account.isFunded)
                    ) {
                        account
                    } else NullCryptoAccount()
                }
            }
            .filter { it !is NullCryptoAccount }
            .map { it as BlockchainAccount }
            .toList()

    private fun handleHostAction(
        newState: AssetDetailsState,
        host: AssetDetailsHost
    ) {
        when (newState.hostAction) {
            AssetAction.ViewActivity -> {
                selectAccountOrPerformAction(
                    state = newState,
                    singleAccountAction = {
                        launchActivity(it)
                    }
                )
            }
            AssetAction.Send -> {
                selectAccountOrPerformAction(
                    state = newState,
                    singleAccountAction = {
                        launchNewSend(it)
                    }
                )
            }
            AssetAction.Receive -> {
                selectAccountOrPerformAction(
                    state = newState,
                    singleAccountAction = {
                        launchReceive(it)
                    }
                )
            }
            AssetAction.Swap -> {
                selectAccountOrPerformAction(
                    state = newState,
                    singleAccountAction = {
                        launchSwap(it)
                    }
                )
            }
            AssetAction.Sell -> {
                selectAccountOrPerformAction(
                    state = newState,
                    singleAccountAction = {
                        host.goToSellFrom(it as CryptoAccount)
                        finishFlow()
                    }
                )
            }
            AssetAction.Summary -> getInterestAccountAndNavigate(
                newState.selectedAccount.selectFirstAccount(), newState.hostAction
            )
            AssetAction.Deposit -> {
                selectAccountOrPerformAction(
                    state = newState,
                    singleAccountAction = {
                        getInterestAccountAndNavigate(it, newState.hostAction)
                    }
                )
            }
        }
    }

    private fun selectAccountOrPerformAction(
        state: AssetDetailsState,
        singleAccountAction: (SingleAccount) -> Unit
    ) {
        state.selectedAccount?.let {
            when (it) {
                is CryptoAccountCustodialGroup -> {
                    val firstAccount = it.accounts.first()
                    if (firstAccount is InterestAccount) {
                        if (state.hostAction == AssetAction.ViewActivity) {
                            singleAccountAction(firstAccount)
                        } else {
                            selectFromAccounts(state, singleAccountAction)
                        }
                    } else {
                        singleAccountAction(firstAccount)
                    }
                }
                is CryptoAccountNonCustodialGroup -> {
                    selectFromAccounts(state, singleAccountAction)
                }
                else -> throw IllegalStateException("Unsupported Account type $it")
            }
        }
    }

    private fun selectFromAccounts(
        state: AssetDetailsState,
        singleAccountAction: (SingleAccount) -> Unit
    ) {
        disposables += coincore[state.asset!!.asset].accountGroup(AssetFilter.NonCustodial)
            .subscribeBy { ag ->
                when {
                    ag.accounts.size > 1 -> {
                        model.process(SelectAccount)
                    }
                    ag.accounts.size == 1 -> {
                        singleAccountAction(ag.accounts.first())
                    }
                    else -> throw IllegalStateException("Error when getting non-custodial accounts")
                }
            }
    }

    override fun finishFlow() {
        model.process(ClearSheetDataIntent)
        disposables.clear()
        currentStep = AssetDetailsStep.ZERO
        super.finishFlow()
    }

    override fun onAccountSelected(account: BlockchainAccount) {
        val singleAccount = account as SingleAccount
        when (localState.hostAction) {
            AssetAction.Deposit -> getInterestAccountAndNavigate(singleAccount, AssetAction.Deposit)
            AssetAction.Send -> launchNewSend(singleAccount)
            AssetAction.Sell -> launchSell(singleAccount)
            AssetAction.ViewActivity -> launchActivity(singleAccount)
            AssetAction.Swap -> launchSwap(singleAccount)
            AssetAction.Receive -> launchReceive(singleAccount)
            else -> throw IllegalStateException(
                "Account selection not supported for this action ${localState.hostAction}"
            )
        }
    }

    private fun launchSell(singleAccount: SingleAccount) {
        (singleAccount as? CryptoAccount)?.let {
            assetFlowHost.goToSellFrom(it)
            finishFlow()
        }
    }

    private fun getInterestAccountAndNavigate(account: SingleAccount, assetAction: AssetAction) {
        localState.asset?.let { ca ->
            disposables += account.sourceState.subscribeBy {
                if (it == TxSourceState.TRANSACTION_IN_FLIGHT) {
                    model.process(TransactionInFlight)
                } else {
                    ca.accountGroup(AssetFilter.Interest).subscribeBy { ag ->
                        if (assetAction == AssetAction.Deposit) {
                            assetFlowHost.goToDeposit(
                                account,
                                ag.accounts.first(),
                                assetAction
                            )
                        } else if (assetAction == AssetAction.Summary) {
                            localState.asset?.asset?.let {
                                assetFlowHost.goToSummary(ag.accounts.first(), it)
                            } ?: throw IllegalStateException("No crypto defined for Interest Summary")
                            finishFlow()
                        }
                    }
                }
            }
        } ?: throw IllegalStateException("No asset defined in local state - action: $assetAction")
    }

    private fun launchNewSend(account: SingleAccount) {
        assetFlowHost.launchNewSendFor(account, AssetAction.Send)
        finishFlow()
    }

    private fun launchReceive(account: SingleAccount) {
        assetFlowHost.goToReceiveFor(account)
        finishFlow()
    }

    private fun launchSwap(account: SingleAccount) {
        assetFlowHost.gotoSwap(account)
        finishFlow()
    }

    private fun launchActivity(account: SingleAccount) {
        assetFlowHost.gotoActivityFor(account)
        finishFlow()
    }

    override fun onAccountSelectorBack() {
        model.process(ReturnToPreviousStep)
    }

    override fun onSheetClosed() {
        finishFlow()
    }
}

fun BlockchainAccount?.selectFirstAccount(): CryptoAccount {
    val selectedAccount = when (this) {
        is SingleAccount -> this
        is AccountGroup -> this.accounts
            .firstOrNull { a -> a.isDefault }
            ?: this.accounts.firstOrNull()
            ?: throw IllegalStateException("No SingleAccount found")
        else -> throw IllegalStateException("Unknown account base")
    }

    return selectedAccount as CryptoAccount
}