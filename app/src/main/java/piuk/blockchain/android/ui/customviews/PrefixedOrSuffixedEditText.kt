package piuk.blockchain.android.ui.customviews

import android.content.Context
import android.graphics.Rect
import android.text.Editable
import android.text.Selection
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import info.blockchain.utils.tryParseBigDecimal
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import piuk.blockchain.android.util.AfterTextChangedWatcher
import java.math.BigDecimal
import kotlin.properties.Delegates

class PrefixedOrSuffixedEditText : AutofitEdittext {

    enum class ImeOptions {
        BACK,
        NEXT
    }

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyle: Int) : super(
        context,
        attrs,
        defStyle
    )

    private val imeActionsSubject: PublishSubject<ImeOptions> = PublishSubject.create()
    private val textSizeSubject: PublishSubject<Int> = PublishSubject.create()

    val onImeAction: Observable<ImeOptions>
        get() = imeActionsSubject

    val textSize: Observable<Int>
        get() = textSizeSubject

    init {
        imeOptions = EditorInfo.IME_ACTION_NEXT

        addTextChangedListener(object : AfterTextChangedWatcher() {
            override fun afterTextChanged(s: Editable?) {
                prefix?.let {
                    if (!s.toString().startsWith(it)) {
                        setText(it)
                    }
                }
                suffix?.let {
                    if (!s.toString().endsWith(it)) {
                        setText(it)
                    }
                }
                val bounds = Rect()
                paint.getTextBounds(text.toString(), 0, text?.length ?: 0, bounds)
                textSizeSubject.onNext(bounds.width())
            }
        })
        setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && majorValue.toDoubleOrNull() == 0.toDouble()) {
                setText(text.toString().replace(digitsOnlyRegex, ""))
            }
        }
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO

        setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_NEXT) {
                imeActionsSubject.onNext(ImeOptions.NEXT)
            }
            true
        }

        isEnabled = false
        maxLines = 1
    }

    override fun onKeyPreIme(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            imeActionsSubject.onNext(ImeOptions.BACK)
        }
        return false
    }

    private val digitsOnlyRegex by lazy {
        ("[\\d.]").toRegex()
    }

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        val textLength = text.toString().length
        suffix?.let {
            if (selEnd > textLength - it.length) {
                Selection.setSelection(text, 0, textLength - it.length)
            }
        }
        prefix?.let {
            if (selEnd < it.length && textLength >= it.length) {
                Selection.setSelection(text, textLength)
            }
        }
    }

    private val majorValue: String
        get() = text.toString().removePrefix(prefix ?: "").removeSuffix(suffix ?: "")

    internal val bigDecimalValue: BigDecimal?
        get() = majorValue.tryParseBigDecimal()

    private var prefix: String? = null

    private var suffix: String? = null

    internal var configuration: Configuration by Delegates.observable(
        Configuration()
    ) { _, oldValue, newValue ->
        if (newValue != oldValue) {
            if (newValue.isPrefix) {
                suffix = null
                prefix = newValue.prefixOrSuffix
                setText(prefix.plus(newValue.initialText))
                Selection.setSelection(text, text.toString().length)
                suffix = null
            } else {
                prefix = null
                suffix = newValue.prefixOrSuffix
                setText(newValue.initialText.plus(suffix))
                suffix?.let {
                    Selection.setSelection(text, text.toString().length - it.length)
                }
            }
            isEnabled = true
        }
    }

    fun resetForTyping() {
        if (isFocused && majorValue.toDoubleOrNull() == 0.toDouble()) {
            setText(text.toString().replace(digitsOnlyRegex, ""))
        }
    }
}

internal data class Configuration(
    val prefixOrSuffix: String = "",
    val isPrefix: Boolean = true,
    val initialText: String = ""
)