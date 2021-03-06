package piuk.blockchain.android.ui.dashboard.assetdetails

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.annotation.UiThread
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.blockchain.koin.scopedInject
import piuk.blockchain.android.simplebuy.CustodialBalanceClicked
import com.blockchain.preferences.CurrencyPrefs
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.android.material.tabs.TabLayout
import info.blockchain.balance.CryptoCurrency
import info.blockchain.balance.FiatValue
import info.blockchain.wallet.prices.data.PriceDatum
import kotlinx.android.synthetic.main.dialog_sheet_dashboard_asset_details.view.*
import org.koin.android.ext.android.inject
import piuk.blockchain.android.R
import piuk.blockchain.android.coincore.AssetFilter
import piuk.blockchain.android.coincore.BlockchainAccount
import piuk.blockchain.android.coincore.Coincore
import piuk.blockchain.android.coincore.CryptoAccount
import piuk.blockchain.android.coincore.CryptoAsset
import piuk.blockchain.android.ui.base.mvi.MviBottomSheet
import piuk.blockchain.android.ui.customviews.BlockchainListDividerDecor
import piuk.blockchain.android.ui.customviews.account.PendingBalanceAccountDecorator
import piuk.blockchain.android.ui.dashboard.setDeltaColour
import piuk.blockchain.android.util.getDecimalPlaces
import piuk.blockchain.android.util.loadInterMedium
import piuk.blockchain.androidcore.data.exchangerate.PriceSeries
import piuk.blockchain.androidcore.data.exchangerate.TimeSpan
import piuk.blockchain.androidcoreui.ui.customviews.ToastCustom
import piuk.blockchain.androidcoreui.ui.customviews.ToastCustom.TYPE_ERROR
import piuk.blockchain.android.util.gone
import piuk.blockchain.android.util.invisible
import piuk.blockchain.android.util.visible
import piuk.blockchain.android.util.setOnTabSelectedListener
import java.math.RoundingMode
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Currency
import java.util.Date
import java.util.Locale

class AssetDetailSheet :
    MviBottomSheet<AssetDetailsModel, AssetDetailsIntent, AssetDetailsState>() {
    private val currencyPrefs: CurrencyPrefs by inject()
    private val locale = Locale.getDefault()

    private val cryptoCurrency: CryptoCurrency by lazy {
        arguments?.getSerializable(ARG_CRYPTO_CURRENCY) as? CryptoCurrency
            ?: throw IllegalArgumentException("No cryptoCurrency specified")
    }

    private val assetSelect: Coincore by scopedInject()

    private val token: CryptoAsset by lazy {
        assetSelect[cryptoCurrency]
    }

    private val detailsAdapter by lazy {
        AssetDetailAdapter(
            ::onAccountSelected,
            cryptoCurrency.hasFeature(CryptoCurrency.CUSTODIAL_ONLY),
            token
        ) {
            PendingBalanceAccountDecorator(it.account)
        }
    }

    private var state = AssetDetailsState()

    override val model: AssetDetailsModel by scopedInject()

    override val layoutResource: Int
        get() = R.layout.dialog_sheet_dashboard_asset_details

    @UiThread
    override fun render(newState: AssetDetailsState) {
        if (newState.errorState != AssetDetailsError.NONE) {
            handleErrorState(newState.errorState)
        }

        newState.assetDisplayMap?.let { assetDisplayMap ->
            onGotAssetDetails(assetDisplayMap)
        }

        dialogView.current_price.text = newState.assetFiatPrice

        configureTimespanSelectionUI(dialogView, newState.timeSpan)

        if (newState.chartLoading) {
            chartToLoadingState()
        } else {
            chartToDataState()
        }

        dialogView.chart.apply {
            if (newState.chartData != state.chartData) {
                updateChart(this, newState.chartData)
            }
        }

        updatePriceChange(dialogView.price_change, newState.chartData)

        state = newState
    }

    override fun initControls(view: View) {
        with(view) {
            configureChart(chart,
                getFiatSymbol(currencyPrefs.selectedFiatCurrency),
                cryptoCurrency.getDecimalPlaces())

            configureTabs(view.chart_price_periods)

            current_price_title.text =
                getString(R.string.dashboard_price_for_asset, cryptoCurrency.displayTicker)

            asset_list.apply {
                adapter = detailsAdapter

                layoutManager = LinearLayoutManager(requireContext())
                addItemDecoration(BlockchainListDividerDecor(requireContext()))
            }

            model.process(LoadAssetDisplayDetails)
            model.process(LoadAssetFiatValue)
            model.process(LoadHistoricPrices)
        }
    }

    override fun dismiss() {
        super.dismiss()
        model.process(ClearSheetDataIntent)
    }

    private fun onGotAssetDetails(assetDetails: AssetDisplayMap) {

        val itemList = mutableListOf<AssetDetailItem>()

        assetDetails[AssetFilter.NonCustodial]?.let {
            itemList.add(
                AssetDetailItem(
                    assetFilter = AssetFilter.NonCustodial,
                    account = it.account,
                    balance = it.amount,
                    fiatBalance = it.fiatValue,
                    actions = it.actions,
                    interestRate = it.interestRate
                )
            )
        }

        assetDetails[AssetFilter.Custodial]?.let {
            itemList.add(
                AssetDetailItem(
                    assetFilter = AssetFilter.Custodial,
                    account = it.account,
                    balance = it.amount,
                    fiatBalance = it.fiatValue,
                    actions = it.actions,
                    interestRate = it.interestRate
                )
            )
        }

        assetDetails[AssetFilter.Interest]?.let {
            itemList.add(
                AssetDetailItem(
                    assetFilter = AssetFilter.Interest,
                    account = it.account,
                    balance = it.amount,
                    fiatBalance = it.fiatValue,
                    actions = it.actions,
                    interestRate = it.interestRate
                )
            )
        }

        detailsAdapter.itemList = itemList
    }

    private fun onAccountSelected(account: BlockchainAccount, assetFilter: AssetFilter) {
        if (account is CryptoAccount && assetFilter == AssetFilter.Custodial) {
            analytics.logEvent(CustodialBalanceClicked(account.asset))
        }

        state.assetDisplayMap?.get(assetFilter)?.let {
            model.process(
                ShowAssetActionsIntent(account)
            )
        }
    }

    private fun updateChart(chart: LineChart, data: List<PriceDatum>) {
        chart.apply {
            visible()
            clear()
            if (data.isEmpty()) {
                dialogView.price_change?.text = "--"
                return
            }
            val entries = data
                .filter { it.price != null }
                .map {
                    Entry(
                        it.timestamp.toFloat(),
                        it.price!!.toFloat())
                }

            this.data = LineData(LineDataSet(entries, null).apply {
                color = ContextCompat.getColor(context, getDataRepresentationColor(data))
                lineWidth = 2f
                mode = LineDataSet.Mode.LINEAR
                setDrawValues(false)
                setDrawCircles(false)
                isHighlightEnabled = true
                setDrawHighlightIndicators(false)
                marker = ValueMarker(
                    context,
                    R.layout.price_chart_marker,
                    getFiatSymbol(currencyPrefs.selectedFiatCurrency),
                    cryptoCurrency.getDecimalPlaces()
                )
            })
            animateX(500)
        }
    }

    private fun handleErrorState(error: AssetDetailsError) {
        val errorString = when (error) {
            AssetDetailsError.NO_CHART_DATA ->
                getString(R.string.asset_details_chart_load_failed_toast)
            AssetDetailsError.NO_ASSET_DETAILS ->
                getString(R.string.asset_details_load_failed_toast)
            AssetDetailsError.NO_EXCHANGE_RATE ->
                getString(R.string.asset_details_exchange_load_failed_toast)
            else -> "" // this never triggers
        }
        ToastCustom.makeText(requireContext(), errorString, Toast.LENGTH_SHORT, TYPE_ERROR)
    }

    private fun chartToLoadingState() {
        with(dialogView) {
            prices_loading?.visible()
            chart?.invisible()
            price_change?.apply {
                text = "--"
                setTextColor(ContextCompat.getColor(context, R.color.dashboard_chart_unknown))
            }
        }
    }

    private fun chartToDataState() {
        dialogView.prices_loading?.gone()
        dialogView.chart?.visible()
    }

    private fun configureTabs(chartPricePeriods: TabLayout) {
        TimeSpan.values().forEachIndexed { index, timeSpan ->
            chartPricePeriods.getTabAt(index)?.text = timeSpan.tabName()
        }
        chartPricePeriods.setOnTabSelectedListener {
            model.process(UpdateTimeSpan(TimeSpan.values()[it]))
        }
    }

    private fun TimeSpan.tabName() =
        when (this) {
            TimeSpan.ALL_TIME -> "ALL"
            TimeSpan.YEAR -> "1Y"
            TimeSpan.MONTH -> "1M"
            TimeSpan.WEEK -> "1W"
            TimeSpan.DAY -> "1D"
        }

    private fun getDataRepresentationColor(data: PriceSeries): Int {
        // We have filtered out nulls by here, so we can 'safely' default to zeros for the price
        val firstPrice: Double = data.first().price ?: 0.0
        val lastPrice: Double = data.last().price ?: 0.0

        val diff = lastPrice - firstPrice
        return if (diff < 0) R.color.dashboard_chart_negative else R.color.dashboard_chart_positive
    }

    @SuppressLint("SetTextI18n")
    private fun updatePriceChange(percentageView: AppCompatTextView, data: PriceSeries) {
        // We have filtered out nulls by here, so we can 'safely' default to zeros for the price
        val firstPrice: Double = data.firstOrNull()?.price ?: 0.0
        val lastPrice: Double = data.lastOrNull()?.price ?: 0.0
        val difference = lastPrice - firstPrice

        val percentChange = (difference / firstPrice) * 100
        val percentChangeTxt = if (percentChange.isNaN()) {
            "--"
        } else {
            String.format("%.1f", percentChange)
        }

        percentageView.text =
            FiatValue.fromMajor(
                currencyPrefs.selectedFiatCurrency,
                difference.toBigDecimal()
            ).toStringWithSymbol() + " ($percentChangeTxt%)"

        percentageView.setDeltaColour(
            delta = difference,
            negativeColor = R.color.dashboard_chart_negative,
            positiveColor = R.color.dashboard_chart_positive
        )
    }

    private fun configureChart(chart: LineChart, fiatSymbol: String, decimalPlaces: Int) {
        chart.apply {
            setDrawGridBackground(false)
            setDrawBorders(false)
            setScaleEnabled(false)
            setPinchZoom(false)
            isDoubleTapToZoomEnabled = false
            description.isEnabled = false
            legend.isEnabled = false
            axisLeft.setDrawGridLines(false)

            axisLeft.valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return fiatSymbol + NumberFormat.getNumberInstance(Locale.getDefault())
                        .apply {
                            maximumFractionDigits = decimalPlaces
                            minimumFractionDigits = decimalPlaces
                            roundingMode = RoundingMode.HALF_UP
                        }.format(value)
                }
            }

            axisLeft.granularity = 0.005f
            axisLeft.isGranularityEnabled = true
            axisLeft.textColor = ContextCompat.getColor(context, R.color.primary_grey_medium)
            axisRight.isEnabled = false
            xAxis.setDrawGridLines(false)
            xAxis.textColor = ContextCompat.getColor(context, R.color.primary_grey_medium)
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.isGranularityEnabled = true
            setExtraOffsets(8f, 0f, 0f, 10f)
            setNoDataTextColor(ContextCompat.getColor(context, R.color.primary_grey_medium))
            val typeFace = context.loadInterMedium()
            xAxis.typeface = typeFace
            axisLeft.typeface = typeFace
        }
    }

    private fun configureTimespanSelectionUI(view: View, selection: TimeSpan) {
        val dateFormat = when (selection) {
            TimeSpan.ALL_TIME -> SimpleDateFormat("yyyy", locale)
            TimeSpan.YEAR -> SimpleDateFormat("MMM ''yy", locale)
            TimeSpan.MONTH, TimeSpan.WEEK -> SimpleDateFormat("dd. MMM", locale)
            TimeSpan.DAY -> SimpleDateFormat("H:00", locale)
        }

        val granularity = when (selection) {
            TimeSpan.ALL_TIME -> 60 * 60 * 24 * 365F
            TimeSpan.YEAR -> 60 * 60 * 24 * 30F
            TimeSpan.MONTH, TimeSpan.WEEK -> 60 * 60 * 24 * 2F
            TimeSpan.DAY -> 60 * 60 * 4F
        }

        with(view) {
            chart.chart.xAxis.apply {
                this.granularity = granularity
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        return dateFormat.format(Date(value.toLong() * 1000))
                    }
                }
            }

            price_change_period.text = resources.getString(
                when (selection) {
                    TimeSpan.YEAR -> R.string.dashboard_time_span_last_year
                    TimeSpan.MONTH -> R.string.dashboard_time_span_last_month
                    TimeSpan.WEEK -> R.string.dashboard_time_span_last_week
                    TimeSpan.DAY -> R.string.dashboard_time_span_last_day
                    TimeSpan.ALL_TIME -> R.string.dashboard_time_span_all_time
                }
            )

            chart_price_periods.getTabAt(selection.ordinal)?.select()
        }
    }

    companion object {
        private const val ARG_CRYPTO_CURRENCY = "crypto"

        fun newInstance(cryptoCurrency: CryptoCurrency): AssetDetailSheet {
            return AssetDetailSheet().apply {
                arguments = Bundle().apply {
                    putSerializable(ARG_CRYPTO_CURRENCY, cryptoCurrency)
                }
                model.process(LoadAsset(token))
            }
        }

        private fun getFiatSymbol(currencyCode: String, locale: Locale = Locale.getDefault()) =
            Currency.getInstance(currencyCode).getSymbol(locale)
    }
}
