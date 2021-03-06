package piuk.blockchain.android.simplebuy

import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import com.blockchain.nabu.datamanagers.PaymentMethod
import kotlinx.android.synthetic.main.simple_buy_crypto_currency_chooser.view.recycler
import kotlinx.android.synthetic.main.simple_buy_payment_method_chooser.view.*
import piuk.blockchain.android.R
import piuk.blockchain.android.ui.adapters.AdapterDelegatesManager
import piuk.blockchain.android.ui.adapters.DelegationAdapter
import piuk.blockchain.android.ui.base.SlidingModalBottomDialog
import piuk.blockchain.android.util.visibleIf
import piuk.blockchain.androidcore.utils.helperfunctions.unsafeLazy
import java.io.Serializable

class PaymentMethodChooserBottomSheet : SlidingModalBottomDialog() {
    private val paymentMethods: List<PaymentMethod> by unsafeLazy {
        arguments?.getSerializable(SUPPORTED_PAYMENT_METHODS) as? List<PaymentMethod>
            ?: emptyList()
    }

    override val layoutResource: Int
        get() = R.layout.simple_buy_payment_method_chooser

    override fun initControls(view: View) {
        view.recycler.adapter =
            PaymentMethodsAdapter(
                paymentMethods
                    .map {
                        it.toPaymentMethodItem()
                    })

        view.recycler.layoutManager = LinearLayoutManager(context)
        val isShowingPaymentMethods = paymentMethods.all { it.canUsedForPaying() }

        view.add_payment_method.visibleIf { isShowingPaymentMethods }
        view.title.text =
            if (isShowingPaymentMethods) getString(R.string.pay_with_my_dotted) else getString(R.string.payment_methods)
        view.add_payment_method.setOnClickListener {
            (parentFragment as? PaymentMethodChangeListener)?.showAvailableToAddPaymentMethods()
            dismiss()
        }

        analytics.logEvent(paymentMethodsShown(paymentMethods.map { it.toAnalyticsString() }.joinToString { "," }))
    }

    private fun PaymentMethod.toPaymentMethodItem(): PaymentMethodItem {
        return PaymentMethodItem(this, clickAction())
    }

    private fun PaymentMethod.clickAction(): () -> Unit =
        {
            (parentFragment as? PaymentMethodChangeListener)?.onPaymentMethodChanged(this)
            dismiss()
        }

    companion object {
        private const val SUPPORTED_PAYMENT_METHODS = "supported_payment_methods_key"

        fun newInstance(
            paymentMethods: List<PaymentMethod>
        ): PaymentMethodChooserBottomSheet {
            val bundle = Bundle()
            bundle.putSerializable(SUPPORTED_PAYMENT_METHODS, paymentMethods as Serializable)
            return PaymentMethodChooserBottomSheet().apply {
                arguments = bundle
            }
        }
    }
}

data class PaymentMethodItem(val paymentMethod: PaymentMethod, val clickAction: () -> Unit)

private class PaymentMethodsAdapter(adapterItems: List<PaymentMethodItem>) :
    DelegationAdapter<PaymentMethodItem>(AdapterDelegatesManager(), adapterItems) {
    init {
        val cardPaymentDelegate = CardPaymentDelegate()
        val bankPaymentDelegate = BankPaymentDelegate()
        val addFundsPaymentDelegate = AddFundsDelegate()
        val addCardPaymentDelegate = AddCardDelegate()
        val linkBankPaymentDelegate = LinkBankDelegate()
        val fundsPaymentDelegate = FundsPaymentDelegate()

        delegatesManager.apply {
            addAdapterDelegate(cardPaymentDelegate)
            addAdapterDelegate(fundsPaymentDelegate)
            addAdapterDelegate(addCardPaymentDelegate)
            addAdapterDelegate(linkBankPaymentDelegate)
            addAdapterDelegate(bankPaymentDelegate)
            addAdapterDelegate(addFundsPaymentDelegate)
        }
    }
}