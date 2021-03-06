package piuk.blockchain.android.ui.addresses

import android.annotation.TargetApi
import android.content.Context
import android.graphics.Outline
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewOutlineProvider
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.Transformation
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.graphics.drawable.DrawableCompat
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat
import com.blockchain.notifications.analytics.Analytics
import com.blockchain.notifications.analytics.AnalyticsEvents
import info.blockchain.balance.CryptoCurrency
import kotlinx.android.synthetic.main.view_expanding_currency_header.view.*
import org.koin.core.KoinComponent
import org.koin.core.inject
import piuk.blockchain.android.R
import piuk.blockchain.android.util.assetName
import piuk.blockchain.android.util.coinIconWhite
import piuk.blockchain.androidcore.utils.helperfunctions.unsafeLazy
import piuk.blockchain.android.util.invisible
import piuk.blockchain.android.util.setAnimationListener
import piuk.blockchain.android.util.visible

class ExpandableCurrencyHeader @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : RelativeLayout(context, attrs), KoinComponent {

    private lateinit var selectionListener: (CryptoCurrency) -> Unit

    private val analytics: Analytics by inject()

    private var expanded = false
    private var firstOpen = true
    private var collapsedHeight: Int = 0
    private var contentHeight: Int = 0
    private var contentWidth: Int = 0
    private var selectedCurrency = CryptoCurrency.BTC
    private val arrowDrawable: Drawable? by unsafeLazy {
        VectorDrawableCompat.create(
            resources,
            R.drawable.vector_expand_more,
            ContextThemeWrapper(context, R.style.AppTheme).theme
        )?.run {
            DrawableCompat.wrap(this)
        }
    }

    init {
        // Inflate layout
        LayoutInflater.from(getContext()).inflate(R.layout.view_expanding_currency_header, this, true)
        CryptoCurrency.values().filter { it.hasFeature(CryptoCurrency.MULTI_WALLET) }
            .forEach { currency ->
                textView(currency)?.apply {
                    setOnClickListener { closeLayout(currency) }
                }
            }
        textview_selected_currency.apply {
            // Hide selector on first load
            invisible()
            setCompoundDrawablesWithIntrinsicBounds(null, null, arrowDrawable, null)
        }
    }

    override fun onFinishInflate() {
        super.onFinishInflate()

        linear_layout_coin_selection.invisible()

        textview_selected_currency.setOnClickListener { animateLayout(true) }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        content_frame.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED)
        textview_selected_currency.measure(MeasureSpec.UNSPECIFIED, heightMeasureSpec)
        collapsedHeight = textview_selected_currency.measuredHeight
        contentWidth = content_frame.measuredWidth + textview_selected_currency.measuredWidth
        contentHeight = content_frame.measuredHeight

        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        if (firstOpen) {
            content_frame.layoutParams.width = contentWidth
            content_frame.layoutParams.height = collapsedHeight
            firstOpen = false
        }

        val width = textview_selected_currency.measuredWidth + content_frame.measuredWidth
        val height = content_frame.measuredHeight

        setMeasuredDimension(width, height)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        outlineProvider = CustomOutline(w, h)
    }

    fun setSelectionListener(selectionListener: (CryptoCurrency) -> Unit) {
        this.selectionListener = selectionListener
    }

    fun setCurrentlySelectedCurrency(cryptoCurrency: CryptoCurrency) {
        selectedCurrency = cryptoCurrency
        updateCurrencyUi(selectedCurrency)
    }

    private fun textView(cryptoCurrency: CryptoCurrency): TextView? =
        when (cryptoCurrency) {
            CryptoCurrency.BTC -> textview_bitcoin
            CryptoCurrency.BCH -> textview_bitcoin_cash
            else -> null
        }

    fun isOpen() = expanded

    fun close() {
        if (isOpen()) closeLayout(null)
    }

    private fun animateLayout(expanding: Boolean) {
        if (expanding) {
            textview_selected_currency.setOnClickListener(null)
            val animation = AlphaAnimation(1.0f, 0.0f).apply { duration = 250 }
            textview_selected_currency.startAnimation(animation)
            animation.setAnimationListener {
                onAnimationEnd {
                    textview_selected_currency.alpha = 0.0f
                    startContentAnimation()
                }
            }
        } else {
            textview_selected_currency.setOnClickListener { animateLayout(true) }
            startContentAnimation()
        }
    }

    private fun startContentAnimation() {
        val animation: Animation = if (expanded) {
            linear_layout_coin_selection.invisible()
            ExpandAnimation(contentHeight, collapsedHeight)
        } else {
            this@ExpandableCurrencyHeader.invalidate()
            ExpandAnimation(collapsedHeight, contentHeight)
        }

        animation.duration = 250
        animation.setAnimationListener {
            onAnimationEnd {
                expanded = !expanded
                if (expanded) linear_layout_coin_selection.visible()
                if (expanded) analytics.logEvent(AnalyticsEvents.OpenAssetsSelector) else
                    analytics.logEvent(AnalyticsEvents.CloseAssetsSelector)
            }
        }

        content_frame.startAnimation(animation)
    }

    private fun updateCurrencyUi(asset: CryptoCurrency) {
        textview_selected_currency.run {
            val title = resources.getString(asset.assetName())
            text = title.toUpperCase()

            setCompoundDrawablesWithIntrinsicBounds(
                AppCompatResources.getDrawable(context, asset.coinIconWhite()),
                null,
                arrowDrawable,
                null
            )
            visible()
        }
    }

    /**
     * Pass null as the parameter here to close the view without triggering any [CryptoCurrency]
     * change listeners.
     */
    private fun closeLayout(cryptoCurrency: CryptoCurrency?) {
        // Update UI
        cryptoCurrency?.run { setCurrentlySelectedCurrency(this) }
        // Trigger layout change
        animateLayout(false)
        // Fade in title
        val alphaAnimation = AlphaAnimation(0.0f, 1.0f).apply { duration = 250 }
        textview_selected_currency.startAnimation(alphaAnimation)
        alphaAnimation.setAnimationListener {
            onAnimationEnd {
                textview_selected_currency.alpha = 1.0f
                // Inform parent of currency selection once animation complete to avoid glitches
                cryptoCurrency?.run { selectionListener(this) }
            }
        }
    }

    fun getSelectedCurrency() = selectedCurrency

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private inner class CustomOutline internal constructor(
        internal var width: Int,
        internal var height: Int
    ) : ViewOutlineProvider() {

        override fun getOutline(view: View, outline: Outline) {
            outline.setRect(0, 0, width, height)
        }
    }

    private inner class ExpandAnimation(private val startHeight: Int, endHeight: Int) :
        Animation() {

        private val deltaHeight: Int = endHeight - startHeight

        override fun applyTransformation(interpolatedTime: Float, t: Transformation) {
            val params = content_frame.layoutParams
            params.height = (startHeight + deltaHeight * interpolatedTime).toInt()
            content_frame.layoutParams = params
        }

        override fun willChangeBounds(): Boolean = true
    }
}