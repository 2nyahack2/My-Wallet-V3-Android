package piuk.blockchain.android.coincore.erc20.dgld

import com.blockchain.logging.CrashLogger
import com.blockchain.preferences.CurrencyPrefs
import com.blockchain.preferences.WalletStatus
import com.blockchain.remoteconfig.FeatureFlag
import com.blockchain.nabu.datamanagers.CustodialWalletManager
import com.blockchain.nabu.datamanagers.EligibilityProvider
import com.blockchain.nabu.service.TierService
import com.blockchain.wallet.DefaultLabels
import info.blockchain.balance.CryptoCurrency
import io.reactivex.Completable
import piuk.blockchain.android.coincore.erc20.Erc20TokensBase
import piuk.blockchain.android.coincore.impl.OfflineAccountUpdater
import piuk.blockchain.android.thepit.PitLinking
import piuk.blockchain.androidcore.data.api.EnvironmentConfig
import piuk.blockchain.androidcore.data.ethereum.EthDataManager
import piuk.blockchain.androidcore.data.exchangerate.ExchangeRateDataManager
import piuk.blockchain.androidcore.data.exchangerate.ExchangeRateService
import piuk.blockchain.androidcore.data.fees.FeeDataManager
import piuk.blockchain.androidcore.data.payload.PayloadDataManager
import java.util.concurrent.atomic.AtomicBoolean

internal class DgldAsset(
    payloadManager: PayloadDataManager,
    ethDataManager: EthDataManager,
    feeDataManager: FeeDataManager,
    custodialManager: CustodialWalletManager,
    exchangeRates: ExchangeRateDataManager,
    historicRates: ExchangeRateService,
    currencyPrefs: CurrencyPrefs,
    labels: DefaultLabels,
    pitLinking: PitLinking,
    crashLogger: CrashLogger,
    tiersService: TierService,
    environmentConfig: EnvironmentConfig,
    eligibilityProvider: EligibilityProvider,
    offlineAccounts: OfflineAccountUpdater,
    walletPreferences: WalletStatus,
    private val wDgldFeatureFlag: FeatureFlag
) : Erc20TokensBase(
    CryptoCurrency.DGLD,
    payloadManager,
    ethDataManager,
    feeDataManager,
    walletPreferences,
    custodialManager,
    exchangeRates,
    historicRates,
    currencyPrefs,
    labels,
    pitLinking,
    crashLogger,
    tiersService,
    environmentConfig,
    eligibilityProvider,
    offlineAccounts
) {
    private val isDgldFeatureFlagEnabled = AtomicBoolean(false)

    override fun initToken(): Completable {
        return wDgldFeatureFlag.enabled.doOnSuccess {
            isDgldFeatureFlagEnabled.set(it)
        }.flatMapCompletable {
            super.initToken()
        }
    }

    override val isEnabled: Boolean
        get() = isDgldFeatureFlagEnabled.get()
}
