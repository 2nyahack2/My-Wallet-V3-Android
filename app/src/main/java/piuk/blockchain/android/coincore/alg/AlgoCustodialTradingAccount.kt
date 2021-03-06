package piuk.blockchain.android.coincore.alg

import com.blockchain.nabu.datamanagers.CustodialWalletManager
import com.blockchain.nabu.datamanagers.EligibilityProvider
import info.blockchain.balance.CryptoCurrency
import io.reactivex.Single
import piuk.blockchain.android.coincore.AssetAction
import piuk.blockchain.android.coincore.AvailableActions
import piuk.blockchain.android.coincore.impl.CustodialTradingAccount
import piuk.blockchain.androidcore.data.api.EnvironmentConfig
import piuk.blockchain.androidcore.data.exchangerate.ExchangeRateDataManager

class AlgoCustodialTradingAccount(
    cryptoCurrency: CryptoCurrency,
    label: String,
    exchangeRates: ExchangeRateDataManager,
    custodialWalletManager: CustodialWalletManager,
    environmentConfig: EnvironmentConfig,
    eligibilityProvider: EligibilityProvider
) : CustodialTradingAccount(
    asset = cryptoCurrency,
    label = label,
    exchangeRates = exchangeRates,
    custodialWalletManager = custodialWalletManager,
    environmentConfig = environmentConfig,
    eligibilityProvider = eligibilityProvider
) {
    override val actions: Single<AvailableActions>
        get() = super.actions.map {
            it.toMutableSet().apply {
                remove(AssetAction.Send)
            }.toSet()
        }
}