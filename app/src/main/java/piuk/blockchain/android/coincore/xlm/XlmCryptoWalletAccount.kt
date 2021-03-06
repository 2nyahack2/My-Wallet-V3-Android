package piuk.blockchain.android.coincore.xlm

import com.blockchain.preferences.WalletStatus
import com.blockchain.sunriver.XlmDataManager
import com.blockchain.sunriver.XlmFeesFetcher
import com.blockchain.nabu.datamanagers.CustodialWalletManager
import info.blockchain.balance.AccountReference
import info.blockchain.balance.CryptoCurrency
import info.blockchain.balance.CryptoValue
import info.blockchain.balance.Money
import io.reactivex.Single
import piuk.blockchain.android.coincore.ActivitySummaryItem
import piuk.blockchain.android.coincore.ActivitySummaryList
import piuk.blockchain.android.coincore.ReceiveAddress
import piuk.blockchain.android.coincore.TxEngine
import piuk.blockchain.android.coincore.impl.CryptoNonCustodialAccount
import piuk.blockchain.androidcore.data.exchangerate.ExchangeRateDataManager
import piuk.blockchain.androidcore.data.payload.PayloadDataManager
import piuk.blockchain.androidcore.data.walletoptions.WalletOptionsDataManager
import piuk.blockchain.androidcore.utils.extensions.mapList
import java.util.concurrent.atomic.AtomicBoolean

internal class XlmCryptoWalletAccount(
    payloadManager: PayloadDataManager,
    override val label: String = "",
    internal val address: String,
    private val xlmManager: XlmDataManager,
    override val exchangeRates: ExchangeRateDataManager,
    private val xlmFeesFetcher: XlmFeesFetcher,
    private val walletOptionsDataManager: WalletOptionsDataManager,
    private val walletPreferences: WalletStatus,
    private val custodialWalletManager: CustodialWalletManager
) : CryptoNonCustodialAccount(payloadManager, CryptoCurrency.XLM) {

    override val isDefault: Boolean = true // Only one account ever, so always default

    private val hasFunds = AtomicBoolean(false)

    override val isFunded: Boolean
        get() = hasFunds.get()

    override val accountBalance: Single<Money>
        get() = xlmManager.getBalance()
            .doOnSuccess {
                hasFunds.set(it > CryptoValue.zero(asset))
            }
            .map { it as Money }

    override val actionableBalance: Single<Money>
        get() = xlmManager.getBalanceAndMin().map {
            it.balance - it.minimumBalance
        }

    override val receiveAddress: Single<ReceiveAddress>
        get() = Single.just(
            XlmAddress(_address = address, _label = label)
        )

    override val activity: Single<ActivitySummaryList>
        get() = xlmManager.getTransactionList()
            .onErrorResumeNext { Single.just(emptyList()) }
            .mapList {
                XlmActivitySummaryItem(
                    it,
                    exchangeRates,
                    account = this
                ) as ActivitySummaryItem
            }.flatMap {
                appendTradeActivity(custodialWalletManager, asset, it)
            }.doOnSuccess { setHasTransactions(it.isNotEmpty()) }

    override fun createTxEngine(): TxEngine =
        XlmOnChainTxEngine(
            xlmDataManager = xlmManager,
            xlmFeesFetcher = xlmFeesFetcher,
            walletOptionsDataManager = walletOptionsDataManager,
            requireSecondPassword = payloadDataManager.isDoubleEncrypted,
            walletPreferences = walletPreferences
        )

    constructor(
        payloadManager: PayloadDataManager,
        account: AccountReference.Xlm,
        xlmManager: XlmDataManager,
        exchangeRates: ExchangeRateDataManager,
        xlmFeesFetcher: XlmFeesFetcher,
        walletOptionsDataManager: WalletOptionsDataManager,
        walletPreferences: WalletStatus,
        custodialWalletManager: CustodialWalletManager
    ) : this(
        payloadManager = payloadManager,
        label = account.label,
        address = account.accountId,
        xlmManager = xlmManager,
        exchangeRates = exchangeRates,
        xlmFeesFetcher = xlmFeesFetcher,
        walletOptionsDataManager = walletOptionsDataManager,
        walletPreferences = walletPreferences,
        custodialWalletManager = custodialWalletManager
    )
}
