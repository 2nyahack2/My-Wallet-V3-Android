package piuk.blockchain.android.ui.swap

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.blockchain.koin.scopedInject
import com.blockchain.notifications.analytics.Analytics
import com.blockchain.preferences.CurrencyPrefs
import com.blockchain.preferences.WalletStatus
import com.blockchain.nabu.datamanagers.CustodialWalletManager
import com.blockchain.nabu.models.responses.nabu.KycTierLevel
import com.blockchain.nabu.models.responses.nabu.KycTiers
import com.blockchain.nabu.datamanagers.TransferLimits
import com.blockchain.nabu.datamanagers.CustodialOrder
import com.blockchain.nabu.service.TierService
import info.blockchain.balance.Money
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.Singles
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.rxkotlin.subscribeBy
import kotlinx.android.synthetic.main.fragment_swap.*
import kotlinx.android.synthetic.main.pending_swaps_layout.view.*
import org.koin.android.ext.android.inject
import piuk.blockchain.android.R
import piuk.blockchain.android.campaign.CampaignType
import piuk.blockchain.android.coincore.AssetAction
import piuk.blockchain.android.ui.base.SlidingModalBottomDialog
import piuk.blockchain.android.ui.customviews.ButtonOptions
import piuk.blockchain.android.ui.customviews.KycBenefitsBottomSheet
import piuk.blockchain.android.ui.customviews.TrendingPair
import piuk.blockchain.android.ui.customviews.TrendingPairsProvider
import piuk.blockchain.android.ui.customviews.VerifyIdentityBenefit
import piuk.blockchain.android.ui.kyc.navhost.KycNavHostActivity
import piuk.blockchain.android.ui.transactionflow.DialogFlow
import piuk.blockchain.android.ui.transactionflow.TransactionFlow
import piuk.blockchain.android.ui.transactionflow.analytics.SwapAnalyticsEvents
import piuk.blockchain.androidcore.data.exchangerate.ExchangeRateDataManager
import piuk.blockchain.androidcoreui.ui.customviews.ToastCustom
import piuk.blockchain.android.util.gone
import piuk.blockchain.android.util.inflate
import piuk.blockchain.android.util.visible
import piuk.blockchain.android.util.visibleIf
import timber.log.Timber

class SwapFragment : Fragment(), DialogFlow.FlowHost, KycBenefitsBottomSheet.Host, TradingWalletPromoBottomSheet.Host {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = container?.inflate(R.layout.fragment_swap)

    private val kycTierService: TierService by scopedInject()
    private val exchangeRateDataManager: ExchangeRateDataManager by scopedInject()
    private val trendingPairsProvider: TrendingPairsProvider by scopedInject()
    private val walletManager: CustodialWalletManager by scopedInject()
    private val currencyPrefs: CurrencyPrefs by inject()
    private val walletPrefs: WalletStatus by inject()
    private val analytics: Analytics by inject()

    private val compositeDisposable = CompositeDisposable()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        swap_error.setDetails {
            loadSwapOrKyc(true)
        }

        swap_cta.apply {
            analytics.logEvent(SwapAnalyticsEvents.NewSwapClicked)
            setOnClickListener {
                if (!walletPrefs.hasSeenTradingSwapPromo) {
                    walletPrefs.setSeenTradingSwapPromo()
                    showBottomSheet(TradingWalletPromoBottomSheet.newInstance())
                } else {
                    startSwap()
                }
            }
            gone()
        }
        pending_swaps.pending_list.addItemDecoration(DividerItemDecoration(
            context,
            DividerItemDecoration.VERTICAL
        ))

        loadSwapOrKyc(showLoading = true)
    }

    private fun startSwap() {
        TransactionFlow(
            action = AssetAction.Swap
        ).apply {
            startFlow(
                fragmentManager = childFragmentManager,
                host = this@SwapFragment
            )
        }
    }

    override fun verificationCtaClicked() {
        analytics.logEvent(SwapAnalyticsEvents.SwapSilverLimitSheetCta)
        walletPrefs.setSeenSwapPromo()
        KycNavHostActivity.start(requireActivity(), CampaignType.Swap)
    }

    override fun onSheetClosed() {
        walletPrefs.setSeenSwapPromo()
    }

    override fun startNewSwap() {
        startSwap()
    }

    private fun loadSwapOrKyc(showLoading: Boolean) {
        compositeDisposable +=
            Singles.zip(
                kycTierService.tiers(),
                trendingPairsProvider.getTrendingPairs(),
                walletManager.getSwapLimits(currencyPrefs.selectedFiatCurrency),
                walletManager.getSwapTrades().onErrorReturn { emptyList() }
            ) { tiers: KycTiers, pairs: List<TrendingPair>, limits: TransferLimits, orders: List<CustodialOrder> ->
                SwapComposite(
                    tiers,
                    pairs,
                    limits,
                    orders
                )
            }
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe {
                    if (showLoading)
                        showLoadingUi()
                }
                .subscribeBy(
                    onSuccess = { composite ->
                        showSwapUi(composite.orders)

                        if (composite.tiers.isVerified()) {
                            swap_view_switcher.displayedChild = SWAP_VIEW
                            swap_header.toggleBottomSeparator(false)

                            val onPairClicked = onTrendingPairClicked()

                            swap_trending.initialise(
                                pairs = composite.pairs,
                                onSwapPairClicked = onPairClicked
                            )

                            if (!composite.tiers.isInitialisedFor(KycTierLevel.GOLD)) {
                                showKycUpsellIfEligible(composite.limits)
                            }
                        } else {
                            swap_view_switcher.displayedChild = KYC_VIEW
                            initKycView()
                        }
                    },
                    onError = {
                        showErrorUi()

                        ToastCustom.makeText(
                            requireContext(),
                            getString(R.string.transfer_wallets_load_error),
                            ToastCustom.LENGTH_SHORT,
                            ToastCustom.TYPE_ERROR
                        )

                        Timber.e("Error loading swap kyc service $it")
                    })
    }

    private fun showKycUpsellIfEligible(limits: TransferLimits) {
        val usedUpLimitPercent = (limits.maxLimit / limits.maxOrder).toFloat() * 100
        if (usedUpLimitPercent >= KYC_UPSELL_PERCENTAGE && !walletPrefs.hasSeenSwapPromo) {
            analytics.logEvent(SwapAnalyticsEvents.SwapSilverLimitSheet)
            val fragment = KycBenefitsBottomSheet.newInstance(
                KycBenefitsBottomSheet.BenefitsDetails(
                    title = getString(R.string.swap_kyc_upsell_title),
                    description = getString(R.string.swap_kyc_upsell_desc),
                    listOfBenefits = listOf(
                        VerifyIdentityBenefit(
                            getString(R.string.swap_kyc_upsell_1_title),
                            getString(R.string.swap_kyc_upsell_1_desc)),
                        VerifyIdentityBenefit(
                            getString(R.string.swap_kyc_upsell_2_title),
                            getString(R.string.swap_kyc_upsell_2_desc)),
                        VerifyIdentityBenefit(
                            getString(R.string.swap_kyc_upsell_3_title),
                            getString(R.string.swap_kyc_upsell_3_desc))
                    )
                )
            )
            showBottomSheet(fragment)
        }
    }

    private fun showBottomSheet(fragment: SlidingModalBottomDialog) {
        childFragmentManager.beginTransaction().add(fragment, TAG).commit()
    }

    private fun onTrendingPairClicked(): (TrendingPair) -> Unit = { pair ->
        analytics.logEvent(SwapAnalyticsEvents.TrendingPairClicked)
        TransactionFlow(
            sourceAccount = pair.sourceAccount,
            target = pair.destinationAccount,
            action = AssetAction.Swap
        ).apply {
            startFlow(
                fragmentManager = childFragmentManager,
                host = this@SwapFragment
            )
        }
    }

    private fun initKycView() {
        swap_kyc_benefits.initWithBenefits(
            listOf(
                VerifyIdentityBenefit(
                    getString(R.string.swap_kyc_1_title),
                    getString(R.string.swap_kyc_1_label)),
                VerifyIdentityBenefit(
                    getString(R.string.swap_kyc_2_title),
                    getString(R.string.swap_kyc_2_label)),
                VerifyIdentityBenefit(
                    getString(R.string.swap_kyc_3_title),
                    getString(R.string.swap_kyc_3_label))
            ),
            getString(R.string.swap_kyc_title),
            getString(R.string.swap_kyc_desc),
            R.drawable.ic_swap_blue_circle,
            ButtonOptions(visible = true, text = getString(R.string.swap_kyc_cta)) {
                analytics.logEvent(SwapAnalyticsEvents.VerifyNowClicked)
                KycNavHostActivity.start(requireActivity(), CampaignType.Swap)
            },
            ButtonOptions(visible = false),
            showSheetIndicator = false
        )
    }

    private fun showErrorUi() {
        swap_loading_indicator.gone()
        swap_error.visible()
    }

    private fun showSwapUi(orders: List<CustodialOrder>) {
        val pendingOrders = orders.filter { it.state.isPending }
        val hasPendingOrder = pendingOrders.isNotEmpty()
        swap_loading_indicator.gone()
        swap_view_switcher.visible()
        swap_error.gone()
        swap_cta.visible()
        swap_trending.visibleIf { !hasPendingOrder }
        pending_swaps.visibleIf { hasPendingOrder }
        pending_swaps.pending_list.apply {
            adapter =
                PendingSwapsAdapter(
                    pendingOrders) { money: Money ->
                    money.toFiat(exchangeRateDataManager, currencyPrefs.selectedFiatCurrency)
                }
            layoutManager = LinearLayoutManager(activity)
        }
    }

    private fun showLoadingUi() {
        swap_loading_indicator.visible()
        swap_view_switcher.gone()
        swap_error.gone()
    }

    companion object {
        private const val KYC_UPSELL_PERCENTAGE = 90
        private const val SWAP_VIEW = 0
        private const val KYC_VIEW = 1
        private const val TAG = "BOTTOM_SHEET"
        fun newInstance(): SwapFragment =
            SwapFragment()
    }

    override fun onFlowFinished() {
        loadSwapOrKyc(showLoading = false)
    }

    private data class SwapComposite(
        val tiers: KycTiers,
        val pairs: List<TrendingPair>,
        val limits: TransferLimits,
        val orders: List<CustodialOrder>
    )

    override fun onDestroyView() {
        compositeDisposable.clear()
        super.onDestroyView()
    }
}
