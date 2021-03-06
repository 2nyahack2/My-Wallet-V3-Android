package piuk.blockchain.android.ui.swipetoreceive

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatImageView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager
import piuk.blockchain.android.util.drawableResFilled
import info.blockchain.balance.CryptoCurrency
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import kotlinx.android.synthetic.main.fragment_swipe_to_receive.*
import org.koin.android.ext.android.inject
import piuk.blockchain.android.R
import piuk.blockchain.android.util.assetName
import piuk.blockchain.androidcore.data.events.ActionEvent
import piuk.blockchain.androidcore.data.rxjava.RxBus
import piuk.blockchain.androidcore.utils.helperfunctions.unsafeLazy
import piuk.blockchain.androidcoreui.ui.base.BaseFragment
import piuk.blockchain.androidcoreui.ui.base.UiState
import piuk.blockchain.androidcoreui.ui.customviews.toast
import piuk.blockchain.android.util.gone
import piuk.blockchain.android.util.inflate
import piuk.blockchain.android.util.invisible
import piuk.blockchain.android.util.visible
import piuk.blockchain.android.util.setOnPageChangeListener

@Suppress("MemberVisibilityCanPrivate")
class SwipeToReceiveFragment : BaseFragment<SwipeToReceiveView, SwipeToReceivePresenter>(),
    SwipeToReceiveView {

    private val presenter: SwipeToReceivePresenter by inject()
    private val rxBus: RxBus by inject()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = container?.inflate(R.layout.fragment_swipe_to_receive)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        listOf(imageview_qr, textview_address, textview_request_currency).forEach {
            it.setOnClickListener { showClipboardWarning() }
        }

        imageview_left_arrow.setOnClickListener {
            viewpager_icons.currentItem = viewpager_icons.currentItem - 1
        }
        imageview_right_arrow.setOnClickListener {
            viewpager_icons.currentItem = viewpager_icons.currentItem + 1
        }

        onViewReady()
    }

    override fun initPager(assetList: List<CryptoCurrency>) {
        val assetImageList = assetList.map { it.drawableResFilled() }
        val adapter = ImageAdapter(
            requireContext(),
            assetImageList
        )

        viewpager_icons.run {
            offscreenPageLimit = 3
            setAdapter(adapter)
            indicator.setViewPager(this)
            setOnPageChangeListener {
                onPageSelected { index ->
                    presenter.onCurrencySelected(assetList[index])

                    when (index) {
                        0 -> imageview_left_arrow.invisible()
                        adapter.count - 1 -> imageview_right_arrow.invisible()
                        else -> listOf(imageview_left_arrow, imageview_right_arrow).forEach { it.visible() }
                    }
                }
            }
        }
        assetList.firstOrNull()?.let {
            presenter.onCurrencySelected(it)
        }
    }

    override fun displayReceiveAddress(address: String) {
        textview_address.text = address
    }

    override fun displayReceiveAccount(accountName: String) {
        textview_account.text = accountName
    }

    override fun displayAsset(cryptoCurrency: CryptoCurrency) {
        val assetName = getString(cryptoCurrency.assetName())
        val requestString = getString(R.string.swipe_to_receive_request, assetName)
        textview_request_currency.text = requestString
        textview_request_currency.contentDescription = requestString
    }

    override fun setUiState(uiState: Int) {
        when (uiState) {
            UiState.LOADING -> displayLoading()
            UiState.CONTENT -> showContent()
            UiState.FAILURE -> showNoAddressesAvailable()
            UiState.EMPTY -> showNoAddressesAvailable()
        }
    }

    override fun displayQrCode(bitmap: Bitmap) {
        imageview_qr.setImageBitmap(bitmap)
    }

    override fun onStop() {
        super.onStop()
        compositeDisposable.clear()
        rxBus.unregister(ActionEvent::class.java, event)
    }

    private val compositeDisposable = CompositeDisposable()

    private val event by unsafeLazy {
        rxBus.register(ActionEvent::class.java)
    }

    override fun onStart() {
        super.onStart()
        compositeDisposable += event.subscribe {
            // Update UI with new Address + QR
            presenter.refresh()
        }
    }

    override fun createPresenter() = presenter

    override fun getMvpView() = this

    private fun showContent() {
        layout_qr.visible()
        progress_bar.gone()
        imageview_qr.visible()
        textview_error.gone()
    }

    private fun displayLoading() {
        layout_qr.visible()
        progress_bar.visible()
        imageview_qr.invisible()
        textview_error.gone()
    }

    private fun showNoAddressesAvailable() {
        layout_qr.invisible()
        textview_error.visible()
        textview_address.text = ""
    }

    private fun showClipboardWarning() {
        val address = textview_address.text
        activity?.run {
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

    companion object {
        fun newInstance(): SwipeToReceiveFragment = SwipeToReceiveFragment()
    }

    private class ImageAdapter(var context: Context, var drawables: List<Int>) : PagerAdapter() {

        override fun getCount(): Int = drawables.size

        override fun isViewFromObject(view: View, any: Any): Boolean {
            return view === any as AppCompatImageView
        }

        override fun instantiateItem(container: ViewGroup, position: Int): Any {
            val imageView = container.inflate(R.layout.item_image_pager) as ImageView
            imageView.setImageDrawable(ContextCompat.getDrawable(context, drawables[position]))
            (container as ViewPager).addView(imageView, 0)
            return imageView
        }

        override fun destroyItem(container: ViewGroup, position: Int, any: Any) {
            container.removeView(any as View)
        }
    }
}
