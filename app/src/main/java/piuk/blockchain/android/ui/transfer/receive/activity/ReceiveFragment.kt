package piuk.blockchain.android.ui.transfer.receive.activity

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.text.Editable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.blockchain.koin.scopedInject
import com.blockchain.notifications.analytics.RequestAnalyticsEvents
import com.blockchain.preferences.CurrencyPrefs
import com.google.android.material.bottomsheet.BottomSheetDialog
import info.blockchain.balance.CryptoCurrency
import info.blockchain.balance.CryptoValue
import info.blockchain.balance.FiatValue
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import kotlinx.android.synthetic.main.fragment_receive.*
import kotlinx.android.synthetic.main.include_amount_row.*
import kotlinx.android.synthetic.main.include_amount_row.view.*
import org.koin.android.ext.android.inject
import piuk.blockchain.android.R
import piuk.blockchain.android.coincore.CryptoAccount
import piuk.blockchain.android.coincore.CryptoAddress
import piuk.blockchain.android.coincore.NullCryptoAccount
import piuk.blockchain.android.ui.base.MvpFragment
import piuk.blockchain.android.ui.share.ReceiveIntentHelper
import piuk.blockchain.android.ui.share.ShareReceiveIntentAdapter
import piuk.blockchain.android.util.AppUtil
import piuk.blockchain.android.util.EditTextFormatUtil
import piuk.blockchain.androidcore.data.events.ActionEvent
import piuk.blockchain.androidcore.data.rxjava.RxBus
import piuk.blockchain.androidcore.utils.extensions.toSafeLong
import piuk.blockchain.androidcore.utils.helperfunctions.consume
import piuk.blockchain.androidcore.utils.helperfunctions.unsafeLazy
import piuk.blockchain.androidcoreui.ui.customviews.ToastCustom
import piuk.blockchain.androidcoreui.ui.customviews.toast
import piuk.blockchain.android.util.disableSoftKeyboard
import piuk.blockchain.android.util.gone
import piuk.blockchain.android.util.inflate
import piuk.blockchain.android.util.invisible
import piuk.blockchain.android.util.visible
import piuk.blockchain.android.util.AfterTextChangedWatcher
import java.text.DecimalFormatSymbols
import java.util.Locale

class ReceiveFragment : MvpFragment<ReceiveView, ReceivePresenter>(),
    ReceiveView {

    interface ReceiveFragmentHost {
        fun actionBackPress()
    }

    private val host: ReceiveFragmentHost by lazy {
        activity as? ReceiveFragmentHost ?: throw IllegalStateException(
            "Host fragment is not a ReceiveFragment.ReceiveFragmentHost")
    }

    override val presenter: ReceivePresenter by scopedInject()
    override val view: ReceiveView = this

    private val appUtil: AppUtil by inject()
    private val rxBus: RxBus by inject()
    private val prefs: CurrencyPrefs by scopedInject()

    private val defaultDecimalSeparator = DecimalFormatSymbols.getInstance().decimalSeparator.toString()

    private var receiveAccount: CryptoAccount = NullCryptoAccount()

    private val disposables = CompositeDisposable()

    private val receiveIntentHelper by unsafeLazy {
        ReceiveIntentHelper(requireContext())
    }

    private val event by unsafeLazy {
        rxBus.register(ActionEvent::class.java)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = container?.inflate(R.layout.fragment_receive)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        onViewReady()
        setupLayout()
        setCustomKeypad()

        scrollview?.post { scrollview?.scrollTo(0, 0) }

        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (custom_keyboard.isVisible) {
                    closeKeypad()
                } else {
                    host.actionBackPress()
                }
            }
        })
    }

    private fun setupLayout() {

        amountCrypto.apply {
            hint = "0${defaultDecimalSeparator}00"
            disableSoftKeyboard()
        }

        amountFiat.apply {
            hint = "0${defaultDecimalSeparator}00"
            disableSoftKeyboard()
        }

        enableAmountUpdates(true)

        image_qr.apply {
            setOnClickListener {
                showClipboardWarning()
                analytics.logEvent(RequestAnalyticsEvents.QrAddressClicked)
            }
            setOnLongClickListener { consume { onShareClicked() } }
        }

        textview_receiving_address.setOnClickListener { showClipboardWarning() }

        cta_button.setOnClickListener {
            onShareClicked()
            analytics.logEvent(RequestAnalyticsEvents.RequestPaymentClicked)
        }

        currencyCrypto.text = receiveAccount.asset.displayTicker
        currencyFiat.text = prefs.selectedFiatCurrency

        configureForAsset()
    }

    private fun enableAmountUpdates(enabled: Boolean) {
        if (enabled) {
            amountCrypto.addTextChangedListener(cryptoTextWatcher)
            amountFiat.addTextChangedListener(fiatTextWatcher)
        } else {
            amountCrypto.removeTextChangedListener(cryptoTextWatcher)
            amountFiat.removeTextChangedListener(fiatTextWatcher)
        }
    }

    private val cryptoTextWatcher = object : AfterTextChangedWatcher() {
        override fun afterTextChanged(s: Editable?) {
            enableAmountUpdates(false)
            val txt = EditTextFormatUtil.formatEditable(
                s,
                receiveAccount.asset.dp,
                amountCrypto,
                defaultDecimalSeparator
            ).toString()

            val amount = txt.toSafeLong(Locale.getDefault()).toBigInteger()
            val value = CryptoValue.fromMinor(receiveAccount.asset, amount)

            presenter.onAmountChanged(value)
            enableAmountUpdates(true)
        }
    }

    private val fiatTextWatcher = object : AfterTextChangedWatcher() {
        override fun afterTextChanged(s: Editable) {
            enableAmountUpdates(false)
            val maxLength = 2
            val txt = EditTextFormatUtil.formatEditable(
                s,
                maxLength,
                amountFiat,
                defaultDecimalSeparator
            ).toString()

            val value = FiatValue.fromMajorOrZero(prefs.selectedFiatCurrency, txt)
            presenter.onAmountChanged(value)

            enableAmountUpdates(true)
        }
    }

    override fun updateReceiveAddress(address: CryptoAddress) {
        if (!isRemoving) {
            textview_receiving_address.text = address.address
            textview_receiving_label.text = address.label
        }
    }

    override fun updateAmountField(value: FiatValue) {
        amountFiat.setText(value.toStringWithoutSymbol())
    }

    override fun updateAmountField(amount: CryptoValue) {
        amountCrypto.setText(amount.toStringWithoutSymbol())
    }

    override fun onResume() {
        super.onResume()
        presenter.onResume(receiveAccount)
        closeKeypad()

        disposables += event.subscribe {
            presenter.onResume(receiveAccount)
        }
    }

    override fun showQrLoading() {
        if (!isRemoving) {
            image_qr.invisible()
            textview_receiving_address.invisible()
            progressbar.visible()
        }
    }

    override fun showQrCode(bitmap: Bitmap?) {
        if (!isRemoving) {
            progressbar.invisible()
            image_qr.visible()
            textview_receiving_address.visible()
            image_qr.setImageBitmap(bitmap)
        }
    }

    private fun displayBitcoinLayout() {
        divider1.visible()
        amount_container.visible()
    }

    private fun displayNonBtcLayout() {
        custom_keyboard.hideKeyboard()
        divider1.gone()
        amount_container.gone()
    }

    private fun configureForAsset() {
        if (receiveAccount.asset == CryptoCurrency.BTC) {
            displayBitcoinLayout()
        } else {
            displayNonBtcLayout()
        }
    }

    override fun showShareSheet(asset: CryptoCurrency, uri: String) {
        receiveIntentHelper.getIntentDataList(uri, getQrBitmap(), asset)
            .let { shareList ->
                BottomSheetDialog(
                    requireContext(),
                    R.style.BottomSheetDialog
                ).also { dlg ->
                    val sheetView = View.inflate(activity, R.layout.bottom_sheet_receive, null)
                    val recycler = sheetView.findViewById<RecyclerView>(R.id.recycler_view)
                    recycler.layoutManager = LinearLayoutManager(context)
                    recycler.adapter = ShareReceiveIntentAdapter(shareList).apply {
                        itemClickedListener = { dlg.dismiss() }
                    }
                    dlg.setContentView(sheetView)
                    dlg.show()
            }
        }
    }

    private fun onShareClicked() {
        activity.run {
            AlertDialog.Builder(this, R.style.AlertDialogStyle)
                .setTitle(R.string.app_name)
                .setMessage(R.string.receive_address_to_share)
                .setCancelable(false)
                .setPositiveButton(R.string.common_yes) { _, _ -> presenter.onShowBottomShareSheetSelected() }
                .setNegativeButton(R.string.common_no, null)
                .show()
        }
    }

    private fun getQrBitmap(): Bitmap = (image_qr.drawable as BitmapDrawable).bitmap

    override fun showToast(@StringRes message: Int, @ToastCustom.ToastType toastType: String) =
        toast(message, toastType)

    private fun showClipboardWarning() {
        val address = textview_receiving_address.text
        activity.run {
            AlertDialog.Builder(this, R.style.AlertDialogStyle)
                .setTitle(R.string.app_name)
                .setMessage(R.string.receive_address_to_clipboard)
                .setCancelable(false)
                .setPositiveButton(R.string.common_yes) { _, _ ->
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("Send address", address)
                    toast(R.string.copied_to_clipboard)
                    clipboard.setPrimaryClip(clip)
                }
                .setNegativeButton(R.string.common_no, null)
                .show()
        }
    }

    fun onBackPressed(): Boolean =
        if (custom_keyboard.isVisible) {
            closeKeypad()
            true
        } else {
            false
        }

    override fun onPause() {
        super.onPause()
        rxBus.unregister(ActionEvent::class.java, event)
        disposables.clear()
    }

    override fun finishPage() =
        activity.finish()

    private fun setCustomKeypad() {
        custom_keyboard.apply {
            setDecimalSeparator(defaultDecimalSeparator)
            // Enable custom keypad and disables default keyboard from popping up
            enableOnView(amount_container.amountCrypto)
            enableOnView(amount_container.amountFiat)
        }

        amount_container.amountCrypto.apply {
            setText("")
            requestFocus()
        }
    }

    private fun closeKeypad() {
        custom_keyboard.setNumpadVisibility(View.GONE)
    }

    companion object {
        fun newInstance(targetAccount: CryptoAccount) =
            ReceiveFragment().apply {
                receiveAccount = targetAccount
        }
    }
}
