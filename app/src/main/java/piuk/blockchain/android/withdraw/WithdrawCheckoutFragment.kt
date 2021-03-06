package piuk.blockchain.android.withdraw

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import com.blockchain.koin.scopedInject
import com.blockchain.notifications.analytics.AnalyticsEvent
import piuk.blockchain.android.simplebuy.SimpleBuyAnalytics
import piuk.blockchain.android.simplebuy.withdrawEventWithCurrency
import kotlinx.android.synthetic.main.fragment_checkout.*
import piuk.blockchain.android.R
import piuk.blockchain.android.simplebuy.CheckoutAdapter
import piuk.blockchain.android.simplebuy.CheckoutItem
import piuk.blockchain.android.ui.base.mvi.MviFragment
import piuk.blockchain.android.ui.base.setupToolbar
import piuk.blockchain.android.ui.customviews.BlockchainListDividerDecor
import piuk.blockchain.android.withdraw.mvi.WithdrawIntent
import piuk.blockchain.android.withdraw.mvi.WithdrawModel
import piuk.blockchain.android.withdraw.mvi.WithdrawState
import piuk.blockchain.android.util.gone
import piuk.blockchain.android.util.inflate

class WithdrawCheckoutFragment : MviFragment<WithdrawModel, WithdrawIntent, WithdrawState>(), WithdrawScreen {
    override val model: WithdrawModel by scopedInject()
    private val checkoutAdapter = CheckoutAdapter()
    private lateinit var confirmEvent: () -> AnalyticsEvent
    private lateinit var cancelEvent: () -> AnalyticsEvent

    override fun render(newState: WithdrawState) {
        newState.amount?.let {
            amount.text = it.toStringWithSymbol()
            button_action.text = getString(R.string.withdraw_amount, it.toStringWithSymbol())
        }

        analytics.logEventOnceForSession(withdrawEventWithCurrency(
            analytics = SimpleBuyAnalytics.WITHDRAWAL_CHECKOUT_SHOWN,
            currency = newState.currency ?: "",
            amount = newState.amount?.toBigInteger().toString()
        ))
        confirmEvent = {
            withdrawEventWithCurrency(
                analytics = SimpleBuyAnalytics.WITHDRAWAL_CHECKOUT_CONFIRM,
                currency = newState.currency ?: ""
            )
        }
        cancelEvent = {
            withdrawEventWithCurrency(
                analytics = SimpleBuyAnalytics.WITHDRAWAL_CHECKOUT_CANCEL,
                currency = newState.currency ?: ""
            )
        }

        checkoutAdapter.items = fields(newState)
    }

    private fun fields(state: WithdrawState) =
        listOf(
            CheckoutItem(getString(R.string.common_from), getString(R.string.currency_funds_wallet, state.currency)),
            CheckoutItem(
                getString(R.string.withdraw_to),
                state.selectedBank?.name?.plus(" ${state.selectedBank.account}") ?: ""
            ),
            CheckoutItem(getString(R.string.fee), state.fee?.toStringWithSymbol() ?: ""),
            CheckoutItem(getString(R.string.common_total), state.total?.toStringWithSymbol() ?: "")
        )

    override fun navigator(): WithdrawNavigator =
        (activity as? WithdrawNavigator)
            ?: throw IllegalStateException("Parent must implement WithdrawNavigator")

    override fun onBackPressed(): Boolean = true

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = container?.inflate(R.layout.fragment_checkout)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        recycler.apply {
            layoutManager = LinearLayoutManager(activity)
            adapter = checkoutAdapter
            addItemDecoration(BlockchainListDividerDecor(requireContext()))
        }
        status.gone()
        progress.gone()
        button_action.setOnClickListener {
            navigator().goToCompleteWithdraw()
            analytics.logEvent(confirmEvent())
        }
        button_cancel.setOnClickListener {
            navigator().exitFlow()
            analytics.logEvent(cancelEvent())
        }

        activity.setupToolbar(R.string.checkout)
    }
}