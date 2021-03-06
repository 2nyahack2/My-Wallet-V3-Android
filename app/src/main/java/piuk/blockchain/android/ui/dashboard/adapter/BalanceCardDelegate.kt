package piuk.blockchain.android.ui.dashboard.adapter

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import kotlinx.android.synthetic.main.item_dashboard_balance_card.view.*
import piuk.blockchain.android.R
import piuk.blockchain.android.coincore.Coincore
import piuk.blockchain.android.coincore.CryptoAsset
import piuk.blockchain.android.ui.adapters.AdapterDelegate
import piuk.blockchain.android.ui.dashboard.BalanceState
import piuk.blockchain.android.ui.dashboard.asDeltaPercent
import piuk.blockchain.android.ui.dashboard.setDeltaColour
import piuk.blockchain.android.util.colorRes
import piuk.blockchain.android.util.inflate

class BalanceCardDelegate<in T>(private val selectedFiat: String, private val coincore: Coincore) :
    AdapterDelegate<T> {

    override fun isForViewType(items: List<T>, position: Int): Boolean =
        items[position] is BalanceState

    override fun onCreateViewHolder(parent: ViewGroup): RecyclerView.ViewHolder =
        BalanceCardViewHolder(parent.inflate(R.layout.item_dashboard_balance_card), selectedFiat, coincore)

    override fun onBindViewHolder(
        items: List<T>,
        position: Int,
        holder: RecyclerView.ViewHolder
    ) = (holder as BalanceCardViewHolder).bind(items[position] as BalanceState)
}

private class BalanceCardViewHolder(
    itemView: View,
    private val selectedFiat: String,
    private val coincore: Coincore
) : RecyclerView.ViewHolder(itemView) {

    fun bind(state: BalanceState) {
        configurePieChart()

        if (state.isLoading) {
            renderLoading()
        } else {
            renderLoaded(state)
        }
    }

    private fun renderLoading() {
        itemView.total_balance.resetLoader()
        itemView.balance_delta_value.resetLoader()
        itemView.balance_delta_percent.resetLoader()

        populateEmptyPieChart()
    }

    @SuppressLint("SetTextI18n")
    private fun renderLoaded(state: BalanceState) {

        with(itemView) {
            total_balance.text = state.fiatBalance?.toStringWithSymbol() ?: ""

            if (state.delta == null) {
                balance_delta_value.text = ""
                balance_delta_percent.text = ""
            } else {
                val (deltaVal, deltaPercent) = state.delta!!

                balance_delta_value.text = deltaVal.toStringWithSymbol()
                balance_delta_value.setDeltaColour(deltaPercent)
                balance_delta_percent.asDeltaPercent(deltaPercent, "(", ")")
            }

            populatePieChart(state)
        }
    }

    private fun populateEmptyPieChart() {
        with(itemView) {
            val entries = listOf(PieEntry(100f))

            val sliceColours = listOf(ContextCompat.getColor(itemView.context, R.color.grey_100))

            pie_chart.data = PieData(
                PieDataSet(entries, null).apply {
                    sliceSpace = 5f
                    setDrawIcons(false)
                    setDrawValues(false)
                    colors = sliceColours
                })
            pie_chart.invalidate()
        }
    }

    private fun populatePieChart(state: BalanceState) {
        with(itemView) {
            val entries = ArrayList<PieEntry>().apply {
                coincore.cryptoAssets.forEach {
                    val asset = state[(it as CryptoAsset).asset]
                    val point = asset.fiatBalance?.toFloat() ?: 0f
                    add(PieEntry(point))
                }

                // Add all fiat from Funds
                add(PieEntry(state.getFundsFiat(selectedFiat).toFloat()))
            }

            if (entries.all { it.value == 0.0f }) {
                populateEmptyPieChart()
            } else {
                val sliceColours = coincore.cryptoAssets.map {
                    ContextCompat.getColor(itemView.context, (it as CryptoAsset).asset.colorRes())
                }.toMutableList()

                // Add colour for Funds
                sliceColours.add(ContextCompat.getColor(itemView.context, R.color.green_500))

                pie_chart.data = PieData(
                    PieDataSet(entries, null).apply {
                        sliceSpace = SLICE_SPACE_DP
                        setDrawIcons(false)
                        setDrawValues(false)
                        colors = sliceColours
                    })
                pie_chart.invalidate()
            }
        }
    }

    private fun configurePieChart() {
        with(itemView.pie_chart) {
            setDrawCenterText(false)

            isDrawHoleEnabled = true
            setHoleColor(Color.TRANSPARENT)
            holeRadius = PIE_HOLE_RADIUS

            setDrawEntryLabels(false)
            legend.isEnabled = false
            description.isEnabled = false

            setTouchEnabled(false)
            setNoDataText(null)
        }
    }

    companion object {
        private const val SLICE_SPACE_DP = 2f
        private const val PIE_HOLE_RADIUS = 85f
    }
}
