package piuk.blockchain.android.ui.transactionflow

import androidx.annotation.CallSuper
import androidx.annotation.UiThread
import androidx.fragment.app.FragmentManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import io.reactivex.Scheduler
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.rxkotlin.subscribeBy
import org.koin.core.KoinComponent
import org.koin.core.inject
import piuk.blockchain.android.coincore.AssetAction
import piuk.blockchain.android.coincore.CryptoAccount
import piuk.blockchain.android.coincore.NullCryptoAccount
import piuk.blockchain.android.coincore.TransactionTarget
import piuk.blockchain.android.ui.base.SlidingModalBottomDialog
import piuk.blockchain.android.ui.base.mvi.MviFragment.Companion.BOTTOM_SHEET
import piuk.blockchain.android.ui.transactionflow.analytics.TxFlowAnalytics
import piuk.blockchain.android.ui.transactionflow.engine.TransactionIntent
import piuk.blockchain.android.ui.transactionflow.engine.TransactionModel
import piuk.blockchain.android.ui.transactionflow.engine.TransactionState
import piuk.blockchain.android.ui.transactionflow.engine.TransactionStep
import piuk.blockchain.android.ui.transactionflow.flow.ActiveTransactionFlow
import piuk.blockchain.android.ui.transactionflow.flow.ConfirmTransactionSheet
import piuk.blockchain.android.ui.transactionflow.flow.EnterAmountSheet
import piuk.blockchain.android.ui.transactionflow.flow.EnterSecondPasswordSheet
import piuk.blockchain.android.ui.transactionflow.flow.EnterTargetAddressSheet
import piuk.blockchain.android.ui.transactionflow.flow.SelectSourceAccountSheet
import piuk.blockchain.android.ui.transactionflow.flow.SelectTargetAccountSheet
import piuk.blockchain.android.ui.transactionflow.flow.TransactionProgressSheet
import timber.log.Timber

abstract class DialogFlow : SlidingModalBottomDialog.Host {

    private var fragmentManager: FragmentManager? = null
    private var host: FlowHost? = null
    private var bottomSheetTag: String = SHEET_FRAGMENT_TAG

    interface FlowHost {
        fun onFlowFinished()
    }

    @CallSuper
    open fun startFlow(
        fragmentManager: FragmentManager,
        host: FlowHost
    ) {
        this.fragmentManager = fragmentManager
        this.host = host
    }

    @CallSuper
    open fun finishFlow() {
        if (fragmentManager?.isDestroyed == true)
            return
        host?.onFlowFinished()
    }

    @UiThread
    protected fun replaceBottomSheet(bottomSheet: BottomSheetDialogFragment?) {
        fragmentManager?.let {
            if (it.isDestroyed || it.isStateSaved) return
            val oldSheet = it.findFragmentByTag(bottomSheetTag)
            it.beginTransaction().run {
                apply { oldSheet?.let { sheet -> remove(sheet) } }
                apply { bottomSheet?.let { sheet -> add(sheet, bottomSheetTag) } }
                commitNowAllowingStateLoss()
            }
        }
    }

    companion object {
        const val SHEET_FRAGMENT_TAG = BOTTOM_SHEET
    }
}

class TransactionFlow(
    private val sourceAccount: CryptoAccount = NullCryptoAccount(),
    private val target: TransactionTarget = NullCryptoAccount(),
    private val action: AssetAction,
    private val uiScheduler: Scheduler = AndroidSchedulers.mainThread()
) : DialogFlow(), KoinComponent {

    private val disposables: CompositeDisposable = CompositeDisposable()
    private var currentStep: TransactionStep = TransactionStep.ZERO

    private val analyticsHooks: TxFlowAnalytics by inject()
    private val activeTransactionFlow: ActiveTransactionFlow by transactionInject()

    override fun startFlow(
        fragmentManager: FragmentManager,
        host: FlowHost
    ) {
        super.startFlow(fragmentManager, host)
        // Create the send scope
        openScope()
        // Get the model
        model.apply {
            // Trigger intent to set initial state: source account & password required
            disposables += state.subscribeBy(
                onNext = { handleStateChange(it) },
                onError = { Timber.e("Transaction state is broken: $it") }
            )
        }

        // Persist the flow
        activeTransactionFlow.setFlow(this)

        disposables += sourceAccount.requireSecondPassword()
            .observeOn(uiScheduler)
            .subscribeBy(
                onSuccess = { passwordRequired ->
                    when {
                        target !is NullCryptoAccount && sourceAccount !is NullCryptoAccount -> {
                            if (action == AssetAction.Swap) {
                                model.process(
                                    TransactionIntent.InitialiseWithSourceAndPreferredTarget(
                                        action, sourceAccount, target, passwordRequired
                                    )
                                )
                            } else {
                                model.process(
                                    TransactionIntent.InitialiseWithSourceAndTargetAccount(
                                        action, sourceAccount, target, passwordRequired
                                    )
                                )
                            }
                        }
                        sourceAccount !is NullCryptoAccount -> {
                            model.process(
                                TransactionIntent.InitialiseWithSourceAccount(action, sourceAccount, passwordRequired)
                            )
                        }
                        else -> {
                            model.process(
                                TransactionIntent.InitialiseWithNoSourceOrTargetAccount(action, passwordRequired)
                            )
                        }
                    }
                },
                onError = {
                    Timber.e("Unable to configure transaction flow, aborting. e == $it")
                    finishFlow()
                })
    }

    override fun finishFlow() {
        model.process(TransactionIntent.ResetFlow)
        super.finishFlow()
    }

    private fun handleStateChange(newState: TransactionState) {
        if (currentStep == newState.currentStep)
            return

        when (newState.currentStep) {
            TransactionStep.ZERO -> kotlin.run {
                onTransactionComplete()
            }
            TransactionStep.CLOSED -> kotlin.run {
                disposables.clear()
                model.destroy()
                closeScope()
            }
            else -> kotlin.run {
                analyticsHooks.onStepChanged(newState)
            }
        }
        newState.currentStep.takeIf { it != TransactionStep.ZERO }?.let {
            showFlowStep(it)
            currentStep = it
        }
    }

    private fun showFlowStep(step: TransactionStep) {
        val stepSheet = when (step) {
            TransactionStep.ZERO,
            TransactionStep.CLOSED -> null
            TransactionStep.ENTER_PASSWORD -> EnterSecondPasswordSheet()
            TransactionStep.SELECT_SOURCE -> SelectSourceAccountSheet()
            TransactionStep.ENTER_ADDRESS -> EnterTargetAddressSheet()
            TransactionStep.ENTER_AMOUNT -> EnterAmountSheet()
            TransactionStep.SELECT_TARGET_ACCOUNT -> SelectTargetAccountSheet()
            TransactionStep.CONFIRM_DETAIL -> ConfirmTransactionSheet()
            TransactionStep.IN_PROGRESS -> TransactionProgressSheet()
        }
        replaceBottomSheet(stepSheet)
    }

    private fun openScope() =
        try {
            createTransactionScope()
        } catch (e: Throwable) {
            Timber.wtf("$e")
        }

    private fun closeScope() =
        closeTransactionScope()

    private val model: TransactionModel
        get() = transactionScope().get()

    private fun onTransactionComplete() =
        finishFlow()

    override fun onSheetClosed() {
        finishFlow()
    }
}
