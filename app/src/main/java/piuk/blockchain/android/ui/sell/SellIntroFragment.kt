package piuk.blockchain.android.ui.sell

import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.blockchain.koin.scopedInject
import com.blockchain.nabu.datamanagers.CustodialWalletManager
import com.blockchain.nabu.datamanagers.EligibilityProvider
import com.blockchain.nabu.models.responses.nabu.KycTierLevel
import com.blockchain.nabu.service.TierService
import com.blockchain.notifications.analytics.Analytics
import com.blockchain.preferences.CurrencyPrefs
import com.blockchain.ui.urllinks.URL_CONTACT_SUPPORT
import info.blockchain.balance.CryptoCurrency
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.rxkotlin.zipWith
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.sell_intro_fragment.*
import org.koin.android.ext.android.inject
import piuk.blockchain.android.R
import piuk.blockchain.android.campaign.CampaignType
import piuk.blockchain.android.coincore.AssetAction
import piuk.blockchain.android.coincore.BlockchainAccount
import piuk.blockchain.android.coincore.Coincore
import piuk.blockchain.android.coincore.CryptoAccount
import piuk.blockchain.android.ui.customviews.ButtonOptions
import piuk.blockchain.android.ui.customviews.IntroHeaderView
import piuk.blockchain.android.ui.customviews.VerifyIdentityBenefit
import piuk.blockchain.android.ui.customviews.account.CellDecorator
import piuk.blockchain.android.ui.home.HomeNavigator
import piuk.blockchain.android.ui.transactionflow.DialogFlow
import piuk.blockchain.android.ui.transactionflow.TransactionFlow
import piuk.blockchain.android.ui.transfer.AccountsSorting
import piuk.blockchain.android.util.gone
import piuk.blockchain.android.util.inflate
import piuk.blockchain.android.util.visible

class SellIntroFragment : Fragment(), DialogFlow.FlowHost {
    interface SellIntroHost {
        fun onSellFinished()
        fun onSellInfoClicked()
        fun onSellListEmptyCta()
    }

    private val host: SellIntroHost by lazy {
        parentFragment as? SellIntroHost ?: throw IllegalStateException(
            "Host fragment is not a SellIntroHost"
        )
    }

    private val tierService: TierService by scopedInject()
    private val coincore: Coincore by scopedInject()
    private val custodialWalletManager: CustodialWalletManager by scopedInject()
    private val eligibilityProvider: EligibilityProvider by scopedInject()
    private val currencyPrefs: CurrencyPrefs by inject()
    private val analytics: Analytics by inject()
    private val accountsSorting: AccountsSorting by inject()
    private val compositeDisposable = CompositeDisposable()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = container?.inflate(R.layout.sell_intro_fragment)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadSellDetails()
    }

    private fun loadSellDetails() {
        compositeDisposable += tierService.tiers()
            .zipWith(eligibilityProvider.isEligibleForSimpleBuy(forceRefresh = true))
            .subscribeOn(Schedulers.io())
            .doOnSubscribe {
                sell_empty.gone()
            }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeBy(onSuccess = { (kyc, eligible) ->
                when {
                    kyc.isApprovedFor(KycTierLevel.GOLD) && eligible -> {
                        renderKycedUserUi()
                    }
                    kyc.isRejectedFor(KycTierLevel.GOLD) -> {
                        renderRejectedKycedUserUi()
                    }
                    kyc.isApprovedFor(KycTierLevel.GOLD) && !eligible -> {
                        renderRejectedKycedUserUi()
                    }
                    else -> {
                        renderNonKycedUserUi()
                    }
                }
            }, onError = {
                renderSellError()
            })
    }

    private fun renderSellError() {
        accounts_list.gone()
        sell_empty.setDetails {
            loadSellDetails()
        }
        sell_empty.visible()
    }

    private fun renderSellEmpty() {
        accounts_list.gone()
        sell_empty.setDetails(
            R.string.sell_intro_empty_title,
            R.string.sell_intro_empty_label,
            ctaText = R.string.buy_now
        ) {
            host.onSellListEmptyCta()
        }
        sell_empty.visible()
    }

    private fun renderRejectedKycedUserUi() {
        kyc_benefits.visible()
        accounts_list.gone()

        kyc_benefits.initWithBenefits(
            benefits = listOf(
                VerifyIdentityBenefit(
                    getString(R.string.invalid_id),
                    getString(R.string.invalid_id_description)
                ), VerifyIdentityBenefit(
                    getString(R.string.information_missmatch),
                    getString(R.string.information_missmatch_description)
                ),
                VerifyIdentityBenefit(
                    getString(R.string.blocked_by_local_laws),
                    getString(R.string.sell_intro_kyc_subtitle_3)
                )
            ),
            title = getString(R.string.unable_to_verify_id),
            description = getString(R.string.unable_to_verify_id_description),
            icon = R.drawable.ic_cart,
            secondaryButton = ButtonOptions(true, getString(R.string.contact_support)) {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(URL_CONTACT_SUPPORT)))
            },
            primaryButton = ButtonOptions(false) {},
            showSheetIndicator = false,
            footerText = getString(R.string.error_contact_support)
        )
    }

    private fun renderNonKycedUserUi() {
        kyc_benefits.visible()
        accounts_list.gone()

        kyc_benefits.initWithBenefits(
            benefits = listOf(
                VerifyIdentityBenefit(
                    getString(R.string.sell_intro_kyc_title_1),
                    getString(R.string.sell_intro_kyc_subtitle_1)
                ), VerifyIdentityBenefit(
                    getString(R.string.sell_intro_kyc_title_2),
                    getString(R.string.sell_intro_kyc_subtitle_2)
                ),
                VerifyIdentityBenefit(
                    getString(R.string.sell_intro_kyc_title_3),
                    getString(R.string.sell_intro_kyc_subtitle_3)
                )
            ),
            title = getString(R.string.sell_crypto),
            description = getString(R.string.sell_crypto_subtitle),
            icon = R.drawable.ic_cart,
            secondaryButton = ButtonOptions(false) {},
            primaryButton = ButtonOptions(true) {
                (activity as? HomeNavigator)?.launchKyc(CampaignType.SimpleBuy)
            },
            showSheetIndicator = false
        )
    }

    private fun renderKycedUserUi() {
        kyc_benefits.gone()
        accounts_list.visible()

        compositeDisposable += supportedCryptoCurrencies()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeBy(onSuccess = { supportedCryptos ->
                val introHeaderView = IntroHeaderView(requireContext())
                introHeaderView.setDetails(
                    icon = R.drawable.ic_sell_minus,
                    label = R.string.select_wallet_to_sell,
                    title = R.string.sell_for_cash
                )

                accounts_list.initialise(
                    coincore.allWalletsWithActions(
                        setOf(AssetAction.Sell),
                        accountsSorting.sorter()
                    ).map {
                        it.filterIsInstance<CryptoAccount>().filter { account ->
                            supportedCryptos.contains(account.asset)
                        }
                    },
                    status = ::statusDecorator,
                    introView = introHeaderView
                )

                renderSellInfo()

                accounts_list.onAccountSelected = { account ->
                    (account as? CryptoAccount)?.let {
                        startSellFlow(it)
                    }
                }

                accounts_list.onListLoaded = {
                    if (it) renderSellEmpty()
                }
            }, onError = {
                renderSellError()
            })
    }

    private fun renderSellInfo() {
        val sellInfoIntro = getString(R.string.sell_info_blurb_1)
        val sellInfoBold = getString(R.string.sell_info_blurb_2)
        val sellInfoEnd = getString(R.string.sell_info_blurb_3)

        val sb = SpannableStringBuilder()
            .append(sellInfoIntro)
            .append(sellInfoBold)
            .append(sellInfoEnd)
        sb.setSpan(
            StyleSpan(Typeface.BOLD), sellInfoIntro.length, sellInfoIntro.length + sellInfoBold.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
    }

    private fun statusDecorator(account: BlockchainAccount): CellDecorator = SellCellDecorator(account)

    private fun startSellFlow(it: CryptoAccount) {
        TransactionFlow(
            sourceAccount = it,
            action = AssetAction.Sell
        ).apply {
            startFlow(
                fragmentManager = fragmentManager ?: return,
                host = this@SellIntroFragment
            )
        }
    }

    private fun supportedCryptoCurrencies(): Single<List<CryptoCurrency>> {
        val availableFiats =
            custodialWalletManager.getSupportedFundsFiats(currencyPrefs.selectedFiatCurrency, true)
        return custodialWalletManager.getSupportedBuySellCryptoCurrencies()
            .zipWith(availableFiats) { supportedPairs, fiats ->
                supportedPairs.pairs.filter { fiats.contains(it.fiatCurrency) }
                    .map { it.cryptoCurrency }
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        compositeDisposable.clear()
    }

    companion object {
        fun newInstance() = SellIntroFragment()
    }

    override fun onFlowFinished() {
        host.onSellFinished()
        loadSellDetails()
    }
}