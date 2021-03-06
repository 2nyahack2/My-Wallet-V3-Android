package piuk.blockchain.android.cards

import android.os.Bundle
import android.text.Editable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import com.blockchain.koin.scopedInject
import piuk.blockchain.android.simplebuy.SimpleBuyAnalytics
import com.blockchain.preferences.SimpleBuyPrefs
import com.blockchain.nabu.datamanagers.CustodialWalletManager
import com.blockchain.nabu.datamanagers.PaymentMethod
import com.blockchain.nabu.datamanagers.custodialwalletimpl.CardStatus
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.rxkotlin.subscribeBy
import kotlinx.android.synthetic.main.fragment_add_new_card.*
import org.koin.android.ext.android.inject
import piuk.blockchain.android.R
import piuk.blockchain.android.ui.base.mvi.MviFragment
import piuk.blockchain.android.ui.base.setupToolbar
import piuk.blockchain.android.util.gone
import piuk.blockchain.android.util.inflate
import piuk.blockchain.android.util.visible
import piuk.blockchain.android.util.AfterTextChangedWatcher
import java.util.Calendar
import java.util.Date

class AddNewCardFragment : MviFragment<CardModel, CardIntent, CardState>(), AddCardFlowFragment {

    override val model: CardModel by scopedInject()

    private var availableCards: List<PaymentMethod.Card> = emptyList()
    private val compositeDisposable = CompositeDisposable()
    private val custodialWalletManager: CustodialWalletManager by scopedInject()
    private val simpleBuyPrefs: SimpleBuyPrefs by inject()

    override val navigator: AddCardNavigator
        get() = (activity as? AddCardNavigator)
            ?: throw IllegalStateException("Parent must implement AddCardNavigator")

    override val cardDetailsPersistence: CardDetailsPersistence
        get() = (activity as? CardDetailsPersistence)
            ?: throw IllegalStateException("Parent must implement CardDetailsPersistence")

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = container?.inflate(R.layout.fragment_add_new_card)

    private val textWatcher = object : AfterTextChangedWatcher() {
        override fun afterTextChanged(s: Editable?) {
            btn_next.isEnabled =
                card_name.isValid && card_number.isValid && cvv.isValid && expiry_date.isValid
            hideError()
        }
    }

    private fun hideError() {
        same_card_error.gone()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        activity.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)

        card_name.addTextChangedListener(textWatcher)
        card_number.addTextChangedListener(textWatcher)
        cvv.addTextChangedListener(textWatcher)
        expiry_date.addTextChangedListener(textWatcher)
        btn_next.apply {
            isEnabled = false
            setOnClickListener {
                if (cardHasAlreadyBeenAdded()) {
                    showError()
                } else {
                    cardDetailsPersistence.setCardData(CardData(
                        fullName = card_name.text.toString(),
                        number = card_number.text.toString().replace(" ", ""),
                        month = expiry_date.month.toInt(),
                        year = expiry_date.year.toInt(),
                        cvv = cvv.text.toString()
                    ))
                    activity.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

                    navigator.navigateToBillingDetails()
                    analytics.logEvent(SimpleBuyAnalytics.CARD_INFO_SET)
                }
            }
        }

        compositeDisposable += custodialWalletManager.fetchUnawareLimitsCards(listOf(CardStatus.PENDING,
            CardStatus.ACTIVE)).subscribeBy(onSuccess = {
            availableCards = it
        })
        card_number.displayCardTypeIcon(false)
        activity.setupToolbar(R.string.add_card_title)
        analytics.logEvent(SimpleBuyAnalytics.ADD_CARD)

        setupCardInfo()
    }

    private fun setupCardInfo() {
        if (simpleBuyPrefs.addCardInfoDismissed) {
            card_info_group.gone()
        } else {
            card_info_close.setOnClickListener {
                simpleBuyPrefs.addCardInfoDismissed = true
                card_info_group.gone()
            }
        }
    }

    private fun cardHasAlreadyBeenAdded(): Boolean {
        availableCards.forEach {
            if (it.expireDate.hasSameMonthAndYear(month = expiry_date.month.toInt(),
                    year = expiry_date.year.toInt().asCalendarYear()) &&
                card_number.text?.toString()?.takeLast(4) == it.endDigits &&
                card_number.cardType == it.cardType
            )
                return true
        }
        return false
    }

    private fun showError() {
        same_card_error.visible()
    }

    override fun render(newState: CardState) {}

    override fun onBackPressed(): Boolean = true

    private fun Date.hasSameMonthAndYear(year: Int, month: Int): Boolean {
        val calendar = Calendar.getInstance()
        calendar.time = this
        // calendar api returns months 0-11
        return calendar.get(Calendar.YEAR) == year && calendar.get(Calendar.MONTH) == month - 1
    }

    override fun onPause() {
        compositeDisposable.clear()
        super.onPause()
    }

    private fun Int.asCalendarYear(): Int =
        if (this < 100) 2000 + this else this
}