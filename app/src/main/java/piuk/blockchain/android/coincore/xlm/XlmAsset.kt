package piuk.blockchain.android.coincore.xlm

import com.blockchain.logging.CrashLogger
import com.blockchain.preferences.CurrencyPrefs
import com.blockchain.preferences.WalletStatus
import com.blockchain.sunriver.StellarPayment
import com.blockchain.sunriver.XlmDataManager
import com.blockchain.sunriver.XlmFeesFetcher
import com.blockchain.sunriver.fromStellarUri
import com.blockchain.sunriver.isValidXlmQr
import com.blockchain.nabu.datamanagers.CustodialWalletManager
import com.blockchain.nabu.datamanagers.EligibilityProvider
import com.blockchain.nabu.service.TierService
import com.blockchain.wallet.DefaultLabels
import info.blockchain.balance.CryptoCurrency
import io.reactivex.Completable
import io.reactivex.Maybe
import io.reactivex.Single
import piuk.blockchain.android.coincore.CryptoAddress
import piuk.blockchain.android.coincore.CachedAddress
import piuk.blockchain.android.coincore.ReceiveAddress
import piuk.blockchain.android.coincore.SingleAccountList
import piuk.blockchain.android.coincore.TxResult
import piuk.blockchain.android.coincore.impl.CryptoAssetBase
import piuk.blockchain.android.thepit.PitLinking
import piuk.blockchain.androidcore.data.api.EnvironmentConfig
import piuk.blockchain.androidcore.data.exchangerate.ExchangeRateDataManager
import piuk.blockchain.androidcore.data.exchangerate.ExchangeRateService
import piuk.blockchain.androidcore.data.payload.PayloadDataManager
import piuk.blockchain.androidcore.data.walletoptions.WalletOptionsDataManager
import piuk.blockchain.android.coincore.SimpleOfflineCacheItem
import piuk.blockchain.android.coincore.impl.OfflineAccountUpdater

internal class XlmAsset(
    payloadManager: PayloadDataManager,
    private val xlmDataManager: XlmDataManager,
    private val xlmFeesFetcher: XlmFeesFetcher,
    private val walletOptionsDataManager: WalletOptionsDataManager,
    custodialManager: CustodialWalletManager,
    exchangeRates: ExchangeRateDataManager,
    historicRates: ExchangeRateService,
    currencyPrefs: CurrencyPrefs,
    labels: DefaultLabels,
    pitLinking: PitLinking,
    crashLogger: CrashLogger,
    tiersService: TierService,
    environmentConfig: EnvironmentConfig,
    private val walletPreferences: WalletStatus,
    eligibilityProvider: EligibilityProvider,
    offlineAccounts: OfflineAccountUpdater
) : CryptoAssetBase(
    payloadManager,
    exchangeRates,
    historicRates,
    currencyPrefs,
    labels,
    custodialManager,
    pitLinking,
    crashLogger,
    tiersService,
    environmentConfig,
    eligibilityProvider,
    offlineAccounts
) {

    override val asset: CryptoCurrency
        get() = CryptoCurrency.XLM

    override fun initToken(): Completable =
        Completable.complete()

    override fun loadNonCustodialAccounts(labels: DefaultLabels): Single<SingleAccountList> =
        xlmDataManager.defaultAccount()
            .map {
                XlmCryptoWalletAccount(
                    payloadManager = payloadManager,
                    account = it,
                    xlmManager = xlmDataManager,
                    exchangeRates = exchangeRates,
                    xlmFeesFetcher = xlmFeesFetcher,
                    walletOptionsDataManager = walletOptionsDataManager,
                    walletPreferences = walletPreferences,
                    custodialWalletManager = custodialManager
                )
            }.doOnSuccess {
                updateOfflineCache(it)
            }.map {
                listOf(it)
            }

    private fun updateOfflineCache(account: XlmCryptoWalletAccount) {
        offlineAccounts.updateOfflineAddresses(
            Single.just(
                SimpleOfflineCacheItem(
                    networkTicker = CryptoCurrency.XLM.networkTicker,
                    accountLabel = account.label,
                    address = CachedAddress(
                        account.address,
                        xlmAddressToUri(account.address)
                    )
                )
            )
        )
    }

    override fun parseAddress(address: String): Maybe<ReceiveAddress> =
        Maybe.fromCallable {
            if (address.isValidXlmQr()) {
                val payment = address.fromStellarUri()
                XlmAddress(
                    _address = payment.public.accountId,
                    stellarPayment = payment
                )
            } else {
                if (isValidAddress(address)) {
                    XlmAddress(address)
                } else {
                    null
                }
            }
        }

    override fun isValidAddress(address: String): Boolean =
        xlmDataManager.isAddressValid(address)
}

internal class XlmAddress(
    _address: String,
    _label: String? = null,
    val stellarPayment: StellarPayment? = null,
    override val onTxCompleted: (TxResult) -> Completable = { Completable.complete() }
) : CryptoAddress {

    override val label: String
    val memo: String?
    override val address: String

    init {
        val parts = _address.split(":")
        address = parts[0]
        label = _label ?: address
        memo = parts.takeIf { it.size > 1 }?.get(1)
    }

    override val asset: CryptoCurrency = CryptoCurrency.XLM

    override fun equals(other: Any?): Boolean {
        return (other is XlmAddress) &&
                (other.asset == asset && other.address == address && other.label == label)
    }

    override fun hashCode(): Int {
        var result = address.hashCode()
        result = 31 * result + label.hashCode()
        result = 31 * result + (stellarPayment?.hashCode() ?: 0)
        result = 31 * result + asset.hashCode()
        return result
    }
}

fun xlmAddressToUri(accountId: String): String = "web+stellar:pay?destination=" + accountId