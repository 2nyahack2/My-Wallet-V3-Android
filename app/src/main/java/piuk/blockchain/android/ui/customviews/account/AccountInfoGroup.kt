package piuk.blockchain.android.ui.customviews.account

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.appcompat.content.res.AppCompatResources
import androidx.constraintlayout.widget.ConstraintLayout
import com.blockchain.koin.scopedInject
import com.blockchain.preferences.CurrencyPrefs
import info.blockchain.balance.ExchangeRates
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.rxkotlin.subscribeBy
import kotlinx.android.synthetic.main.view_account_group_overview.view.*
import org.koin.core.KoinComponent
import piuk.blockchain.android.R
import piuk.blockchain.android.coincore.AccountGroup
import piuk.blockchain.android.coincore.impl.AllWalletsAccount
import piuk.blockchain.android.util.invisible
import piuk.blockchain.android.util.visible
import timber.log.Timber

class AccountInfoGroup @JvmOverloads constructor(
    ctx: Context,
    attr: AttributeSet? = null,
    defStyle: Int = 0
) : ConstraintLayout(ctx, attr, defStyle), KoinComponent {

    private val exchangeRates: ExchangeRates by scopedInject()
    private val currencyPrefs: CurrencyPrefs by scopedInject()

    init {
        LayoutInflater.from(context)
            .inflate(R.layout.view_account_group_overview, this, true)
    }

    private val disposables = CompositeDisposable()

    fun updateAccount(account: AccountGroup) {
        disposables.clear()
        updateView(account)
    }

    private fun updateView(account: AccountGroup) {
        // Only supports AllWallets at this time
        require(account is AllWalletsAccount)

        disposables.clear()

        val currency = currencyPrefs.selectedFiatCurrency
        icon.setImageDrawable(AppCompatResources.getDrawable(context, R.drawable.ic_all_wallets_blue))

        wallet_name.text = account.label

        asset_subtitle.text = context.getString(R.string.activity_wallet_total_balance)

        wallet_balance_fiat.invisible()
        wallet_currency.text = currency

        disposables += account.fiatBalance(currency, exchangeRates)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeBy(
                onSuccess = {
                    wallet_balance_fiat.text = it.toStringWithSymbol()
                    wallet_balance_fiat.visible()
                },
                onError = {
                    Timber.e("Cannot get balance for ${account.label}")
                }
            )
    }

    fun dispose() {
        disposables.clear()
    }
}
