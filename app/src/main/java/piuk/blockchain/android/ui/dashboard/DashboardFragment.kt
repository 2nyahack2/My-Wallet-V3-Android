package piuk.blockchain.android.ui.dashboard

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.UiThread
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.blockchain.extensions.exhaustive
import com.blockchain.koin.scopedInject
import com.blockchain.notifications.analytics.AnalyticsEvents
import piuk.blockchain.android.simplebuy.SimpleBuyAnalytics
import com.blockchain.preferences.CurrencyPrefs
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import info.blockchain.balance.CryptoCurrency
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import kotlinx.android.synthetic.main.fragment_dashboard.*
import org.koin.android.ext.android.get
import org.koin.android.ext.android.inject
import piuk.blockchain.android.R
import piuk.blockchain.android.campaign.CampaignType
import piuk.blockchain.android.campaign.blockstackCampaignName
import piuk.blockchain.android.coincore.AssetAction
import piuk.blockchain.android.coincore.BlockchainAccount
import piuk.blockchain.android.coincore.Coincore
import piuk.blockchain.android.coincore.CryptoAccount
import piuk.blockchain.android.coincore.FiatAccount
import piuk.blockchain.android.coincore.SingleAccount
import piuk.blockchain.android.coincore.impl.CryptoNonCustodialAccount
import piuk.blockchain.android.coincore.impl.CustodialTradingAccount
import piuk.blockchain.android.simplebuy.SimpleBuyCancelOrderBottomSheet
import piuk.blockchain.android.ui.airdrops.AirdropStatusSheet
import piuk.blockchain.android.ui.customviews.BlockchainListDividerDecor
import piuk.blockchain.android.ui.customviews.KycBenefitsBottomSheet
import piuk.blockchain.android.ui.customviews.VerifyIdentityBenefit
import piuk.blockchain.android.ui.dashboard.adapter.DashboardDelegateAdapter
import piuk.blockchain.android.ui.dashboard.announcements.AnnouncementCard
import piuk.blockchain.android.ui.dashboard.announcements.AnnouncementHost
import piuk.blockchain.android.ui.dashboard.announcements.AnnouncementList
import piuk.blockchain.android.ui.dashboard.assetdetails.AssetDetailsFlow
import piuk.blockchain.android.ui.dashboard.sheets.FiatFundsDetailSheet
import piuk.blockchain.android.ui.dashboard.sheets.ForceBackupForSendSheet
import piuk.blockchain.android.ui.dashboard.sheets.LinkBankAccountDetailsBottomSheet
import piuk.blockchain.android.ui.home.HomeScreenMviFragment
import piuk.blockchain.android.ui.home.MainActivity
import piuk.blockchain.android.ui.interest.InterestSummarySheet
import piuk.blockchain.android.ui.sell.BuySellFragment
import piuk.blockchain.android.ui.transactionflow.DialogFlow
import piuk.blockchain.android.ui.transactionflow.TransactionFlow
import piuk.blockchain.android.ui.transfer.receive.activity.ReceiveActivity
import piuk.blockchain.android.util.launchUrlInBrowser
import piuk.blockchain.androidcore.data.events.ActionEvent
import piuk.blockchain.androidcore.data.rxjava.RxBus
import piuk.blockchain.androidcore.utils.helperfunctions.unsafeLazy
import piuk.blockchain.android.util.inflate
import timber.log.Timber

class EmptyDashboardItem : DashboardItem

private typealias RefreshFn = () -> Unit

class DashboardFragment : HomeScreenMviFragment<DashboardModel, DashboardIntent, DashboardState>(),
    ForceBackupForSendSheet.Host,
    FiatFundsDetailSheet.Host,
    KycBenefitsBottomSheet.Host,
    DialogFlow.FlowHost,
    AssetDetailsFlow.AssetDetailsHost,
    InterestSummarySheet.Host {

    override val model: DashboardModel by scopedInject()
    private val announcements: AnnouncementList by scopedInject()
    private val analyticsReporter: BalanceAnalyticsReporter by scopedInject()
    private val currencyPrefs: CurrencyPrefs by inject()
    private val coincore: Coincore by scopedInject()

    private val theAdapter: DashboardDelegateAdapter by lazy {
        DashboardDelegateAdapter(
            prefs = get(),
            onCardClicked = { onAssetClicked(it) },
            analytics = get(),
            onFundsItemClicked = { onFundsClicked(it) },
            coincore = coincore
        )
    }

    private lateinit var theLayoutManager: RecyclerView.LayoutManager

    private val displayList = mutableListOf<DashboardItem>()

    private val compositeDisposable = CompositeDisposable()
    private val rxBus: RxBus by inject()

    private val actionEvent by unsafeLazy {
        rxBus.register(ActionEvent::class.java)
    }

    private var state: DashboardState? =
        null // Hold the 'current' display state, to enable optimising of state updates

    @UiThread
    override fun render(newState: DashboardState) {
        try {
            doRender(newState)
        } catch (e: Throwable) {
            Timber.e("Error rendering: $e")
        }
    }

    @UiThread
    private fun doRender(newState: DashboardState) {

        swipe.isRefreshing = false

        if (newState.assets.isNotEmpty()) {
            if (displayList.isEmpty()) {
                createDisplayList(newState)
            } else {
                updateDisplayList(newState)
            }
        } else {
            // TODO clear display list
        }

        if (this.state?.showDashboardSheet != newState.showDashboardSheet) {
            showPromoSheet(newState)
        }

        // Update/show dialog flow
        if (state?.activeFlow != newState.activeFlow) {
            state?.activeFlow?.let {
                clearBottomSheet()
            }

            newState.activeFlow?.startFlow(childFragmentManager, this)
        }

        // Update/show announcement
        if (this.state?.announcement != newState.announcement) {
            showAnnouncement(newState.announcement)
        }

        updateAnalytics(this.state, newState)

        this.state = newState
    }

    private fun createDisplayList(newState: DashboardState) {
        with(displayList) {
            add(IDX_CARD_ANNOUNCE, EmptyDashboardItem()) // Placeholder for announcements
            add(IDX_CARD_BALANCE, newState)
            add(IDX_FUNDS_BALANCE, EmptyDashboardItem()) // Placeholder for funds
            addAll(newState.assets.values)
        }
        theAdapter.notifyDataSetChanged()
    }

    private fun updateDisplayList(newState: DashboardState) {
        with(displayList) {

            val modList = mutableListOf<RefreshFn?>()
            newState.assets.values.forEachIndexed { index, v ->
                modList.add(handleUpdatedAssetState(IDX_ASSET_CARDS_START + index, v))
            }

            modList.removeAll { it == null }

            if (newState.fiatAssets?.fiatAccounts?.isNotEmpty() == true) {
                set(IDX_FUNDS_BALANCE, newState.fiatAssets)
                modList.add { theAdapter.notifyItemChanged(IDX_FUNDS_BALANCE) }
            }

            if (modList.isNotEmpty()) {
                set(IDX_CARD_BALANCE, newState)
                modList.add { theAdapter.notifyItemChanged(IDX_CARD_BALANCE) }
            }

            modList.forEach { it?.invoke() }
        }
    }

    private fun handleUpdatedAssetState(index: Int, newState: CryptoAssetState): RefreshFn? {
        if (displayList[index] != newState) {
            displayList[index] = newState
            return { theAdapter.notifyItemChanged(index) }
        } else {
            return null
        }
    }

    private fun showPromoSheet(state: DashboardState) {
        showBottomSheet(
            when (state.showDashboardSheet) {
                DashboardSheet.STX_AIRDROP_COMPLETE -> AirdropStatusSheet.newInstance(
                    blockstackCampaignName)
                DashboardSheet.BACKUP_BEFORE_SEND -> ForceBackupForSendSheet.newInstance(state.backupSheetDetails!!)
                DashboardSheet.SIMPLE_BUY_CANCEL_ORDER -> {
                    analytics.logEvent(SimpleBuyAnalytics.BANK_DETAILS_CANCEL_PROMPT)
                    SimpleBuyCancelOrderBottomSheet.newInstance(true)
                }
                DashboardSheet.FIAT_FUNDS_DETAILS -> FiatFundsDetailSheet.newInstance(
                    state.selectedFiatAccount
                        ?: return
                )
                DashboardSheet.LINK_OR_DEPOSIT -> {
                    state.selectedFiatAccount?.let {
                        LinkBankAccountDetailsBottomSheet.newInstance(it)
                    } ?: LinkBankAccountDetailsBottomSheet.newInstance()
                }
                DashboardSheet.FIAT_FUNDS_NO_KYC -> showFiatFundsKyc()
                DashboardSheet.INTEREST_SUMMARY -> InterestSummarySheet.newInstance(
                    state.selectedCryptoAccount!!,
                    state.selectedAsset!!
                )
                null -> null
            }
        )
    }

    private fun showFiatFundsKyc(): BottomSheetDialogFragment {
        val currencyIcon = when (currencyPrefs.selectedFiatCurrency) {
            "EUR" -> R.drawable.ic_funds_euro
            "GBP" -> R.drawable.ic_funds_gbp
            else -> R.drawable.ic_funds_usd // show dollar if currency isn't selected
        }

        return KycBenefitsBottomSheet.newInstance(
            KycBenefitsBottomSheet.BenefitsDetails(
                title = getString(R.string.fiat_funds_no_kyc_announcement_title),
                description = getString(R.string.fiat_funds_no_kyc_announcement_description),
                listOfBenefits = listOf(
                    VerifyIdentityBenefit(
                        getString(R.string.fiat_funds_no_kyc_step_1_title),
                        getString(R.string.fiat_funds_no_kyc_step_1_description)),
                    VerifyIdentityBenefit(
                        getString(R.string.fiat_funds_no_kyc_step_2_title),
                        getString(R.string.fiat_funds_no_kyc_step_2_description)),
                    VerifyIdentityBenefit(
                        getString(R.string.fiat_funds_no_kyc_step_3_title),
                        getString(R.string.fiat_funds_no_kyc_step_3_description))
                ),
                icon = currencyIcon
            )
        )
    }

    private fun showAnnouncement(card: AnnouncementCard?) {
        displayList[IDX_CARD_ANNOUNCE] = card ?: EmptyDashboardItem()
        theAdapter.notifyItemChanged(IDX_CARD_ANNOUNCE)
    }

    private fun updateAnalytics(oldState: DashboardState?, newState: DashboardState) {
        analyticsReporter.updateFiatTotal(newState.fiatBalance)

        newState.assets.forEach { (cc, s) ->

            val newBalance = s.balance
            if (newBalance != null && newBalance != oldState?.assets?.get(cc)?.balance) {
                // If we have the full set, this will fire
                analyticsReporter.gotAssetBalance(cc, newBalance)
            }
        }
    }

    override fun onBackPressed(): Boolean = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = container?.inflate(R.layout.fragment_dashboard)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        analytics.logEvent(AnalyticsEvents.Dashboard)

        setupSwipeRefresh()
        setupRecycler()
    }

    private fun setupRecycler() {
        theLayoutManager = SafeLayoutManager(requireContext())

        recycler_view.apply {
            layoutManager = theLayoutManager
            adapter = theAdapter

            addItemDecoration(BlockchainListDividerDecor(requireContext()))
        }
        theAdapter.items = displayList
    }

    private fun setupToolbar() {
        activity.supportActionBar?.let {
            activity.setupToolbar(it, R.string.dashboard_title)
        }
    }

    private fun setupSwipeRefresh() {

        swipe.setOnRefreshListener { model.process(RefreshAllIntent) }

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
        compositeDisposable += actionEvent.subscribe {
            initOrUpdateAssets()
        }

        (activity as? MainActivity)?.let {
            compositeDisposable += it.refreshAnnouncements.observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    if (announcements.enable()) {
                        announcements.checkLatest(announcementHost, compositeDisposable)
                    }
                }
        }

        announcements.checkLatest(announcementHost, compositeDisposable)

        initOrUpdateAssets()
    }

    private fun initOrUpdateAssets() {
        if (displayList.isEmpty()) {
            model.process(GetAvailableAssets)
        } else {
            model.process(RefreshAllIntent)
        }
    }

    override fun onPause() {
        compositeDisposable.clear()
        rxBus.unregister(ActionEvent::class.java, actionEvent)
        super.onPause()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            MainActivity.SETTINGS_EDIT,
            MainActivity.ACCOUNT_EDIT -> model.process(RefreshAllIntent)
            BACKUP_FUNDS_REQUEST_CODE -> {
                state?.backupSheetDetails?.let {
                    model.process(CheckBackupStatus(it.account, it.action))
                }
            }
        }
    }

    private fun onAssetClicked(cryptoCurrency: CryptoCurrency) {
        model.process(LaunchAssetDetailsFlow(cryptoCurrency))
    }

    private fun onFundsClicked(fiatAccount: FiatAccount) {
        model.process(ShowFiatAssetDetails(fiatAccount))
    }

    private val announcementHost = object : AnnouncementHost {

        override val disposables: CompositeDisposable
            get() = compositeDisposable

        override fun showAnnouncementCard(card: AnnouncementCard) {
            model.process(ShowAnnouncement(card))
        }

        override fun dismissAnnouncementCard() {
            model.process(ClearAnnouncement)
        }

        override fun startKyc(campaignType: CampaignType) = navigator().launchKyc(campaignType)

        override fun startSwap() = navigator().tryTolaunchSwap()

        override fun startPitLinking() = navigator().launchThePitLinking()

        override fun startFundsBackup() = navigator().launchBackupFunds()

        override fun startSetup2Fa() = navigator().launchSetup2Fa()

        override fun startVerifyEmail() = navigator().launchVerifyEmail()

        override fun startEnableFingerprintLogin() = navigator().launchSetupFingerprintLogin()

        override fun startIntroTourGuide() = navigator().launchIntroTour()

        override fun startTransferCrypto() = navigator().launchTransfer()

        override fun startStxReceivedDetail() =
            model.process(ShowDashboardSheet(DashboardSheet.STX_AIRDROP_COMPLETE))

        override fun finishSimpleBuySignup() {
            navigator().resumeSimpleBuyKyc()
        }

        override fun startSimpleBuy(cryptoCurrency: CryptoCurrency) {
            navigator().startSimpleBuy(cryptoCurrency)
        }

        override fun startSell() {
            navigator().launchSimpleBuySell(BuySellFragment.BuySellViewType.TYPE_SELL)
        }

        override fun startInterestDashboard() {
            navigator().startInterestDashboard()
        }

        override fun showFiatFundsKyc() {
            model.process(ShowDashboardSheet(DashboardSheet.FIAT_FUNDS_NO_KYC))
        }

        override fun showBankLinking() =
            model.process(ShowBankLinkingSheet())

        override fun openBrowserLink(url: String) =
            requireContext().launchUrlInBrowser(url)
    }

    override fun depositFiat(account: FiatAccount) {
        model.process(ShowBankLinkingSheet(account))
    }

    override fun showFundsKyc() {
        model.process(ShowDashboardSheet(DashboardSheet.FIAT_FUNDS_NO_KYC))
    }

    override fun verificationCtaClicked() {
        navigator().launchKyc(CampaignType.FiatFunds)
    }

    // DialogBottomSheet.Host
    override fun onSheetClosed() {
        model.process(ClearBottomSheet)
    }

    override fun onFlowFinished() {
        model.process(ClearBottomSheet)
    }

    override fun launchNewSendFor(account: SingleAccount, action: AssetAction) {
        if (account is CustodialTradingAccount) {
            model.process(CheckBackupStatus(account, action))
        } else {
            model.process(LaunchSendFlow(account, action))
        }
    }

    override fun goToReceiveFor(account: SingleAccount) =
        when (account) {
            is CryptoNonCustodialAccount -> {
                clearBottomSheet()
                startOldReceiveFor(account)
            }
            else -> throw IllegalStateException("The Send action is invalid for account: ${account.label}")
        }.exhaustive

    override fun gotoActivityFor(account: BlockchainAccount) =
        navigator().gotoActivityFor(account)

    override fun withdrawFiat(currency: String) {
        navigator().goToWithdraw(currency)
    }

    override fun goToDeposit(
        fromAccount: SingleAccount,
        toAccount: SingleAccount,
        action: AssetAction
    ) {
        model.process(LaunchDepositFlow(toAccount, fromAccount, action))
    }

    override fun goToSummary(account: SingleAccount, asset: CryptoCurrency) {
        model.process(UpdateSelectedCryptoAccount(account, asset))
        model.process(ShowDashboardSheet(DashboardSheet.INTEREST_SUMMARY))
    }

    override fun goToSellFrom(account: CryptoAccount) {
        TransactionFlow(
            sourceAccount = account,
            action = AssetAction.Sell
        ).apply {
            startFlow(
                fragmentManager = childFragmentManager,
                host = this@DashboardFragment
            )
        }
    }

    override fun gotoSwap(account: SingleAccount) {
        require(account is CryptoAccount)

        clearBottomSheet()
        navigator().tryTolaunchSwap(sourceAccount = account)
    }

    override fun goToInterestDashboard() {
        navigator().startInterestDashboard()
    }

    override fun startBackupForTransfer() {
        navigator().launchBackupFunds(this, BACKUP_FUNDS_REQUEST_CODE)
    }

    override fun startTransferFunds(account: SingleAccount, action: AssetAction) {
        model.process(LaunchSendFlow(account, action))
    }

    private fun startOldReceiveFor(account: SingleAccount) {
        require(account is CryptoAccount)
        ReceiveActivity.start(requireContext(), account)
    }

    companion object {
        fun newInstance() = DashboardFragment()

        private const val IDX_CARD_ANNOUNCE = 0
        private const val IDX_CARD_BALANCE = 1
        private const val IDX_FUNDS_BALANCE = 2
        private const val IDX_ASSET_CARDS_START = 3

        private const val BACKUP_FUNDS_REQUEST_CODE = 8265
    }
}

/**
 * supportsPredictiveItemAnimations = false to avoid crashes when computing changes.
 */
private class SafeLayoutManager(context: Context) : LinearLayoutManager(context) {
    override fun supportsPredictiveItemAnimations() = false
}
