package piuk.blockchain.android.coincore.impl

import androidx.annotation.VisibleForTesting
import com.blockchain.logging.CrashLogger
import com.blockchain.nabu.datamanagers.CustodialWalletManager
import com.blockchain.nabu.datamanagers.EligibilityProvider
import com.blockchain.nabu.service.TierService
import com.blockchain.preferences.CurrencyPrefs
import com.blockchain.wallet.DefaultLabels
import info.blockchain.balance.CryptoCurrency
import info.blockchain.balance.ExchangeRate
import info.blockchain.wallet.prices.TimeInterval
import io.reactivex.Completable
import io.reactivex.Maybe
import io.reactivex.Single
import io.reactivex.rxkotlin.Singles
import piuk.blockchain.android.coincore.AccountGroup
import piuk.blockchain.android.coincore.AssetAction
import piuk.blockchain.android.coincore.AssetFilter
import piuk.blockchain.android.coincore.CryptoAccount
import piuk.blockchain.android.coincore.CryptoAsset
import piuk.blockchain.android.coincore.NonCustodialAccount
import piuk.blockchain.android.coincore.SingleAccount
import piuk.blockchain.android.coincore.SingleAccountList
import piuk.blockchain.android.coincore.TradingAccount
import piuk.blockchain.android.thepit.PitLinking
import piuk.blockchain.androidcore.data.api.EnvironmentConfig
import piuk.blockchain.androidcore.data.exchangerate.ExchangeRateDataManager
import piuk.blockchain.androidcore.data.exchangerate.ExchangeRateService
import piuk.blockchain.androidcore.data.exchangerate.PriceSeries
import piuk.blockchain.androidcore.data.exchangerate.TimeSpan
import piuk.blockchain.androidcore.data.payload.PayloadDataManager
import piuk.blockchain.androidcore.utils.helperfunctions.unsafeLazy
import timber.log.Timber
import java.math.BigDecimal
import java.util.concurrent.atomic.AtomicBoolean

interface AccountRefreshTrigger {
    fun forceAccountsRefresh()
}

internal abstract class CryptoAssetBase(
    protected val payloadManager: PayloadDataManager,
    protected val exchangeRates: ExchangeRateDataManager,
    private val historicRates: ExchangeRateService,
    protected val currencyPrefs: CurrencyPrefs,
    protected val labels: DefaultLabels,
    protected val custodialManager: CustodialWalletManager,
    private val pitLinking: PitLinking,
    protected val crashLogger: CrashLogger,
    private val tiersService: TierService,
    protected val environmentConfig: EnvironmentConfig,
    private val eligibilityProvider: EligibilityProvider,
    protected val offlineAccounts: OfflineAccountUpdater
) : CryptoAsset, AccountRefreshTrigger {

    private val activeAccounts: ActiveAccountList by unsafeLazy {
        ActiveAccountList(asset, custodialManager)
    }

    protected val accounts: Single<SingleAccountList>
        get() = activeAccounts.fetchAccountList(::loadAccounts)

    override val isEnabled: Boolean
        get() = !asset.hasFeature(CryptoCurrency.STUB_ASSET)

    // Init token, set up accounts and fetch a few activities
    override fun init(): Completable =
        initToken()
            .doOnError { throwable ->
                crashLogger.logException(throwable, "Coincore: Failed to load $asset wallet")
            }
            .doOnComplete { Timber.d("Coincore: Init $asset Complete") }
            .doOnError { Timber.d("Coincore: Init $asset Failed") }

    private fun loadAccounts(): Single<SingleAccountList> =
        Singles.zip(
            loadNonCustodialAccounts(labels),
            loadCustodialAccount(),
            loadInterestAccounts()
        ) { nc, c, i ->
            nc + c + i
        }.doOnError {
            val errorMsg = "Error loading accounts for ${asset.networkTicker}"
            Timber.e("$errorMsg: $it")
            crashLogger.logException(it, errorMsg)
        }

    // Called when the set of account in use bu this asset changes. Update the offline
    // cache and the BE notification addresses here
    protected open fun onAccountListChanged(accountList: List<SingleAccount>) {
        Timber.d("Accounts changed!")
    }

    final override fun forceAccountsRefresh() {
        activeAccounts.setForceRefresh()
    }

    abstract fun initToken(): Completable

    abstract fun loadNonCustodialAccounts(labels: DefaultLabels): Single<SingleAccountList>

    private fun loadInterestAccounts(): Single<SingleAccountList> =
        custodialManager.getInterestAvailabilityForAsset(asset)
            .map {
                if (it) {
                    listOf(
                        CryptoInterestAccount(
                            asset,
                            labels.getDefaultInterestWalletLabel(asset),
                            custodialManager,
                            exchangeRates,
                            environmentConfig
                        )
                    )
                } else {
                    emptyList()
                }
            }

    override fun interestRate(): Single<Double> = custodialManager.getInterestAccountRates(asset)

    open fun loadCustodialAccount(): Single<SingleAccountList> =
        Single.just(
            listOf(
                CustodialTradingAccount(
                    asset = asset,
                    label = labels.getDefaultCustodialWalletLabel(asset),
                    exchangeRates = exchangeRates,
                    custodialWalletManager = custodialManager,
                    environmentConfig = environmentConfig,
                    eligibilityProvider = eligibilityProvider
                )
            )
        )

    final override fun accountGroup(filter: AssetFilter): Maybe<AccountGroup> =
        accounts.flatMapMaybe {
            Maybe.fromCallable {
                it.makeAccountGroup(asset, labels, filter)
            }
        }

    final override fun defaultAccount(): Single<SingleAccount> =
        accounts.map { it.first { a -> a.isDefault } }

    private fun getNonCustodialAccountList(): Single<SingleAccountList> =
        accountGroup(filter = AssetFilter.NonCustodial)
            .map { group -> group.accounts.mapNotNull { it as? SingleAccount } }
            .toSingle(emptyList())

    final override fun exchangeRate(): Single<ExchangeRate> =
        exchangeRates.fetchExchangeRate(asset, currencyPrefs.selectedFiatCurrency)
            .map {
                ExchangeRate.CryptoToFiat(
                    asset,
                    currencyPrefs.selectedFiatCurrency,
                    it
                )
            }

    final override fun historicRate(epochWhen: Long): Single<ExchangeRate> =
        exchangeRates.getHistoricPrice(asset, currencyPrefs.selectedFiatCurrency, epochWhen)
            .map {
                ExchangeRate.CryptoToFiat(
                    asset,
                    currencyPrefs.selectedFiatCurrency,
                    it.toBigDecimal()
                )
            }

    override fun historicRateSeries(period: TimeSpan, interval: TimeInterval): Single<PriceSeries> =
        if (asset.hasFeature(CryptoCurrency.PRICE_CHARTING))
            historicRates.getHistoricPriceSeries(asset, currencyPrefs.selectedFiatCurrency, period)
        else
            Single.just(emptyList())

    private fun getPitLinkingTargets(): Maybe<SingleAccountList> =
        pitLinking.isPitLinked().filter { it }
            .flatMap { custodialManager.getExchangeSendAddressFor(asset) }
            .map { address ->
                listOf(
                    CryptoExchangeAccount(
                        asset = asset,
                        label = labels.getDefaultExchangeWalletLabel(asset),
                        address = address,
                        exchangeRates = exchangeRates,
                        environmentConfig = environmentConfig
                    )
                )
            }

    private fun getInterestTargets(): Maybe<SingleAccountList> =
        custodialManager.getInterestEligibilityForAsset(asset).flatMapMaybe { eligibility ->
            if (eligibility.eligible) {
                accounts.flatMapMaybe {
                    Maybe.just(it.filterIsInstance<CryptoInterestAccount>())
                }
            } else {
                Maybe.empty()
            }
        }

    private fun getCustodialTargets(): Maybe<SingleAccountList> =
        accountGroup(AssetFilter.Custodial)
            .map { it.accounts }
            .onErrorComplete()

    private fun getNonCustodialTargets(exclude: SingleAccount? = null): Maybe<SingleAccountList> =
        getNonCustodialAccountList()
            .map { ll ->
                ll.filter { a -> a !== exclude }
            }.flattenAsObservable {
                it
            }.flatMapMaybe { account ->
                account.actions.flatMapMaybe {
                    if (it.contains(AssetAction.Receive)) {
                        Maybe.just(account)
                    } else Maybe.empty()
                }
            }.toList().toMaybe()

    final override fun transactionTargets(account: SingleAccount): Single<SingleAccountList> {
        require(account is CryptoAccount)
        require(account.asset == asset)

        return when (account) {
            is TradingAccount -> getNonCustodialTargets().toSingle(emptyList())
            is NonCustodialAccount ->
                Maybe.concat(
                    listOf(
                        getPitLinkingTargets(),
                        getInterestTargets(),
                        getCustodialTargets(),
                        getNonCustodialTargets(exclude = account)
                    )
                ).toList()
                    .map { ll -> ll.flatten() }
                    .onErrorReturnItem(emptyList())
            else -> Single.just(emptyList())
        }
    }
}

fun ExchangeRateDataManager.fetchExchangeRate(
    cryptoCurrency: CryptoCurrency,
    currencyName: String
): Single<BigDecimal> =
    updateTickers()
        .andThen(Single.defer { Single.just(getLastPrice(cryptoCurrency, currencyName)) })

@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
internal class ActiveAccountList(
    private val asset: CryptoCurrency,
    private val custodialManager: CustodialWalletManager
) {
    private val activeList = mutableSetOf<CryptoAccount>()

    private var interestEnabled = false
    private val forceRefreshOnNext = AtomicBoolean(true)

    fun setForceRefresh() {
        forceRefreshOnNext.set(true)
    }

    fun fetchAccountList(
        loader: () -> Single<SingleAccountList>
    ): Single<SingleAccountList> =
        shouldRefresh().flatMap { refresh ->
            if (refresh) {
                loader().map { updateWith(it) }
            } else {
                Single.just(activeList.toList())
            }
        }

    private fun shouldRefresh() =
        Singles.zip(
            Single.just(interestEnabled),
            custodialManager.getInterestAvailabilityForAsset(asset),
            Single.just(forceRefreshOnNext.getAndSet(false))
        ) { wasEnabled, isEnabled, force ->
            interestEnabled = isEnabled
            wasEnabled != isEnabled || force
        }.onErrorReturn { false }

    @Synchronized
    private fun updateWith(
        accounts: List<SingleAccount>
    ): List<CryptoAccount> {
        val newActives = mutableSetOf<CryptoAccount>()
        accounts.filterIsInstance<CryptoAccount>()
            .forEach { a ->
                val existing = activeList.find { it.matches(a) }
                if (existing != null) {
                    newActives.add(existing)
                } else {
                    newActives.add(a)
                }
            }
        activeList.clear()
        activeList.addAll(newActives)

        return activeList.toList()
    }
}
