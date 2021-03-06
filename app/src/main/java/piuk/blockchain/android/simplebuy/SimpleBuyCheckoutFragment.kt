package piuk.blockchain.android.simplebuy

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.blockchain.koin.scopedInject
import com.blockchain.nabu.datamanagers.OrderState
import com.blockchain.nabu.datamanagers.custodialwalletimpl.PaymentMethodType
import info.blockchain.balance.FiatValue
import kotlinx.android.synthetic.main.fragment_checkout.*
import piuk.blockchain.android.R
import piuk.blockchain.android.ui.base.ErrorSlidingBottomDialog
import piuk.blockchain.android.ui.base.mvi.MviFragment
import piuk.blockchain.android.ui.base.setupToolbar
import piuk.blockchain.android.ui.customviews.BlockchainListDividerDecor
import piuk.blockchain.android.util.secondsToDays
import piuk.blockchain.androidcore.utils.helperfunctions.unsafeLazy
import piuk.blockchain.android.util.gone
import piuk.blockchain.android.util.goneIf
import piuk.blockchain.android.util.inflate
import piuk.blockchain.android.util.setOnClickListenerDebounced
import piuk.blockchain.android.util.visible
import piuk.blockchain.android.util.visibleIf

class SimpleBuyCheckoutFragment : MviFragment<SimpleBuyModel, SimpleBuyIntent, SimpleBuyState>(), SimpleBuyScreen,
    SimpleBuyCancelOrderBottomSheet.Host {

    override val model: SimpleBuyModel by scopedInject()
    private var lastState: SimpleBuyState? = null
    private val checkoutAdapter = CheckoutAdapter()

    private val isForPendingPayment: Boolean by unsafeLazy {
        arguments?.getBoolean(PENDING_PAYMENT_ORDER_KEY, false) ?: false
    }

    private val showOnlyOrderData: Boolean by unsafeLazy {
        arguments?.getBoolean(SHOW_ONLY_ORDER_DATA, false) ?: false
    }

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

        model.process(SimpleBuyIntent.FlowCurrentScreen(FlowScreen.CHECKOUT))
        if (!showOnlyOrderData)
            setUpToolbar()

        model.process(SimpleBuyIntent.FetchQuote)
        model.process(SimpleBuyIntent.FetchWithdrawLockTime)
    }

    private fun setUpToolbar() {
        activity.setupToolbar(
            if (isForPendingPayment) {
                R.string.order_details
            } else {
                R.string.checkout
            },
            !isForPendingPayment
        )
    }

    override fun backPressedHandled(): Boolean =
        isForPendingPayment

    override fun navigator(): SimpleBuyNavigator =
        (activity as? SimpleBuyNavigator) ?: throw IllegalStateException(
            "Parent must implement SimpleBuyNavigator"
        )

    override fun onBackPressed(): Boolean = true

    override fun render(newState: SimpleBuyState) {
        // Event should be triggered only the first time a state is rendered
        if (lastState == null) {
            analytics.logEvent(
                eventWithPaymentMethod(
                    SimpleBuyAnalytics.CHECKOUT_SUMMARY_SHOWN,
                    newState.selectedPaymentMethod?.paymentMethodType?.toAnalyticsString() ?: ""
                )
            )
            lastState = newState
        }

        progress.visibleIf { newState.isLoading }
        val note = when {
            newState.selectedPaymentMethod?.isCard() == true -> {
                getString(R.string.purchase_card_note_1)
            }
            newState.selectedPaymentMethod?.isFunds() == true -> {
                getString(R.string.purchase_funds_note)
            }
            newState.selectedPaymentMethod?.isBank() == true -> {
                newState.withdrawalLockPeriod.secondsToDays().takeIf { it > 0 }?.let {
                    getString(R.string.security_locked_funds_bank_transfer_explanation, it.toString())
                }
            }
            else -> ""
        }

        purchase_note.apply {
            if (note.isNullOrBlank())
                gone()
            else {
                visible()
                text = note
            }
        }

        if (newState.errorState != null) {
            showErrorState(newState.errorState)
            return
        }

        showAmountForMethod(newState)

        updateStatusPill(newState)

        checkoutAdapter.items = getListFields(newState)

        configureButtons(newState)

        when (newState.order.orderState) {
            OrderState.FINISHED, // Funds orders are getting finished right after confirmation
            OrderState.AWAITING_FUNDS -> {
                if (newState.confirmationActionRequested) {
                    navigator().goToPaymentScreen()
                }
            }
            OrderState.CANCELED -> {
                model.process(SimpleBuyIntent.ClearState)
                navigator().exitSimpleBuyFlow()
            }
            else -> {
                // do nothing
            }
        }
    }

    private fun showAmountForMethod(newState: SimpleBuyState) {
        amount.text = newState.orderValue?.toStringWithSymbol()
    }

    private fun updateStatusPill(newState: SimpleBuyState) {
        when {
            isPendingOrAwaitingFunds(newState.orderState) -> {
                status.text = getString(R.string.order_pending)
                status.background =
                    ContextCompat.getDrawable(requireContext(), R.drawable.bkgd_status_unconfirmed)
                status.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.grey_800)
                )
            }
            newState.orderState == OrderState.FINISHED -> {
                status.text = getString(R.string.order_complete)
                status.background =
                    ContextCompat.getDrawable(requireContext(), R.drawable.bkgd_green_100_rounded)
                status.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.green_600)
                )
            }
            else -> {
                status.gone()
            }
        }
    }

    private fun getListFields(state: SimpleBuyState) =
        listOfNotNull(
            CheckoutItem(
                getString(R.string.quote_price, state.selectedCryptoCurrency?.displayTicker),
                state.orderExchangePrice?.toStringWithSymbol() ?: ""
            ),

            CheckoutItem(
                getString(R.string.fee),
                state.fee?.toStringWithSymbol() ?: FiatValue.zero(state.fiatCurrency)
                    .toStringWithSymbol()
            ),

            CheckoutItem(
                getString(R.string.common_total),
                state.order.amount?.toStringWithSymbol() ?: ""
            ),

            CheckoutItem(getString(R.string.payment_method),
                state.selectedPaymentMethod?.let {
                    paymentMethodLabel(it, state.fiatCurrency)
                } ?: ""
            ),
            state.selectedPaymentMethod?.isBank()?.let {
                CheckoutItem(
                    getString(R.string.available_to_trade),
                    getString(R.string.instantly)
                )
            }
        )

    private fun isPendingOrAwaitingFunds(orderState: OrderState) =
        isForPendingPayment || orderState == OrderState.AWAITING_FUNDS

    private fun configureButtons(state: SimpleBuyState) {
        val isOrderAwaitingFunds = state.orderState == OrderState.AWAITING_FUNDS

        button_action.apply {
            if (!isForPendingPayment && !isOrderAwaitingFunds) {
                text = getString(R.string.buy_now_1, state.orderValue?.toStringWithSymbol())
                setOnClickListener {
                    model.process(SimpleBuyIntent.ConfirmOrder)
                    analytics.logEvent(
                        eventWithPaymentMethod(
                            SimpleBuyAnalytics.CHECKOUT_SUMMARY_CONFIRMED,
                            state.selectedPaymentMethod?.paymentMethodType?.toAnalyticsString() ?: ""
                        )
                    )
                }
            } else {
                text = if (isOrderAwaitingFunds && !isForPendingPayment) {
                    getString(R.string.complete_payment)
                } else {
                    getString(R.string.ok_cap)
                }
                setOnClickListener {
                    if (isForPendingPayment) {
                        navigator().exitSimpleBuyFlow()
                    } else {
                        navigator().goToPaymentScreen()
                    }
                }
            }
            visibleIf { !showOnlyOrderData }
        }

        button_action.isEnabled = !state.isLoading
        button_cancel.goneIf { isOrderAwaitingFunds || isForPendingPayment }
        button_cancel.setOnClickListenerDebounced {
            analytics.logEvent(SimpleBuyAnalytics.CHECKOUT_SUMMARY_PRESS_CANCEL)
            showBottomSheet(SimpleBuyCancelOrderBottomSheet.newInstance())
        }
    }

    private fun paymentMethodLabel(
        selectedPaymentMethod: SelectedPaymentMethod,
        fiatCurrency: String
    ): String =
        when (selectedPaymentMethod.paymentMethodType) {
            PaymentMethodType.FUNDS -> getString(R.string.currency_funds_wallet, fiatCurrency)
            else -> selectedPaymentMethod.label ?: ""
        }

    private fun showErrorState(errorState: ErrorState) {
        showBottomSheet(ErrorSlidingBottomDialog.newInstance(activity))
    }

    override fun cancelOrderConfirmAction(cancelOrder: Boolean, orderId: String?) {
        if (cancelOrder) {
            model.process(SimpleBuyIntent.CancelOrder)
            analytics.logEvent(
                eventWithPaymentMethod(
                    SimpleBuyAnalytics.CHECKOUT_SUMMARY_CANCELLATION_CONFIRMED,
                    lastState?.selectedPaymentMethod?.paymentMethodType?.toAnalyticsString()
                        ?: ""
                )
            )
        } else {
            analytics.logEvent(SimpleBuyAnalytics.CHECKOUT_SUMMARY_CANCELLATION_GO_BACK)
        }
    }

    override fun onPause() {
        super.onPause()
        model.process(SimpleBuyIntent.NavigationHandled)
    }

    override fun onSheetClosed() {
        if (lastState?.errorState != null) {
            model.process(SimpleBuyIntent.ClearError)
            model.process(SimpleBuyIntent.ClearState)
            navigator().exitSimpleBuyFlow()
        }
    }

    companion object {
        private const val PENDING_PAYMENT_ORDER_KEY = "PENDING_PAYMENT_KEY"
        private const val SHOW_ONLY_ORDER_DATA = "SHOW_ONLY_ORDER_DATA"

        fun newInstance(isForPending: Boolean = false, showOnlyOrderData: Boolean = false): SimpleBuyCheckoutFragment {
            val fragment = SimpleBuyCheckoutFragment()
            fragment.arguments = Bundle().apply {
                putBoolean(PENDING_PAYMENT_ORDER_KEY, isForPending)
                putBoolean(SHOW_ONLY_ORDER_DATA, showOnlyOrderData)
            }
            return fragment
        }
    }
}