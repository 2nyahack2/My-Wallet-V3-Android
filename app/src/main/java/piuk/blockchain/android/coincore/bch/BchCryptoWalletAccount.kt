package piuk.blockchain.android.coincore.bch

import com.blockchain.nabu.datamanagers.CustodialWalletManager
import com.blockchain.preferences.WalletStatus
import info.blockchain.balance.CryptoCurrency
import info.blockchain.balance.CryptoValue
import info.blockchain.balance.Money
import info.blockchain.wallet.coin.GenericMetadataAccount
import io.reactivex.Completable
import io.reactivex.Single
import org.bitcoinj.core.Address
import org.bitcoinj.core.NetworkParameters
import piuk.blockchain.android.coincore.ActivitySummaryList
import piuk.blockchain.android.coincore.CryptoAccount
import piuk.blockchain.android.coincore.ReceiveAddress
import piuk.blockchain.android.coincore.TxEngine
import piuk.blockchain.android.coincore.impl.AccountRefreshTrigger
import piuk.blockchain.android.coincore.impl.CryptoNonCustodialAccount
import piuk.blockchain.android.coincore.impl.transactionFetchCount
import piuk.blockchain.android.coincore.impl.transactionFetchOffset
import piuk.blockchain.androidcore.data.bitcoincash.BchDataManager
import piuk.blockchain.androidcore.data.exchangerate.ExchangeRateDataManager
import piuk.blockchain.androidcore.data.fees.FeeDataManager
import piuk.blockchain.androidcore.data.payload.PayloadDataManager
import piuk.blockchain.androidcore.data.payments.SendDataManager
import piuk.blockchain.androidcore.utils.extensions.mapList
import piuk.blockchain.androidcore.utils.extensions.then
import java.util.concurrent.atomic.AtomicBoolean

internal class BchCryptoWalletAccount private constructor(
    payloadManager: PayloadDataManager,
    private val bchManager: BchDataManager,
    // Used to lookup the account in payloadDataManager to fetch receive address
    private val addressIndex: Int,
    override val exchangeRates: ExchangeRateDataManager,
    private val networkParams: NetworkParameters,
    private val feeDataManager: FeeDataManager,
    private val sendDataManager: SendDataManager,
    private val internalAccount: GenericMetadataAccount,
    private val walletPreferences: WalletStatus,
    private val custodialWalletManager: CustodialWalletManager,
    private val refreshTrigger: AccountRefreshTrigger
) : CryptoNonCustodialAccount(payloadManager, CryptoCurrency.BCH) {

    private val hasFunds = AtomicBoolean(false)

    override val label: String
        get() = internalAccount.label

    override val isArchived: Boolean
        get() = internalAccount.isArchived

    override val isDefault: Boolean
        get() = addressIndex == bchManager.getDefaultAccountPosition()

    override val isFunded: Boolean
        get() = hasFunds.get()

    override val accountBalance: Single<Money>
        get() = bchManager.getBalance(xpubAddress)
            .map { CryptoValue.fromMinor(CryptoCurrency.BCH, it) }
            .doOnSuccess {
                hasFunds.set(it > CryptoValue.ZeroBch)
            }
            .map { it }

    override val actionableBalance: Single<Money>
        get() = accountBalance

    override val receiveAddress: Single<ReceiveAddress>
        get() = bchManager.getNextReceiveAddress(
            addressIndex
        ).map {
            val address = Address.fromBase58(networkParams, it)
            address.toCashAddress()
        }.singleOrError()
            .map {
                BchAddress(address_ = it, label = label)
            }

    override val activity: Single<ActivitySummaryList>
        get() = bchManager.getAddressTransactions(
            xpubAddress,
            transactionFetchCount,
            transactionFetchOffset
        ).onErrorReturn { emptyList() }
        .mapList {
            BchActivitySummaryItem(
                it,
                exchangeRates,
                account = this
            )
        }.flatMap {
            appendTradeActivity(custodialWalletManager, asset, it)
        }.doOnSuccess { setHasTransactions(it.isNotEmpty()) }

    override fun createTxEngine(): TxEngine =
        BchOnChainTxEngine(
            feeManager = feeDataManager,
            networkParams = networkParams,
            sendDataManager = sendDataManager,
            bchDataManager = bchManager,
            payloadDataManager = payloadDataManager,
            requireSecondPassword = payloadDataManager.isDoubleEncrypted,
            walletPreferences = walletPreferences
        )

    override fun updateLabel(newLabel: String): Completable {
        require(newLabel.isNotEmpty())
        val revertLabel = label
        internalAccount.label = newLabel
        return bchManager.syncWithServer()
            .doOnError { internalAccount.label = revertLabel }
    }

    override fun archive(): Completable =
        if (!isArchived && !isDefault) {
            toggleArchived()
        } else {
            Completable.error(IllegalStateException("${asset.networkTicker} Account $label cannot be archived"))
        }

    override fun unarchive(): Completable =
        if (isArchived) {
            toggleArchived()
        } else {
            Completable.error(IllegalStateException("${asset.networkTicker} Account $label cannot be unarchived"))
        }

    private fun toggleArchived(): Completable {
        val isArchived = this.isArchived
        internalAccount.isArchived = !isArchived

        return bchManager.syncWithServer()
            .doOnError { internalAccount.isArchived = isArchived } // Revert
            .then { bchManager.updateTransactions() }
    }

    override fun setAsDefault(): Completable {
        require(!isDefault)
        val revertDefault = bchManager.getDefaultAccountPosition()
        bchManager.setDefaultAccountPosition(addressIndex)
        return bchManager.syncWithServer()
            .doOnError { bchManager.setDefaultAccountPosition(revertDefault) }
    }

    override val xpubAddress: String
        get() = internalAccount.xpub

    override fun matches(other: CryptoAccount): Boolean =
        other is BchCryptoWalletAccount && other.xpubAddress == xpubAddress

    fun getReceiveAddressAtPosition(position: Int) =
        bchManager.getReceiveAddressAtPosition(addressIndex, position)

    internal fun forceRefresh() {
        refreshTrigger.forceAccountsRefresh()
    }

    companion object {
        fun createBchAccount(
            payloadManager: PayloadDataManager,
            jsonAccount: GenericMetadataAccount,
            bchManager: BchDataManager,
            addressIndex: Int,
            exchangeRates: ExchangeRateDataManager,
            networkParams: NetworkParameters,
            feeDataManager: FeeDataManager,
            sendDataManager: SendDataManager,
            walletPreferences: WalletStatus,
            custodialWalletManager: CustodialWalletManager,
            refreshTrigger: AccountRefreshTrigger
        ) = BchCryptoWalletAccount(
            payloadManager = payloadManager,
            bchManager = bchManager,
            addressIndex = addressIndex,
            exchangeRates = exchangeRates,
            networkParams = networkParams,
            feeDataManager = feeDataManager,
            sendDataManager = sendDataManager,
            internalAccount = jsonAccount,
            walletPreferences = walletPreferences,
            custodialWalletManager = custodialWalletManager,
            refreshTrigger = refreshTrigger
        )
    }
}
