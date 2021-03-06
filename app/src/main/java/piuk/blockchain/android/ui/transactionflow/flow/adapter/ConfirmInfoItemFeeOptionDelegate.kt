package piuk.blockchain.android.ui.transactionflow.flow.adapter

import android.app.Activity
import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.text.Editable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.StyleSpan
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.appcompat.widget.AppCompatSpinner
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.blockchain.ui.urllinks.URL_TX_FEES
import kotlinx.android.extensions.LayoutContainer
import kotlinx.android.synthetic.main.item_send_confirm_select_fee.view.*
import piuk.blockchain.android.R
import piuk.blockchain.android.coincore.FeeState
import piuk.blockchain.android.coincore.FeeLevel
import piuk.blockchain.android.coincore.TxConfirmationValue
import piuk.blockchain.android.ui.adapters.AdapterDelegate
import piuk.blockchain.android.ui.transactionflow.analytics.TxFlowAnalytics
import piuk.blockchain.android.ui.transactionflow.engine.TransactionIntent
import piuk.blockchain.android.ui.transactionflow.engine.TransactionModel
import piuk.blockchain.android.ui.transactionflow.flow.formatWithExchange
import piuk.blockchain.android.util.StringUtils
import piuk.blockchain.android.util.assetName
import piuk.blockchain.android.util.inflate
import piuk.blockchain.android.util.AfterTextChangedWatcher

class ConfirmInfoItemFeeOptionDelegate<in T>(
    private val model: TransactionModel,
    private val analytics: TxFlowAnalytics,
    private val activityContext: Activity,
    private val stringUtils: StringUtils
) : AdapterDelegate<T> {
    override fun isForViewType(items: List<T>, position: Int): Boolean {
        return items[position] is TxConfirmationValue.FeeSelection
    }

    override fun onCreateViewHolder(parent: ViewGroup): RecyclerView.ViewHolder =
        FeeOptionViewHolder(parent.inflate(R.layout.item_send_confirm_select_fee))

    override fun onBindViewHolder(
        items: List<T>,
        position: Int,
        holder: RecyclerView.ViewHolder
    ) = (holder as FeeOptionViewHolder).bind(
        items[position] as TxConfirmationValue.FeeSelection,
        model,
        analytics,
        activityContext,
        stringUtils
    )

    private class FeeOptionViewHolder(
        view: View
    ) : RecyclerView.ViewHolder(view), LayoutContainer {

        private val feeList = mutableListOf<FeeLevel>()
        private val displayList = mutableListOf<String>()

        private fun feeToPosition(feeLevel: FeeLevel): Int = feeList.indexOf(feeLevel)

        private fun posToFeeLevel(pos: Int): FeeLevel = feeList[pos]

        private fun updateFeeList(list: List<FeeLevel>) {
            feeList.clear()
            feeList.addAll(list)

            displayList.clear()
            with(itemView) {
                feeList.forEach {
                    displayList.add(
                        when (it) {
                            FeeLevel.None -> throw IllegalStateException("Fee level None not supported")
                            FeeLevel.Regular -> context.getString(R.string.fee_options_label,
                                context.getString(R.string.fee_options_regular),
                                context.getString(R.string.fee_options_regular_time))
                            FeeLevel.Priority -> context.getString(
                                R.string.fee_options_label, context.getString(R.string.fee_options_priority),
                                context.getString(R.string.fee_options_priority_time))
                            FeeLevel.Custom -> context.getString(R.string.fee_options_label,
                                context.getString(R.string.fee_options_custom),
                                context.getString(R.string.fee_options_custom_warning))
                        }
                    )
                }
            }
        }

        private lateinit var textChangedWatcher: AfterTextChangedWatcher

        override val containerView: View?
            get() = itemView

        fun bind(
            item: TxConfirmationValue.FeeSelection,
            model: TransactionModel,
            analytics: TxFlowAnalytics,
            activityContext: Activity,
            stringUtils: StringUtils
        ) {
            updateFeeList(item.availableLevels.toList())
            val selectedOption = item.selectedLevel

            if (!::textChangedWatcher.isInitialized) {
                textChangedWatcher = makeTextWatcher(model, item)
            }

            with(itemView) {
                fee_option_custom.removeTextChangedListener(textChangedWatcher)

                showFeeSelector(selectedOption, model, analytics, item)

                showFeeDetails(item)

                fee_option_custom.addTextChangedListener(textChangedWatcher)

                val linksMap = mapOf<String, Uri>(
                    "send_tx_fees" to Uri.parse(URL_TX_FEES)
                )

                val boldText = context.getString(R.string.tx_confirmation_fee_learn_more_1)
                val networkText = context.getString(R.string.tx_confirmation_fee_learn_more_2,
                    context.getString(item.asset.assetName()))

                val linkedText = stringUtils.getStringWithMappedAnnotations(
                    R.string.tx_confirmation_fee_learn_more_3,
                    linksMap,
                    activityContext
                )

                val sb = SpannableStringBuilder()
                    .append(boldText)
                    .append(networkText)
                    .append(linkedText)

                sb.setSpan(StyleSpan(Typeface.BOLD), 0, boldText.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

                fee_learn_more.setText(sb, TextView.BufferType.SPANNABLE)
                fee_learn_more.movementMethod = LinkMovementMethod.getInstance()
            }
        }

        private fun View.showFeeDetails(item: TxConfirmationValue.FeeSelection) {
            item.feeDetails?.let {
                when (it) {
                    is FeeState.FeeUnderMinLimit -> {
                        setCustomFeeValues(item.customFeeAmount,
                            context.getString(R.string.fee_options_sat_byte_min_error))
                    }
                    is FeeState.FeeUnderRecommended -> {
                        setCustomFeeValues(item.customFeeAmount, context.getString(R.string.fee_options_fee_too_low))
                    }
                    is FeeState.FeeOverRecommended -> {
                        setCustomFeeValues(item.customFeeAmount, context.getString(R.string.fee_options_fee_too_high))
                    }
                    is FeeState.ValidCustomFee -> {
                        setCustomFeeValues(item.customFeeAmount)
                    }
                    is FeeState.FeeTooHigh -> {
                        fee_option_value.text = context.getString(R.string.send_confirmation_insufficient_fee)
                        fee_option_value.setTextColor(ContextCompat.getColor(context, R.color.red_600))
                    }
                    is FeeState.FeeDetails -> {
                        fee_option_value.text = it.absoluteFee.formatWithExchange(item.exchange)
                        fee_option_value.setTextColor(ContextCompat.getColor(context, R.color.grey_800))
                    }
                }
            }
        }

        private fun makeTextWatcher(model: TransactionModel, item: TxConfirmationValue.FeeSelection) =
            object : AfterTextChangedWatcher() {
                override fun afterTextChanged(s: Editable) {
                    val input = s.toString()
                    if (input.isNotEmpty()) {
                        model.process(TransactionIntent.ModifyTxOption(
                            TxConfirmationValue.FeeSelection(selectedLevel = FeeLevel.Custom,
                                customFeeAmount = input.toLong(), asset = item.asset)))
                    } else {
                        itemView.fee_option_custom_il.error = ""
                    }
                }
            }

        private fun View.setCustomFeeValues(customFee: Long, error: String = "") {
            if (customFee != -1L) {
                val fee = customFee.toString()
                fee_option_custom.setText(fee, TextView.BufferType.EDITABLE)
                fee_option_custom.setSelection(fee.length)
                fee_option_custom.requestFocus()
            }

            fee_option_custom_il.error = error
        }

        private fun View.showFeeSelector(
            selectedOption: FeeLevel,
            model: TransactionModel,
            analytics: TxFlowAnalytics,
            item: TxConfirmationValue.FeeSelection
        ) {
            if (feeList.size > 1) {
                fee_option_select_spinner.setupSpinner(selectedOption, model, analytics, item)
                fee_switcher.displayedChild = SHOW_DROPDOWN
            } else {
                fee_switcher.displayedChild = SHOW_STATIC
            }
        }

        private fun AppCompatSpinner.setupSpinner(
            currentLevel: FeeLevel,
            model: TransactionModel,
            analytics: TxFlowAnalytics,
            item: TxConfirmationValue.FeeSelection
        ) {
            val spinnerArrayAdapter: ArrayAdapter<String> =
                CustomPaddingArrayAdapter(
                    context,
                    android.R.layout.simple_spinner_dropdown_item,
                    displayList
                )

            adapter = spinnerArrayAdapter
            val newSelection = feeToPosition(currentLevel)

            onItemSelectedListener = null
            setSelection(newSelection)

            when (currentLevel) {
                FeeLevel.None -> throw IllegalStateException("Fee level None not supported")
                FeeLevel.Regular,
                FeeLevel.Priority -> showStandardUi()
                FeeLevel.Custom -> showCustomFeeUi(item)
            }

            post {
                onItemSelectedListener = createSpinnerListener(model, analytics, currentLevel, item)
            }
        }

        private fun createSpinnerListener(
            model: TransactionModel,
            analytics: TxFlowAnalytics,
            currentLevel: FeeLevel,
            item: TxConfirmationValue.FeeSelection
        ) = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                val newFeeLevel = posToFeeLevel(position)

                if (newFeeLevel == FeeLevel.Custom) {
                    showCustomFeeUi(item)
                } else {
                    showStandardUi()
                }

                model.process(
                    TransactionIntent.ModifyTxOption(TxConfirmationValue.FeeSelection(selectedLevel = newFeeLevel,
                    asset = item.asset)))
                analytics.onFeeLevelChanged(currentLevel, newFeeLevel)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // do nothing
            }
        }

        private fun showCustomFeeUi(item: TxConfirmationValue.FeeSelection) {
            with(itemView) {
                fee_option_custom_bounds.text = context.getString(R.string.fee_options_sat_byte_inline_hint,
                    item.feeInfo?.regularFee.toString(),
                    item.feeInfo?.priorityFee.toString())

                fee_type_switcher.displayedChild = SHOW_CUSTOM
            }
        }

        private fun showStandardUi() {
            itemView.fee_type_switcher.displayedChild = SHOW_STANDARD
        }
    }

    companion object {
        private const val SHOW_DROPDOWN = 0
        private const val SHOW_STATIC = 1
        private const val SHOW_STANDARD = 0
        private const val SHOW_CUSTOM = 1
    }
}

private class CustomPaddingArrayAdapter<T>(context: Context, layoutId: Int, items: MutableList<T>) :
    ArrayAdapter<T>(context, layoutId, items) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = super.getView(position, convertView, parent)
        val smallPadding = context.resources.getDimension(R.dimen.tiny_margin).toInt()
        view.setPadding(0, smallPadding, view.paddingRight, smallPadding)
        return view
    }
}