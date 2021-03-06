package piuk.blockchain.android.ui.activity

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.annotation.UiThread
import androidx.appcompat.content.res.AppCompatResources
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.blockchain.annotations.CommonCode
import com.blockchain.koin.scopedInject
import com.blockchain.notifications.analytics.ActivityAnalytics
import com.blockchain.preferences.CurrencyPrefs
import info.blockchain.balance.CryptoCurrency
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.rxkotlin.subscribeBy
import kotlinx.android.synthetic.main.fragment_activities.*
import org.koin.android.ext.android.get
import org.koin.android.ext.android.inject
import piuk.blockchain.android.R
import piuk.blockchain.android.coincore.ActivitySummaryItem
import piuk.blockchain.android.coincore.BlockchainAccount
import piuk.blockchain.android.coincore.CryptoAccount
import piuk.blockchain.android.coincore.FiatAccount
import piuk.blockchain.android.coincore.isCustodial
import piuk.blockchain.android.ui.activity.adapter.ActivitiesDelegateAdapter
import piuk.blockchain.android.ui.activity.detail.CryptoActivityDetailsBottomSheet
import piuk.blockchain.android.ui.activity.detail.FiatActivityDetailsBottomSheet
import piuk.blockchain.android.ui.customviews.BlockchainListDividerDecor
import piuk.blockchain.android.ui.customviews.account.AccountSelectSheet
import piuk.blockchain.android.ui.home.HomeScreenMviFragment
import piuk.blockchain.android.util.getAccount
import piuk.blockchain.android.util.putAccount
import piuk.blockchain.android.util.setCoinIcon
import piuk.blockchain.androidcore.data.events.ActionEvent
import piuk.blockchain.androidcore.data.exchangerate.ExchangeRateDataManager
import piuk.blockchain.androidcore.data.rxjava.RxBus
import piuk.blockchain.androidcore.utils.helperfunctions.unsafeLazy
import piuk.blockchain.androidcoreui.ui.customviews.ToastCustom
import piuk.blockchain.android.util.gone
import piuk.blockchain.android.util.goneIf
import piuk.blockchain.android.util.inflate
import piuk.blockchain.android.util.visible
import timber.log.Timber

class ActivitiesFragment : HomeScreenMviFragment<ActivitiesModel, ActivitiesIntent, ActivitiesState>(),
    AccountSelectSheet.SelectionHost {

    override val model: ActivitiesModel by scopedInject()

    private val theAdapter: ActivitiesDelegateAdapter by lazy {
        ActivitiesDelegateAdapter(
            prefs = get(),
            onCryptoItemClicked = { cc, tx, type ->
                onCryptoActivityClicked(cc, tx, type)
            },
            onFiatItemClicked = { cc, tx -> onFiatActivityClicked(cc, tx) }
        )
    }

    private lateinit var theLayoutManager: RecyclerView.LayoutManager

    private val displayList = mutableListOf<ActivitySummaryItem>()

    private val disposables = CompositeDisposable()
    private val rxBus: RxBus by inject()
    private val currencyPrefs: CurrencyPrefs by inject()
    private val exchangeRates: ExchangeRateDataManager by scopedInject()

    private val actionEvent by unsafeLazy {
        rxBus.register(ActionEvent::class.java)
    }

    private var state: ActivitiesState? = null

    @UiThread
    override fun render(newState: ActivitiesState) {
        if (newState.isError) {
            ToastCustom.makeText(
                requireContext(),
                getString(R.string.activity_loading_error),
                ToastCustom.LENGTH_SHORT,
                ToastCustom.TYPE_ERROR
            )
        }

        switchView(newState)

        swipe.isRefreshing = newState.isLoading

        renderAccountDetails(newState)
        renderTransactionList(newState)

        if (this.state?.bottomSheet != newState.bottomSheet) {
            when (newState.bottomSheet) {
                ActivitiesSheet.ACCOUNT_SELECTOR -> {
                    analytics.logEvent(ActivityAnalytics.WALLET_PICKER_SHOWN)
                    showBottomSheet(AccountSelectSheet.newInstance(this))
                }
                ActivitiesSheet.CRYPTO_ACTIVITY_DETAILS -> {
                    newState.selectedCryptoCurrency?.let {
                        showBottomSheet(
                            CryptoActivityDetailsBottomSheet.newInstance(it, newState.selectedTxId,
                                newState.activityType))
                    }
                }
                ActivitiesSheet.FIAT_ACTIVITY_DETAILS -> {
                    newState.selectedFiatCurrency?.let {
                        showBottomSheet(
                            FiatActivityDetailsBottomSheet.newInstance(it, newState.selectedTxId))
                    }
                }
            }
        }

        this.state = newState
    }

    private fun switchView(newState: ActivitiesState) {
        when {
            newState.isLoading && newState.activityList.isEmpty() -> {
                header_layout.gone()
                content_list.gone()
                empty_view.gone()
            }
            newState.activityList.isEmpty() -> {
                header_layout.visible()
                content_list.gone()
                empty_view.visible()
            }
            else -> {
                header_layout.visible()
                content_list.visible()
                empty_view.gone()
            }
        }
    }

    private fun renderAccountDetails(newState: ActivitiesState) {
        if (newState.account == state?.account) {
            return
        }

        if (newState.account == null) {
            // Should not happen! TODO: Crash
            return
        }

        disposables.clear()

        val account = newState.account

        account_icon.setAccountIcon(account)

        account_spend_locked.goneIf { account.isCustodial().not() }
        account_name.text = account.label
        fiat_balance.text = ""

        disposables += account.fiatBalance(currencyPrefs.selectedFiatCurrency, exchangeRates)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeBy(
                onSuccess = {
                    fiat_balance.text =
                        getString(R.string.common_spaced_strings, it.toStringWithSymbol(),
                            it.currencyCode)
                },
                onError = {
                    Timber.e("Unable to get balance for ${account.label}")
                }
            )
    }

    private fun ImageView.setAccountIcon(account: BlockchainAccount) {
        when (account) {
            is CryptoAccount -> setCoinIcon(account.asset)
            is FiatAccount -> setImageResource(account.icon())
            else -> setImageDrawable(
                AppCompatResources.getDrawable(context, R.drawable.ic_all_wallets_white))
        }
    }

    private fun renderTransactionList(newState: ActivitiesState) {
        if (state?.activityList == newState.activityList) {
            return
        }

        with(newState.activityList) {
            displayList.clear()
            if (isEmpty()) {
                Timber.d("Render new tx list - empty")
            } else {
                displayList.addAll(this)
            }
            theAdapter.notifyDataSetChanged()
        }
    }

    override fun onBackPressed(): Boolean = false

    private val preselectedAccount: BlockchainAccount?
        get() = arguments?.getAccount(PARAM_ACCOUNT)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = container?.inflate(R.layout.fragment_activities)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        preselectedAccount?.let {
            onAccountSelected(it)
        } ?: onShowAllActivity()

        setupSwipeRefresh()
        setupRecycler()
        setupAccountSelect()
    }

    private fun setupRecycler() {
        theLayoutManager = SafeLayoutManager(requireContext())

        content_list.apply {
            layoutManager = theLayoutManager
            adapter = theAdapter
            addItemDecoration(BlockchainListDividerDecor(requireContext()))
        }
        theAdapter.items = displayList
    }

    private fun setupToolbar() {
        activity.supportActionBar?.let {
            activity.setupToolbar(it, R.string.activities_title)
        }
    }

    private fun setupAccountSelect() {
        account_select_btn.setOnClickListener {
            model.process(ShowAccountSelectionIntent)
        }
    }

    private fun setupSwipeRefresh() {
        swipe.setOnRefreshListener {
            state?.account?.let {
                model.process(AccountSelectedIntent(it, true))
            }
        }

        // Configure the refreshing colors
        swipe.setColorSchemeResources(
            R.color.blue_800,
            R.color.blue_600,
            R.color.blue_400,
            R.color.blue_200
        )
    }

    override fun onResume() {
        super.onResume()
        setupToolbar()
    }

    override fun onPause() {
        disposables.clear()
        rxBus.unregister(ActionEvent::class.java, actionEvent)
        super.onPause()
    }

    private fun onCryptoActivityClicked(
        cryptoCurrency: CryptoCurrency,
        txHash: String,
        type: CryptoActivityType
    ) {
        model.process(ShowActivityDetailsIntent(cryptoCurrency, txHash, type))
    }

    private fun onFiatActivityClicked(
        currency: String,
        txHash: String
    ) {
        model.process(ShowFiatActivityDetailsIntent(currency, txHash))
    }

    private fun onShowAllActivity() {
        model.process(SelectDefaultAccountIntent)
    }

    override fun onAccountSelected(account: BlockchainAccount) {
        model.process(AccountSelectedIntent(account, false))
    }

    // SlidingModalBottomDialog.Host
    override fun onSheetClosed() {
        model.process(ClearBottomSheetIntent)
    }

    companion object {
        private const val PARAM_ACCOUNT = "PARAM_ACCOUNT"

        fun newInstance(account: BlockchainAccount?): ActivitiesFragment {
            return ActivitiesFragment().apply {
                arguments = Bundle().apply {
                    if (account != null)
                        putAccount(PARAM_ACCOUNT, account)
                }
            }
        }
    }
}

private fun FiatAccount.icon(): Int = when (fiatCurrency) {
    "EUR" -> R.drawable.ic_funds_euro
    "GBP" -> R.drawable.ic_funds_gbp
    else -> R.drawable.ic_funds_usd
}

/**
 * supportsPredictiveItemAnimations = false to avoid crashes when computing changes.
 */
@CommonCode(commonWith = "DashboardFragment - move to ui utils package")
private class SafeLayoutManager(context: Context) : LinearLayoutManager(context) {
    override fun supportsPredictiveItemAnimations() = false
}
