package piuk.blockchain.android.ui.customviews

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.verify_identity_benefit_layout.view.*
import piuk.blockchain.android.R
import kotlin.properties.Delegates

class BenefitsAdapter : RecyclerView.Adapter<BenefitsAdapter.BenefitsViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BenefitsViewHolder {
        val layout = LayoutInflater.from(parent.context).inflate(
            R.layout.verify_identity_benefit_layout,
            parent,
            false
        )
        return BenefitsViewHolder(layout)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: BenefitsViewHolder, position: Int) {
        holder.bind(items[position], position + 1)
    }

    var items: List<VerifyIdentityBenefit> by Delegates.observable(emptyList()) { _, oldValue, newValue ->
        if (oldValue != newValue) {
            notifyDataSetChanged()
        }
    }

    class BenefitsViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleView = itemView.benefit_title
        private val subtitleView = itemView.benefit_subtitle
        private val indexView = itemView.benefit_index

        fun bind(benefit: VerifyIdentityBenefit, index: Int) {
            titleView.text = benefit.title
            subtitleView.text = benefit.subtitle
            indexView.text = index.toString()
        }
    }
}