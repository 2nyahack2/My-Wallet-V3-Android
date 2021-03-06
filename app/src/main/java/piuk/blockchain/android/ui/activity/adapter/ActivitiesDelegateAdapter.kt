package piuk.blockchain.android.ui.activity.adapter

import android.widget.TextView
import com.blockchain.preferences.CurrencyPrefs
import info.blockchain.balance.CryptoCurrency
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.rxkotlin.subscribeBy
import piuk.blockchain.android.coincore.CryptoActivitySummaryItem
import piuk.blockchain.android.ui.activity.CryptoActivityType
import piuk.blockchain.android.ui.adapters.AdapterDelegatesManager
import piuk.blockchain.android.ui.adapters.DelegationAdapter
import piuk.blockchain.android.util.visible
import timber.log.Timber

class ActivitiesDelegateAdapter(
    prefs: CurrencyPrefs,
    onCryptoItemClicked: (CryptoCurrency, String, CryptoActivityType) -> Unit,
    onFiatItemClicked: (String, String) -> Unit
) : DelegationAdapter<Any>(AdapterDelegatesManager(), emptyList()) {

    init {
        // Add all necessary AdapterDelegate objects here
        with(delegatesManager) {
            addAdapterDelegate(NonCustodialActivityItemDelegate(prefs, onCryptoItemClicked))
            addAdapterDelegate(SwapActivityItemDelegate(onCryptoItemClicked))
            addAdapterDelegate(CustodialTradingActivityItemDelegate(prefs, onCryptoItemClicked))
            addAdapterDelegate(SellActivityItemDelegate(onCryptoItemClicked))
            addAdapterDelegate(CustodialFiatActivityItemDelegate(onFiatItemClicked))
            addAdapterDelegate(CustodialInterestActivityItemDelegate(prefs, onCryptoItemClicked))
        }
    }
}

fun TextView.bindAndConvertFiatBalance(
    tx: CryptoActivitySummaryItem,
    disposables: CompositeDisposable,
    selectedFiatCurrency: String
) {
    disposables += tx.totalFiatWhenExecuted(selectedFiatCurrency).observeOn(AndroidSchedulers.mainThread())
        .subscribeBy(
            onSuccess = {
                text = it.toStringWithSymbol()
                visible()
            },
            onError = {
                Timber.e("Cannot convert to fiat")
            }
        )
}