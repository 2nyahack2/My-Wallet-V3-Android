package piuk.blockchain.android.simplebuy

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.blockchain.koin.scopedInject
import com.blockchain.nabu.datamanagers.PaymentMethod
import com.blockchain.nabu.datamanagers.custodialwalletimpl.PaymentMethodType
import com.blockchain.nabu.models.data.BankPartner
import com.blockchain.nabu.models.data.LinkBankTransfer
import com.blockchain.nabu.models.data.YodleeAttributes
import info.blockchain.balance.CryptoCurrency
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.rxkotlin.subscribeBy
import kotlinx.android.synthetic.main.fragment_activity.*
import kotlinx.android.synthetic.main.toolbar_general.toolbar_general
import piuk.blockchain.android.R
import piuk.blockchain.android.campaign.CampaignType
import piuk.blockchain.android.cards.CardDetailsActivity
import piuk.blockchain.android.ui.linkbank.yodlee.LinkBankFragment
import piuk.blockchain.android.ui.linkbank.yodlee.YodleeSplashFragment
import piuk.blockchain.android.ui.linkbank.yodlee.YodleeWebViewFragment
import piuk.blockchain.android.ui.base.BlockchainActivity
import piuk.blockchain.android.ui.home.MainActivity
import piuk.blockchain.android.ui.kyc.navhost.KycNavHostActivity
import piuk.blockchain.android.util.ViewUtils
import piuk.blockchain.android.util.gone
import piuk.blockchain.android.util.visible
import piuk.blockchain.androidcore.utils.helperfunctions.consume
import piuk.blockchain.androidcore.utils.helperfunctions.unsafeLazy

class SimpleBuyActivity : BlockchainActivity(), SimpleBuyNavigator {
    override val alwaysDisableScreenshots: Boolean
        get() = false

    override val enableLogoutTimer: Boolean = false
    private val simpleBuyModel: SimpleBuyModel by scopedInject()
    private val compositeDisposable = CompositeDisposable()
    private val simpleBuyFlowNavigator: SimpleBuyFlowNavigator by scopedInject()

    private val startedFromDashboard: Boolean by unsafeLazy {
        intent.getBooleanExtra(STARTED_FROM_NAVIGATION_KEY, false)
    }

    private val startedFromKycResume: Boolean by unsafeLazy {
        intent.getBooleanExtra(STARTED_FROM_KYC_RESUME, false)
    }

    private val cryptoCurrency: CryptoCurrency? by unsafeLazy {
        intent.getSerializableExtra(CRYPTOCURRENCY_KEY) as? CryptoCurrency
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.fragment_activity)
        setSupportActionBar(toolbar_general)
        if (savedInstanceState == null) {
            subscribeForNavigation()
        }
    }

    override fun onSheetClosed() {
        subscribeForNavigation()
    }

    private fun subscribeForNavigation() {
        compositeDisposable += simpleBuyFlowNavigator.navigateTo(
            startedFromKycResume,
            startedFromDashboard,
            cryptoCurrency
        )
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeBy {
                when (it) {
                    is BuyNavigation.CurrencySelection -> launchCurrencySelector(it.currencies)
                    is BuyNavigation.FlowScreenWithCurrency -> startFlow(it)
                    BuyNavigation.PendingOrderScreen -> goToPendingOrderScreen()
                }
            }
    }

    private fun launchCurrencySelector(currencies: List<String>) {
        compositeDisposable.clear()
        showBottomSheet(SimpleBuySelectCurrencyFragment.newInstance(currencies))
    }

    private fun startFlow(screenWithCurrency: BuyNavigation.FlowScreenWithCurrency) {
        when (screenWithCurrency.flowScreen) {
            FlowScreen.ENTER_AMOUNT -> goToBuyCryptoScreen(false, screenWithCurrency.cryptoCurrency)
            FlowScreen.KYC -> startKyc()
            FlowScreen.KYC_VERIFICATION -> goToKycVerificationScreen(false)
            FlowScreen.CHECKOUT -> goToCheckOutScreen(false)
            FlowScreen.ADD_CARD -> addNewCard()
        }
    }

    private fun addNewCard() {
        val intent = Intent(this, CardDetailsActivity::class.java)
        startActivityForResult(intent, CardDetailsActivity.ADD_CARD_REQUEST_CODE)
    }

    override fun onDestroy() {
        super.onDestroy()
        compositeDisposable.clear()
    }

    override fun exitSimpleBuyFlow() {
        if (!startedFromDashboard) {
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } else {
            finish()
        }
    }

    override fun goToBuyCryptoScreen(addToBackStack: Boolean, cryptoCurrency: CryptoCurrency) {
        supportFragmentManager.beginTransaction()
            .replace(
                R.id.content_frame,
                SimpleBuyCryptoFragment.newInstance(cryptoCurrency),
                SimpleBuyCryptoFragment::class.simpleName
            )
            .apply {
                if (addToBackStack) {
                    addToBackStack(SimpleBuyCryptoFragment::class.simpleName)
                }
            }
            .commitAllowingStateLoss()
    }

    override fun goToCheckOutScreen(addToBackStack: Boolean) {
        ViewUtils.hideKeyboard(this)

        supportFragmentManager.beginTransaction()
            .replace(R.id.content_frame, SimpleBuyCheckoutFragment(), SimpleBuyCheckoutFragment::class.simpleName)
            .apply {
                if (addToBackStack) {
                    addToBackStack(SimpleBuyCheckoutFragment::class.simpleName)
                }
            }
            .commitAllowingStateLoss()
    }

    override fun goToKycVerificationScreen(addToBackStack: Boolean) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.content_frame, SimpleBuyPendingKycFragment(), SimpleBuyPendingKycFragment::class.simpleName)
            .apply {
                if (addToBackStack) {
                    addToBackStack(SimpleBuyPendingKycFragment::class.simpleName)
                }
            }
            .commitAllowingStateLoss()
    }

    override fun goToPendingOrderScreen() {
        supportFragmentManager.beginTransaction()
            .replace(
                R.id.content_frame,
                SimpleBuyCheckoutFragment.newInstance(true),
                SimpleBuyCheckoutFragment::class.simpleName
            )
            .commitAllowingStateLoss()
    }

    override fun startKyc() {
        KycNavHostActivity.startForResult(this, CampaignType.SimpleBuy, KYC_STARTED)
    }

    override fun pop() {
        onBackPressed()
    }

    override fun hasMoreThanOneFragmentInTheStack(): Boolean =
        supportFragmentManager.backStackEntryCount > 1

    override fun goToPaymentScreen(addToBackStack: Boolean) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.content_frame, SimpleBuyPaymentFragment(), SimpleBuyPaymentFragment::class.simpleName)
            .apply {
                if (addToBackStack) {
                    addToBackStack(SimpleBuyPaymentFragment::class.simpleName)
                }
            }
            .commitAllowingStateLoss()
    }

    override fun launchIntro() {
        supportFragmentManager.beginTransaction()
            .add(R.id.content_frame, SimpleBuyIntroFragment())
            .commitAllowingStateLoss()
    }

    override fun linkBankWithPartner(bankTransfer: LinkBankTransfer) {
        when (bankTransfer.partner) {
            BankPartner.YODLEE -> {
                val attributes = bankTransfer.attributes as YodleeAttributes
                launchYodleeSplash(attributes, bankTransfer.id)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == KYC_STARTED && resultCode == RESULT_KYC_SIMPLE_BUY_COMPLETE) {
            simpleBuyModel.process(SimpleBuyIntent.KycCompleted)
            goToKycVerificationScreen()
        } else if (requestCode == CardDetailsActivity.ADD_CARD_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                val card = (data?.extras?.getSerializable(CardDetailsActivity.CARD_KEY) as?
                    PaymentMethod.Card) ?: return
                val cardId = card.cardId
                val cardLabel = card.uiLabel()
                val cardPartner = card.partner

                simpleBuyModel.process(
                    SimpleBuyIntent.UpdateSelectedPaymentMethod(
                        cardId,
                        cardLabel,
                        cardPartner,
                        PaymentMethodType.PAYMENT_CARD
                    )
                )
                goToCheckOutScreen()
            } else
                finish()
        }
    }

    override fun onSupportNavigateUp(): Boolean = consume {
        onBackPressed()
    }

    override fun showLoading() {
        progress.visible()
    }

    override fun hideLoading() {
        progress.gone()
    }

    override fun launchYodleeSplash(attributes: YodleeAttributes, bankId: String) {
        ViewUtils.hideKeyboard(this)
        supportFragmentManager.beginTransaction()
            .replace(
                R.id.content_frame, YodleeSplashFragment.newInstance(
                    attributes = attributes,
                    bankId = bankId
                )
            )
            .addToBackStack(YodleeSplashFragment::class.simpleName)
            .commitAllowingStateLoss()
    }

    override fun launchYodleeWebview(attributes: YodleeAttributes, bankId: String) {
        supportFragmentManager.beginTransaction()
            .replace(
                R.id.content_frame,
                YodleeWebViewFragment.newInstance(
                    attributes = attributes,
                    bankId = bankId
                )
            )
            .addToBackStack(YodleeWebViewFragment::class.simpleName)
            .commitAllowingStateLoss()
    }

    override fun launchBankLinking(accountProviderId: String, accountId: String, bankId: String) {
        supportFragmentManager.beginTransaction()
            .replace(
                R.id.content_frame,
                LinkBankFragment.newInstance(
                    accountProviderId = accountProviderId,
                    accountId = accountId,
                    linkingBankId = bankId
                )
            )
            .addToBackStack(LinkBankFragment::class.simpleName)
            .commitAllowingStateLoss()
    }

    override fun launchBankLinkingWithError(errorState: ErrorState) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.content_frame, LinkBankFragment.newInstance(errorState))
            .addToBackStack(LinkBankFragment::class.simpleName)
            .commitAllowingStateLoss()
    }

    override fun bankLinkingCancelled() {
        exitSimpleBuyFlow()
    }

    override fun bankLinkingFinished(id: String) {
        goToCheckOutScreen(false)
    }

    override fun retry() {
        pop()
    }

    companion object {
        const val KYC_STARTED = 6788
        const val RESULT_KYC_SIMPLE_BUY_COMPLETE = 7854

        private const val STARTED_FROM_NAVIGATION_KEY = "started_from_navigation_key"
        private const val CRYPTOCURRENCY_KEY = "crypto_currency_key"
        private const val STARTED_FROM_KYC_RESUME = "started_from_kyc_resume_key"

        fun newInstance(
            context: Context,
            cryptoCurrency: CryptoCurrency? = null,
            launchFromNavigationBar: Boolean = false,
            launchKycResume: Boolean = false
        ) = Intent(context, SimpleBuyActivity::class.java).apply {
            putExtra(STARTED_FROM_NAVIGATION_KEY, launchFromNavigationBar)
            putExtra(CRYPTOCURRENCY_KEY, cryptoCurrency)
            putExtra(STARTED_FROM_KYC_RESUME, launchKycResume)
        }
    }
}