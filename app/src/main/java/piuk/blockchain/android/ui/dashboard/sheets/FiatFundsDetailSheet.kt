package piuk.blockchain.android.ui.dashboard.sheets

import android.content.Context
import android.view.View
import android.widget.TextView
import android.widget.Toast
import com.blockchain.koin.scopedInject
import com.blockchain.preferences.CurrencyPrefs
import com.blockchain.nabu.models.responses.nabu.KycTierLevel
import com.blockchain.nabu.service.TierService
import info.blockchain.balance.ExchangeRates
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.Singles
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.rxkotlin.subscribeBy
import kotlinx.android.synthetic.main.dialog_sheet_fiat_funds_detail.view.*
import kotlinx.android.synthetic.main.item_dashboard_funds.view.*
import piuk.blockchain.android.R
import piuk.blockchain.android.coincore.AssetAction
import piuk.blockchain.android.coincore.BlockchainAccount
import piuk.blockchain.android.coincore.FiatAccount
import piuk.blockchain.android.coincore.NullFiatAccount
import piuk.blockchain.android.ui.base.SlidingModalBottomDialog
import piuk.blockchain.androidcoreui.ui.customviews.ToastCustom
import piuk.blockchain.android.util.gone
import piuk.blockchain.android.util.visibleIf
import timber.log.Timber

class FiatFundsDetailSheet : SlidingModalBottomDialog() {

    interface Host : SlidingModalBottomDialog.Host {
        fun depositFiat(account: FiatAccount)
        fun gotoActivityFor(account: BlockchainAccount)
        fun withdrawFiat(currency: String)
        fun showFundsKyc()
    }

    override val host: Host by lazy {
        super.host as? Host ?: throw IllegalStateException(
            "Host fragment is not a FiatFundsDetailSheet.Host")
    }

    private val prefs: CurrencyPrefs by scopedInject()
    private val exchangeRates: ExchangeRates by scopedInject()
    private val tierService: TierService by scopedInject()
    private val disposables = CompositeDisposable()

    private var account: FiatAccount = NullFiatAccount

    override val layoutResource: Int
        get() = R.layout.dialog_sheet_fiat_funds_detail

    override fun initControls(view: View) {
        val ticker = account.fiatCurrency
        view.apply {
            funds_title.setStringFromTicker(context, ticker)
            funds_fiat_ticker.text = ticker
            funds_icon.setIcon(ticker)

            funds_balance.gone()
            funds_user_fiat_balance.gone()

            disposables += Singles.zip(
                account.accountBalance,
                account.fiatBalance(prefs.selectedFiatCurrency, exchangeRates),
                account.actions
            ).observeOn(AndroidSchedulers.mainThread()).subscribeBy(
                onSuccess = { (fiatBalance, userFiatBalance, actions) ->
                    funds_user_fiat_balance.visibleIf { prefs.selectedFiatCurrency != ticker }
                    funds_user_fiat_balance.text = userFiatBalance.toStringWithSymbol()

                    funds_balance.text = fiatBalance.toStringWithSymbol()
                    funds_balance.visibleIf { fiatBalance.isZero || fiatBalance.isPositive }
                    funds_withdraw_holder.visibleIf { actions.contains(AssetAction.Withdraw) }
                    funds_deposit_holder.visibleIf { actions.contains(AssetAction.Deposit) }
                    funds_activity_holder.visibleIf { actions.contains(AssetAction.ViewActivity) }
                },
                onError = {
                    Timber.e("Error getting fiat funds balances: $it")
                    showErrorToast()
                }
            )

            disposables += tierService.tiers()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeBy(
                    onSuccess = { tiers ->
                        funds_deposit_holder.setOnClickListener {
                            dismiss()
                            if (!tiers.isApprovedFor(KycTierLevel.GOLD)) {
                                host.showFundsKyc()
                            } else {
                                host.depositFiat(account)
                            }
                        }
                    },
                    onError = {
                        Timber.e("Error getting fiat funds tiers: $it")
                        showErrorToast()
                    }
                )

            funds_withdraw_holder.setOnClickListener {
                dismiss()
                host.withdrawFiat(account.fiatCurrency)
            }

            funds_activity_holder.setOnClickListener {
                dismiss()
                host.gotoActivityFor(account)
            }
        }
    }

    private fun showErrorToast() {
        ToastCustom.makeText(requireContext(), getString(R.string.common_error), Toast.LENGTH_SHORT,
            ToastCustom.TYPE_ERROR)
    }

    companion object {
        fun newInstance(fiatAccount: FiatAccount): FiatFundsDetailSheet {
            return FiatFundsDetailSheet().apply {
                account = fiatAccount
            }
        }
    }

    private fun TextView.setStringFromTicker(context: Context, ticker: String) {
        text = context.getString(
            when (ticker) {
                "EUR" -> R.string.euros
                "GBP" -> R.string.pounds
                "USD" -> R.string.us_dollars
                else -> R.string.empty
            }
        )
    }
}