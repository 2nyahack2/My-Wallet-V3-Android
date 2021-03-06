package piuk.blockchain.android.coincore.btc

import com.blockchain.logging.CrashLogger
import com.blockchain.preferences.CurrencyPrefs
import com.blockchain.preferences.WalletStatus
import com.blockchain.nabu.datamanagers.CustodialWalletManager
import com.blockchain.nabu.datamanagers.EligibilityProvider
import com.blockchain.nabu.service.TierService
import com.blockchain.wallet.DefaultLabels
import info.blockchain.balance.CryptoCurrency
import info.blockchain.balance.CryptoValue
import info.blockchain.wallet.payload.data.Account
import info.blockchain.wallet.payload.data.LegacyAddress
import info.blockchain.wallet.util.FormatsUtil
import info.blockchain.wallet.util.PrivateKeyFactory
import io.reactivex.Completable
import io.reactivex.Maybe
import io.reactivex.Single
import org.bitcoinj.core.Address
import org.bitcoinj.core.Coin
import org.bitcoinj.core.ECKey
import org.bitcoinj.core.NetworkParameters
import org.bitcoinj.uri.BitcoinURI
import piuk.blockchain.android.coincore.CachedAddress
import piuk.blockchain.android.coincore.CryptoAccount
import piuk.blockchain.android.coincore.CryptoAddress
import piuk.blockchain.android.coincore.ReceiveAddress
import piuk.blockchain.android.coincore.SingleAccountList
import piuk.blockchain.android.coincore.TxResult
import piuk.blockchain.android.coincore.impl.CryptoAssetBase
import piuk.blockchain.android.coincore.impl.OfflineAccountUpdater
import piuk.blockchain.android.data.coinswebsocket.strategy.CoinsWebSocketStrategy
import piuk.blockchain.android.thepit.PitLinking
import piuk.blockchain.androidcore.data.api.EnvironmentConfig
import piuk.blockchain.androidcore.data.exchangerate.ExchangeRateDataManager
import piuk.blockchain.androidcore.data.exchangerate.ExchangeRateService
import piuk.blockchain.androidcore.data.fees.FeeDataManager
import piuk.blockchain.androidcore.data.payload.PayloadDataManager
import piuk.blockchain.androidcore.data.payments.SendDataManager

private const val BTC_URL_PREFIX = "bitcoin:"

internal class BtcAsset(
    payloadManager: PayloadDataManager,
    private val sendDataManager: SendDataManager,
    private val feeDataManager: FeeDataManager,
    private val coinsWebsocket: CoinsWebSocketStrategy,
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
    offlineAccounts: OfflineAccountUpdater,
    eligibilityProvider: EligibilityProvider
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
        get() = CryptoCurrency.BTC

    override fun initToken(): Completable =
        Completable.complete()

    override fun loadNonCustodialAccounts(labels: DefaultLabels): Single<SingleAccountList> =
        Single.fromCallable {
            with(payloadManager) {
                val result = mutableListOf<CryptoAccount>()
                accounts.forEachIndexed { i, account ->
                    val btcAccount = btcAccountFromPayloadAccount(i, account)
                    if (btcAccount.isDefault) {
                        updateOfflineCache(btcAccount)
                    }
                    result.add(btcAccount)
                }

                legacyAddresses.forEach { account ->
                    result.add(btcAccountFromLegacyAccount(account))
                }
                result
            }
        }

    private fun updateOfflineCache(account: BtcCryptoWalletAccount) {
        require(account.isDefault)
        require(!account.isArchived)

        return offlineAccounts.updateOfflineAddresses(
            Single.fromCallable {
                val result = mutableListOf<CachedAddress>()

                for (i in 0 until OFFLINE_CACHE_ITEM_COUNT) {
                    account.getReceiveAddressAtPosition(i)?.let {
                        result += CachedAddress(
                            address = it,
                            addressUri = "$BTC_URL_PREFIX$it"
                        )
                    }
                }
                BtcOfflineAccountItem(
                    accountLabel = account.label,
                    addressList = result
                )
            }
        )
    }

    override fun parseAddress(address: String): Maybe<ReceiveAddress> =
        Maybe.fromCallable {
            val normalisedAddress = address.removePrefix(BTC_URL_PREFIX)
            if (isValidAddress(normalisedAddress)) {
                BtcAddress(
                    address = normalisedAddress,
                    networkParams = environmentConfig.bitcoinNetworkParameters
                )
            } else {
                null
            }
        }

    override fun isValidAddress(address: String): Boolean =
        FormatsUtil.isValidBitcoinAddress(
            environmentConfig.bitcoinNetworkParameters,
            address
        )

    fun createAccount(label: String, secondPassword: String?): Single<BtcCryptoWalletAccount> =
        payloadManager.createNewAccount(label, secondPassword)
            .singleOrError()
            .map { btcAccountFromPayloadAccount(payloadManager.accountCount - 1, it) }
            .doOnSuccess { forceAccountsRefresh() }
            .doOnSuccess { coinsWebsocket.subscribeToXpubBtc(it.xpubAddress) }

    fun importLegacyAddressFromKey(
        keyData: String,
        keyFormat: String,
        keyPassword: String? = null, // Required for BIP38 format keys
        walletSecondPassword: String? = null
    ): Single<BtcCryptoWalletAccount> {
        require(keyData.isNotEmpty())
        require(keyPassword != null || keyFormat != PrivateKeyFactory.BIP38)

        return when (keyFormat) {
            PrivateKeyFactory.BIP38 -> extractBip38Key(keyData, keyPassword!!)
            else -> extractKey(keyData, keyFormat)
        }.map { key ->
            if (!key.hasPrivKey())
                throw Exception()
            key
        }.flatMap { key ->
            payloadManager.addLegacyAddressFromKey(key, walletSecondPassword)
        }.map { legacyAddress ->
            btcAccountFromLegacyAccount(legacyAddress)
        }.doOnSuccess {
            forceAccountsRefresh()
        }.doOnSuccess { btcAccount ->
            coinsWebsocket.subscribeToExtraBtcAddress(btcAccount.xpubAddress)
        }
    }

    private fun extractBip38Key(keyData: String, keyPassword: String): Single<ECKey> =
        payloadManager.getBip38KeyFromImportedData(keyData, keyPassword)

    private fun extractKey(keyData: String, keyFormat: String): Single<ECKey> =
        payloadManager.getKeyFromImportedData(keyFormat, keyData)

    private fun btcAccountFromPayloadAccount(index: Int, payloadAccount: Account): BtcCryptoWalletAccount =
        BtcCryptoWalletAccount.createHdAccount(
            jsonAccount = payloadAccount,
            payloadManager = payloadManager,
            hdAccountIndex = index,
            sendDataManager = sendDataManager,
            feeDataManager = feeDataManager,
            exchangeRates = exchangeRates,
            networkParameters = environmentConfig.bitcoinNetworkParameters,
            walletPreferences = walletPreferences,
            custodialWalletManager = custodialManager,
            refreshTrigger = this
        )

    private fun btcAccountFromLegacyAccount(payloadAccount: LegacyAddress): BtcCryptoWalletAccount =
        BtcCryptoWalletAccount.createLegacyAccount(
            legacyAccount = payloadAccount,
            payloadManager = payloadManager,
            sendDataManager = sendDataManager,
            feeDataManager = feeDataManager,
            exchangeRates = exchangeRates,
            networkParameters = environmentConfig.bitcoinNetworkParameters,
            walletPreferences = walletPreferences,
            custodialWalletManager = custodialManager,
            refreshTrigger = this
        )

    companion object {
        private const val OFFLINE_CACHE_ITEM_COUNT = 5
    }
}

internal class BtcAddress(
    override val address: String,
    override val label: String = address,
    private val networkParams: NetworkParameters,
    override val onTxCompleted: (TxResult) -> Completable = { Completable.complete() }
) : CryptoAddress {
    override val asset: CryptoCurrency = CryptoCurrency.BTC

    override fun toUrl(amount: CryptoValue): String {
        return if (amount.isPositive) {
            BitcoinURI.convertToBitcoinURI(
                Address.fromBase58(networkParams, address),
                Coin.valueOf(amount.toBigInteger().toLong()),
                "",
                ""
            )
        } else {
            return "$BTC_URL_PREFIX$address"
        }
    }
}
