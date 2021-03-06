package piuk.blockchain.android.ui.kyc.countryselection.adapter

import androidx.recyclerview.widget.RecyclerView
import android.view.View
import android.view.ViewGroup
import piuk.blockchain.android.ui.kyc.countryselection.util.CountryDisplayModel
import kotlinx.android.synthetic.main.item_country.view.*
import piuk.blockchain.android.R
import piuk.blockchain.android.util.autoNotify
import piuk.blockchain.android.util.inflate
import kotlin.properties.Delegates

class CountryCodeAdapter(
    private val countrySelector: (CountryDisplayModel) -> Unit
) : RecyclerView.Adapter<CountryCodeAdapter.CountryCodeViewHolder>() {

    var items: List<CountryDisplayModel> by Delegates.observable(
        emptyList()
    ) { _, oldList, newList ->
        autoNotify(oldList, newList) { o, n -> o == n }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CountryCodeViewHolder =
        CountryCodeViewHolder(parent.inflate(R.layout.item_country))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: CountryCodeViewHolder, position: Int) {
        holder.bind(items[position], countrySelector)
    }

    class CountryCodeViewHolder(
        itemView: View
    ) : RecyclerView.ViewHolder(itemView) {

        private val name = itemView.text_view_country_name
        private val shortName = itemView.text_view_country_short_name

        fun bind(
            country: CountryDisplayModel,
            countrySelector: (CountryDisplayModel) -> Unit
        ) {
            name.text = country.name
            shortName.text = country.shortName
            itemView.setOnClickListener { countrySelector(country) }
        }
    }
}