package piuk.blockchain.android.ui.dashboard.sheets

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.blockchain.nabu.datamanagers.custodialwalletimpl.PaymentMethodType
import kotlinx.android.synthetic.main.link_bank_method_chooser_sheet_layout.view.*
import kotlinx.android.synthetic.main.link_bank_method_item.view.*
import piuk.blockchain.android.R
import piuk.blockchain.android.ui.base.SlidingModalBottomDialog
import piuk.blockchain.android.ui.settings.BankLinkingHost
import piuk.blockchain.android.ui.settings.LinkableBank
import piuk.blockchain.android.util.visibleIf
import java.lang.IllegalStateException

class LinkBankMethodChooserBottomSheet : SlidingModalBottomDialog() {
    private val bank: LinkableBank
        get() = arguments?.getSerializable(LINKABLE_BANK) as LinkableBank
    override val layoutResource: Int
        get() = R.layout.link_bank_method_chooser_sheet_layout

    override fun initControls(view: View) {
        with(view) {
            recycler.layoutManager = LinearLayoutManager(activity)
            recycler.adapter = LinkBankMethodChooserAdapter(bank.linkMethods) {
                when (it) {
                    PaymentMethodType.BANK_TRANSFER -> kotlin.run {
                        (parentFragment as? BankLinkingHost)?.linkBankWithBankTransfer(
                            bank.currency
                        )
                        dismiss()
                    }
                    PaymentMethodType.FUNDS -> kotlin.run {
                        (parentFragment as? BankLinkingHost)?.linkBankWithWireTransfer(
                            bank.currency
                        )
                        dismiss()
                    }
                    else -> throw IllegalStateException("Not supported linking method")
                }
            }
        }
    }

    companion object {
        private const val LINKABLE_BANK = "LINKABLE_BANK"
        fun newInstance(linkableBank: LinkableBank): LinkBankMethodChooserBottomSheet =
            LinkBankMethodChooserBottomSheet().apply {
                arguments = Bundle().apply {
                    putSerializable(LINKABLE_BANK, linkableBank)
                }
            }
    }
}

class LinkBankMethodChooserAdapter(
    private val paymentMethods: List<PaymentMethodType>,
    private val onClick: (PaymentMethodType) -> Unit
) : RecyclerView.Adapter<LinkBankMethodChooserAdapter.LinkBankMethodViewHolder>() {

    class LinkBankMethodViewHolder(private val parent: View) : RecyclerView.ViewHolder(parent) {
        fun bind(paymentMethod: PaymentMethodType, onClick: (PaymentMethodType) -> Unit) {
            val item = paymentMethod.toLinkBankMethodItemUI()
            with(parent) {
                payment_method_title.setText(item.title)
                subtitle.setText(item.subtitle)
                payment_method_icon.setImageResource(item.icon)
                payment_method_root.setOnClickListener {
                    onClick(paymentMethod)
                }
                badge.visibleIf { paymentMethod == PaymentMethodType.BANK_TRANSFER }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LinkBankMethodViewHolder {
        val itemView =
            LayoutInflater.from(parent.context).inflate(
                R.layout.link_bank_method_item,
                parent,
                false
            )
        return LinkBankMethodViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: LinkBankMethodViewHolder, position: Int) {
        holder.bind(paymentMethods[position], onClick)
    }

    override fun getItemCount(): Int = paymentMethods.size
}

private fun PaymentMethodType.toLinkBankMethodItemUI(): LinkBankMethodItem =
    when (this) {
        PaymentMethodType.FUNDS -> LinkBankMethodItem(
            title = R.string.bank_wire_transfer,
            subtitle = R.string.link_a_bank_wire_transfer,
            icon = R.drawable.ic_funds_deposit
        )
        PaymentMethodType.BANK_TRANSFER -> LinkBankMethodItem(
            title = R.string.link_a_bank,
            subtitle = R.string.link_a_bank_bank_transfer,
            icon = R.drawable.ic_bank_transfer
        )
        else -> throw IllegalStateException("Not supported linking method")
    }

data class LinkBankMethodItem(
    @StringRes val title: Int,
    @StringRes val subtitle: Int,
    @DrawableRes val icon: Int
)