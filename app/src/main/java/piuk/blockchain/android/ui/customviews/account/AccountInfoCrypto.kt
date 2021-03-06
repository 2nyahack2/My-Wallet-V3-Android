package piuk.blockchain.android.ui.customviews.account

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.constraintlayout.widget.ConstraintLayout
import com.blockchain.koin.scopedInject
import com.blockchain.preferences.CurrencyPrefs
import info.blockchain.balance.CryptoValue
import info.blockchain.balance.ExchangeRates
import info.blockchain.balance.Money
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.rxkotlin.subscribeBy
import kotlinx.android.synthetic.main.view_account_crypto_overview.view.*
import org.koin.core.KoinComponent
import piuk.blockchain.android.R
import piuk.blockchain.android.coincore.Coincore
import piuk.blockchain.android.coincore.CryptoAccount
import piuk.blockchain.android.coincore.InterestAccount
import piuk.blockchain.android.coincore.NonCustodialAccount
import piuk.blockchain.android.coincore.NullCryptoAccount
import piuk.blockchain.android.coincore.TradingAccount
import piuk.blockchain.android.util.assetName
import piuk.blockchain.android.util.setCoinIcon
import piuk.blockchain.android.util.gone
import piuk.blockchain.android.util.visible
import timber.log.Timber

class AccountInfoCrypto @JvmOverloads constructor(
    ctx: Context,
    attr: AttributeSet? = null,
    defStyle: Int = 0
) : ConstraintLayout(ctx, attr, defStyle), KoinComponent {

    private val exchangeRates: ExchangeRates by scopedInject()
    private val currencyPrefs: CurrencyPrefs by scopedInject()
    private val coincore: Coincore by scopedInject()
    private val compositeDisposable = CompositeDisposable()
    private var accountBalance: Money? = null
    private var isEnabled: Boolean? = null
    private var interestRate: Double? = null
    private var displayedAccount: CryptoAccount = NullCryptoAccount()

    init {
        LayoutInflater.from(context)
            .inflate(R.layout.view_account_crypto_overview, this, true)
    }

    fun updateAccount(
        account: CryptoAccount,
        onAccountClicked: (CryptoAccount) -> Unit,
        cellDecorator: CellDecorator = DefaultCellDecorator()
    ) {
        compositeDisposable.clear()
        updateView(account, onAccountClicked, cellDecorator)
    }

    private fun updateView(
        account: CryptoAccount,
        onAccountClicked: (CryptoAccount) -> Unit,
        cellDecorator: CellDecorator
    ) {
        val accountsAreTheSame = displayedAccount.isTheSameWith(account)
        updateAccountDetails(account, accountsAreTheSame, onAccountClicked, cellDecorator)

        when (account) {
            is InterestAccount -> setInterestAccountDetails(account, accountsAreTheSame)
            is TradingAccount -> {
                asset_account_icon.visible()
                asset_account_icon.setImageResource(R.drawable.ic_account_badge_custodial)
            }
            is NonCustodialAccount -> asset_account_icon.gone()
            else -> asset_account_icon.gone()
        }
        displayedAccount = account
    }

    private fun setInterestAccountDetails(
        account: CryptoAccount,
        accountsAreTheSame: Boolean
    ) {
        asset_account_icon.setImageResource(R.drawable.ic_account_badge_interest)

        compositeDisposable += coincore[account.asset].interestRate().observeOn(AndroidSchedulers.mainThread())
            .doOnSubscribe { asset_subtitle.text = resources.getString(R.string.empty) }
            .doOnSuccess {
                interestRate = it
            }.startWithValueIfCondition(value = interestRate, condition = accountsAreTheSame)
            .subscribeBy(
                onNext = {
                    asset_subtitle.text = resources.getString(R.string.dashboard_asset_balance_interest, it)
                },
                onError = {
                    asset_subtitle.text = resources.getString(
                        R.string.dashboard_asset_actions_interest_dsc_failed)

                    Timber.e("AssetActions error loading Interest rate: $it")
                }
            )
    }

    private fun updateAccountDetails(
        account: CryptoAccount,
        accountsAreTheSame: Boolean,
        onAccountClicked: (CryptoAccount) -> Unit,
        cellDecorator: CellDecorator
    ) {
        val crypto = account.asset
        wallet_name.text = account.label
        icon.setCoinIcon(crypto)
        icon.visible()

        asset_subtitle.setText(crypto.assetName())

        compositeDisposable += account.accountBalance
            .doOnSuccess {
                accountBalance = it
            }.startWithValueIfCondition(
                value = accountBalance,
                alternativeValue = CryptoValue.zero(account.asset),
                condition = accountsAreTheSame
            )
            .observeOn(AndroidSchedulers.mainThread())
            .doOnSubscribe {
                wallet_balance_crypto.text = ""
                wallet_balance_fiat.text = ""
            }
            .subscribeBy(
                onNext = { accountBalance ->
                    wallet_balance_crypto.text = accountBalance.toStringWithSymbol()
                    wallet_balance_fiat.text =
                        accountBalance.toFiat(
                            exchangeRates,
                            currencyPrefs.selectedFiatCurrency
                        ).toStringWithSymbol()
                },
                onError = {
                    Timber.e("Cannot get balance for ${account.label}")
                }
            )
        compositeDisposable += cellDecorator.view(container.context)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe {
                container.addViewToBottomWithConstraints(
                    view = it,
                    bottomOfView = asset_subtitle,
                    startOfView = asset_subtitle,
                    endOfView = wallet_balance_crypto
                )
            }

        container.alpha = 1f
        compositeDisposable += cellDecorator.isEnabled()
            .doOnSuccess {
                isEnabled = it
            }.startWithValueIfCondition(value = isEnabled, condition = accountsAreTheSame)
            .observeOn(AndroidSchedulers.mainThread())
            .doOnSubscribe {
                setOnClickListener {
                }
            }
            .subscribeBy(
                onNext = { isEnabled ->
                    if (isEnabled) {
                        setOnClickListener {
                            onAccountClicked(account)
                        }
                        container.alpha = 1f
                    } else {
                        container.alpha = .6f
                    }
                }
            )

        container.removePossibleBottomView()
    }

    fun dispose() {
        compositeDisposable.clear()
    }
}

private fun <T> Single<T>.startWithValueIfCondition(
    value: T?,
    alternativeValue: T? = null,
    condition: Boolean
): Observable<T> =
    if (!condition)
        this.toObservable()
    else {
        when {
            value != null -> this.toObservable().startWith(value)
            alternativeValue != null -> this.toObservable().startWith(alternativeValue)
            else -> this.toObservable()
        }
    }