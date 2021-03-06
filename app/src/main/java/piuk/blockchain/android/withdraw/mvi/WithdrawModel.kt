package piuk.blockchain.android.withdraw.mvi

import io.reactivex.Scheduler
import io.reactivex.disposables.Disposable
import io.reactivex.rxkotlin.subscribeBy
import piuk.blockchain.android.ui.base.mvi.MviModel

class WithdrawModel(
    mainScheduler: Scheduler,
    private val withdrawInteractor: WithdrawInteractor,
    private val withdrawStatePersistence: WithdrawStatePersistence
) : MviModel<WithdrawState, WithdrawIntent>(withdrawStatePersistence.state, mainScheduler) {

    override fun performAction(previousState: WithdrawState, intent: WithdrawIntent): Disposable? =
        when (intent) {
            is WithdrawIntent.UpdateCurrency -> withdrawInteractor.fetchBalanceForCurrency(intent.currency)
                .subscribeBy(
                    onSuccess = {
                        process(WithdrawIntent.BalanceUpdated(it))
                        process(WithdrawIntent.FetchWithdrawFee(intent.currency))
                        process(WithdrawIntent.FetchLinkedBanks(intent.currency))
                    },
                    onError = {
                        process(WithdrawIntent.ErrorIntent())
                    }
                )
            is WithdrawIntent.FetchLinkedBanks -> withdrawInteractor.fetchLinkedBanks(intent.currency)
                .subscribeBy(
                    onSuccess = {
                        process(WithdrawIntent.BanksUpdated(it))
                    },
                    onError = {
                        process(WithdrawIntent.ErrorIntent())
                    }
                )
            is WithdrawIntent.CreateWithdrawOrder -> withdrawInteractor.createWithdrawOrder(intent.amount,
                intent.bankId).subscribeBy(
                onComplete = {
                    process(WithdrawIntent.WithdrawOrderCreated)
                },
                onError = {
                    process(WithdrawIntent.ErrorIntent())
                }
            )
            is WithdrawIntent.FetchWithdrawFee -> withdrawInteractor.fetchWithdrawFees(intent.currency)
                .subscribeBy(
                    onSuccess = {
                        process(WithdrawIntent.FeeUpdated(it))
                    },
                    onError = {
                        process(WithdrawIntent.ErrorIntent())
                    }
                )
            else -> null
        }

    override fun onStateUpdate(s: WithdrawState) {
        super.onStateUpdate(s)
        withdrawStatePersistence.updateState(s)
    }
}