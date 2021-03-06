package piuk.blockchain.android.ui.sell

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.blockchain.koin.scopedInject
import com.blockchain.preferences.CurrencyPrefs
import com.blockchain.preferences.SimpleBuyPrefs
import com.blockchain.nabu.datamanagers.BuySellPairs
import com.blockchain.nabu.datamanagers.CustodialWalletManager
import com.blockchain.ui.trackProgress
import info.blockchain.balance.CryptoCurrency
import info.blockchain.balance.ExchangeRate
import info.blockchain.balance.Money
import info.blockchain.balance.percentageDelta
import info.blockchain.wallet.prices.TimeAgo
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.rxkotlin.zipWith
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.buy_intro_fragment.*
import org.koin.android.ext.android.inject
import piuk.blockchain.android.R
import piuk.blockchain.android.coincore.Coincore
import piuk.blockchain.android.simplebuy.SimpleBuyActivity
import piuk.blockchain.android.ui.customviews.IntroHeaderView
import piuk.blockchain.android.ui.customviews.account.HeaderDecoration
import piuk.blockchain.android.ui.customviews.account.removeAllHeaderDecorations
import piuk.blockchain.android.util.AppUtil
import piuk.blockchain.android.util.gone
import piuk.blockchain.android.util.inflate
import piuk.blockchain.android.util.visible

class BuyIntroFragment : Fragment() {

    private val custodialWalletManager: CustodialWalletManager by scopedInject()
    private val currencyPrefs: CurrencyPrefs by inject()
    private val compositeDisposable = CompositeDisposable()
    private val coinCore: Coincore by scopedInject()
    private val appUtil: AppUtil by inject()
    private val simpleBuyPrefs: SimpleBuyPrefs by scopedInject()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = container?.inflate(R.layout.buy_intro_fragment)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadBuyDetails()
    }

    private fun loadBuyDetails() {

        compositeDisposable +=
            custodialWalletManager.getSupportedBuySellCryptoCurrencies(
                currencyPrefs.selectedFiatCurrency)
                .flatMap { pairs ->
                    val enabledPairs = pairs.pairs.filter {
                        coinCore[it.cryptoCurrency].isEnabled
                    }
                    Single.zip(enabledPairs.map {
                        coinCore[it.cryptoCurrency].exchangeRate().zipWith(
                            coinCore[it.cryptoCurrency].historicRate(TimeAgo.ONE_DAY.epoch)
                        ).map { (currentPrice, price24h) ->
                            PriceHistory(
                                currentExchangeRate = currentPrice as ExchangeRate.CryptoToFiat,
                                exchangeRate24h = price24h as ExchangeRate.CryptoToFiat
                            )
                        }
                    }) { t: Array<Any> ->
                        t.map {
                            it as PriceHistory
                        } to pairs.copy(pairs = enabledPairs)
                    }
                }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe {
                    buy_empty.gone()
                }
                .trackProgress(appUtil.activityIndicator)
                .subscribeBy(
                    onSuccess = { (exchangeRates, buyPairs) ->
                        renderBuyIntro(buyPairs, exchangeRates)
                    },
                    onError = {
                        renderErrorState()
                    }
                )
    }

    private fun renderBuyIntro(
        buyPairs: BuySellPairs,
        pricesHistory: List<PriceHistory>
    ) {
        rv_cryptos.visible()
        buy_empty.gone()

        val introHeaderView = IntroHeaderView(requireContext())
        introHeaderView.setDetails(
            icon = R.drawable.ic_cart,
            label = R.string.select_crypto_you_want,
            title = R.string.buy_with_cash)

        rv_cryptos.removeAllHeaderDecorations()
        rv_cryptos.addItemDecoration(
            HeaderDecoration.with(requireContext())
                .parallax(0.5f)
                .setView(introHeaderView)
                .build()
        )

        rv_cryptos.layoutManager = LinearLayoutManager(activity)
        rv_cryptos.adapter = BuyCryptoCurrenciesAdapter(buyPairs.pairs.map { pair ->
            BuyCryptoItem(
                cryptoCurrency = pair.cryptoCurrency,
                price = pricesHistory.first { it.cryptoCurrency == pair.cryptoCurrency }.currentExchangeRate.price(),
                percentageDelta = pricesHistory.first { it.cryptoCurrency == pair.cryptoCurrency }.percentageDelta
            ) {
                simpleBuyPrefs.clearState()
                startActivity(SimpleBuyActivity.newInstance(
                    activity as Context,
                    pair.cryptoCurrency,
                    launchFromNavigationBar = true,
                    launchKycResume = false
                ))
            }
        })
    }

    private fun renderErrorState() {
        rv_cryptos.gone()
        buy_empty.setDetails {
            loadBuyDetails()
        }
        buy_empty.visible()
    }

    override fun onDestroyView() {
        compositeDisposable.clear()
        super.onDestroyView()
    }

    companion object {
        fun newInstance() = BuyIntroFragment()
    }
}

data class PriceHistory(
    val currentExchangeRate: ExchangeRate.CryptoToFiat,
    val exchangeRate24h: ExchangeRate.CryptoToFiat
) {
    val cryptoCurrency: CryptoCurrency
        get() = currentExchangeRate.from
    val percentageDelta: Double
        get() = currentExchangeRate.percentageDelta(exchangeRate24h)
}

data class BuyCryptoItem(
    val cryptoCurrency: CryptoCurrency,
    val price: Money,
    val percentageDelta: Double,
    val click: () -> Unit
)