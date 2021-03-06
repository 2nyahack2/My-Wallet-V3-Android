package piuk.blockchain.android.withdraw

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.blockchain.nabu.datamanagers.Beneficiary
import kotlinx.android.synthetic.main.layout_linked_bank.view.*
import kotlinx.android.synthetic.main.simple_buy_crypto_currency_chooser.view.*
import piuk.blockchain.android.R
import piuk.blockchain.android.ui.adapters.AdapterDelegate
import piuk.blockchain.android.ui.adapters.AdapterDelegatesManager
import piuk.blockchain.android.ui.adapters.DelegationAdapter
import piuk.blockchain.android.ui.base.SlidingModalBottomDialog
import piuk.blockchain.androidcore.utils.helperfunctions.unsafeLazy
import java.io.Serializable

class BankChooserBottomSheet : SlidingModalBottomDialog() {

    private val beneficiaries: List<Beneficiary> by unsafeLazy {
        arguments?.getSerializable(LINKED_BANKS) as? List<Beneficiary>
            ?: emptyList()
    }
    private val currency: String by unsafeLazy {
        arguments?.getString(CURRENCY)
            ?: throw IllegalStateException("Currency not provided")
    }

    override val layoutResource: Int = piuk.blockchain.android.R.layout.layout_bank_chooser_bottom_sheet

    override fun initControls(view: View) {
        view.recycler.adapter =
            BanksAdapter(
                beneficiaries
                    .map {
                        it.toBankItem()
                    }.let {
                        it.plus(
                            BankChooserItem.AddBankItem {
                                dismiss()
                                (parentFragment as? BankChooserHost)?.addBankWithCurrency(currency)
                            }
                        )
                    })

        view.recycler.layoutManager = LinearLayoutManager(context)
    }

    private fun Beneficiary.toBankItem(): BankChooserItem.BankItem =
        BankChooserItem.BankItem(this) {
            dismiss()
            (parentFragment as? BankChooserHost)?.onNewBankSelected(
                this
            )
        }

    companion object {
        private const val LINKED_BANKS = "linked_banks_key"
        private const val CURRENCY = "currency_key"
        fun newInstance(beneficiaries: List<Beneficiary>, currency: String):
                BankChooserBottomSheet {
            val bundle = Bundle()
            bundle.putSerializable(LINKED_BANKS, beneficiaries as Serializable)
            bundle.putString(CURRENCY, currency)
            return BankChooserBottomSheet().apply {
                arguments = bundle
            }
        }
    }
}

private class BanksAdapter(adapterItems: List<BankChooserItem>) :
    DelegationAdapter<BankChooserItem>(AdapterDelegatesManager(), adapterItems) {
    init {
        val bankPaymentDelegate = LinkedBankItemDelegate()
        val addBankPaymentDelegate = AddBankPaymentDelegate()

        delegatesManager.apply {
            addAdapterDelegate(bankPaymentDelegate)
            addAdapterDelegate(addBankPaymentDelegate)
        }
    }
}

sealed class BankChooserItem(val clickAction: () -> Unit) {
    class BankItem(val bank: Beneficiary, clickAction: () -> Unit) : BankChooserItem(clickAction)
    class AddBankItem(clickAction: () -> Unit) : BankChooserItem(clickAction)
}

private class LinkedBankItemDelegate :
    AdapterDelegate<BankChooserItem> {
    override fun isForViewType(items: List<BankChooserItem>, position: Int): Boolean =
        items[position] is BankChooserItem.BankItem

    override fun onCreateViewHolder(parent: ViewGroup): RecyclerView.ViewHolder {
        val itemView =
            LayoutInflater.from(parent.context).inflate(
                R.layout.layout_linked_bank,
                parent,
                false
            )
        return ViewHolder(itemView)
    }

    override fun onBindViewHolder(items: List<BankChooserItem>, position: Int, holder: RecyclerView.ViewHolder) {
        val viewHolder = holder as ViewHolder
        viewHolder.bind(items[position])
    }

    private class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: AppCompatTextView = itemView.linked_bank_title
        val root: ViewGroup = itemView.root

        fun bind(item: BankChooserItem) {
            (item as? BankChooserItem.BankItem)?.let {
                title.text = it.bank.name.plus(" ${it.bank.account}")
            }
            root.setOnClickListener { item.clickAction() }
        }
    }
}

private class AddBankPaymentDelegate :
    AdapterDelegate<BankChooserItem> {
    override fun isForViewType(items: List<BankChooserItem>, position: Int): Boolean =
        items[position] is BankChooserItem.AddBankItem

    override fun onCreateViewHolder(parent: ViewGroup): RecyclerView.ViewHolder {
        val itemView = LayoutInflater.from(parent.context).inflate(
            R.layout.layout_link_new_bank,
            parent,
            false
        )
        return ViewHolder(itemView)
    }

    override fun onBindViewHolder(items: List<BankChooserItem>, position: Int, holder: RecyclerView.ViewHolder) {
        val viewHolder = holder as ViewHolder
        viewHolder.bind(items[position])
    }

    private class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val root: ViewGroup = itemView.root

        fun bind(item: BankChooserItem) {
            root.setOnClickListener { item.clickAction() }
        }
    }
}