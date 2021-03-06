package piuk.blockchain.android.coincore.btc

import com.blockchain.nabu.datamanagers.CustodialWalletManager
import com.blockchain.preferences.WalletStatus
import com.blockchain.serialization.JsonSerializableAccount
import info.blockchain.balance.CryptoCurrency
import info.blockchain.balance.CryptoValue
import info.blockchain.balance.Money
import info.blockchain.wallet.payload.data.Account
import info.blockchain.wallet.payload.data.LegacyAddress
import info.blockchain.wallet.payment.SpendableUnspentOutputs
import io.reactivex.Completable
import io.reactivex.Single
import org.bitcoinj.core.ECKey
import org.bitcoinj.core.NetworkParameters
import piuk.blockchain.android.coincore.ActivitySummaryList
import piuk.blockchain.android.coincore.AssetAction
import piuk.blockchain.android.coincore.AvailableActions
import piuk.blockchain.android.coincore.CryptoAccount
import piuk.blockchain.android.coincore.ReceiveAddress
import piuk.blockchain.android.coincore.TxEngine
import piuk.blockchain.android.coincore.impl.AccountRefreshTrigger
import piuk.blockchain.android.coincore.impl.CryptoNonCustodialAccount
import piuk.blockchain.android.coincore.impl.transactionFetchCount
import piuk.blockchain.android.coincore.impl.transactionFetchOffset
import piuk.blockchain.androidcore.data.exchangerate.ExchangeRateDataManager
import piuk.blockchain.androidcore.data.fees.FeeDataManager
import piuk.blockchain.androidcore.data.payload.PayloadDataManager
import piuk.blockchain.androidcore.data.payments.SendDataManager
import piuk.blockchain.androidcore.utils.extensions.mapList
import piuk.blockchain.androidcore.utils.extensions.then
import java.util.concurrent.atomic.AtomicBoolean

internal class BtcCryptoWalletAccount(
    payloadManager: PayloadDataManager,
    private val sendDataManager: SendDataManager,
    private val feeDataManager: FeeDataManager,
    // Used to lookup the account in payloadDataManager to fetch receive address
    private val hdAccountIndex: Int,
    override val exchangeRates: ExchangeRateDataManager,
    private val networkParameters: NetworkParameters,
    private val internalAccount: JsonSerializableAccount,
    val isHDAccount: Boolean,
    private val walletPreferences: WalletStatus,
    private val custodialWalletManager: CustodialWalletManager,
    private val refreshTrigger: AccountRefreshTrigger
) : CryptoNonCustodialAccount(payloadManager, CryptoCurrency.BTC) {

    private val hasFunds = AtomicBoolean(false)

    override val label: String
        get() = internalAccount.label

    override val isArchived: Boolean
        get() = if (isHDAccount) {
            (internalAccount as Account).isArchived
        } else {
            (internalAccount as LegacyAddress).tag == LegacyAddress.ARCHIVED_ADDRESS
        }

    override val isDefault: Boolean
        get() = isHDAccount && payloadDataManager.defaultAccountIndex == hdAccountIndex

    override val isFunded: Boolean
        get() = hasFunds.get()

    override val accountBalance: Single<Money>
        get() = getAccountBalance(false)

    private fun getAccountBalance(forceRefresh: Boolean): Single<Money> =
        payloadDataManager.getAddressBalanceRefresh(xpubAddress, forceRefresh)
            .doOnSuccess {
                hasFunds.set(it > CryptoValue.zero(asset))
            }
            .map { it }

    override val actionableBalance: Single<Money>
        get() = accountBalance

    override val receiveAddress: Single<ReceiveAddress>
        get() = if (!isHDAccount) {
            Single.error(IllegalStateException("Cannot receive to Legacy Account"))
        } else {
            payloadDataManager.getNextReceiveAddress(
                payloadDataManager.getAccount(hdAccountIndex)
            ).singleOrError()
                .map {
                    BtcAddress(address = it, label = label, networkParams = networkParameters)
                }
        }

    override val activity: Single<ActivitySummaryList>
        get() = payloadDataManager.getAccountTransactions(
            xpubAddress,
            transactionFetchCount,
            transactionFetchOffset
        ).onErrorReturn { emptyList() }
            .mapList {
                BtcActivitySummaryItem(
                    it,
                    payloadDataManager,
                    exchangeRates,
                    this
                )
            }
            .flatMap {
                appendTradeActivity(custodialWalletManager, asset, it)
            }
            .doOnSuccess {
                setHasTransactions(it.isNotEmpty())
            }

    override fun createTxEngine(): TxEngine =
        BtcOnChainTxEngine(
            btcDataManager = payloadDataManager,
            sendDataManager = sendDataManager,
            feeManager = feeDataManager,
            btcNetworkParams = networkParameters,
            requireSecondPassword = payloadDataManager.isDoubleEncrypted,
            walletPreferences = walletPreferences
        )

    override val actions: Single<AvailableActions>
        get() = super.actions.map {
            if (!isHDAccount) {
                it.toMutableSet().apply { remove(AssetAction.Receive) }.toSet()
            } else it
        }

    override fun updateLabel(newLabel: String): Completable {
        require(newLabel.isNotEmpty())
        val revertLabel = label
        internalAccount.label = newLabel
        return payloadDataManager.syncPayloadWithServer()
            .doOnError { internalAccount.label = revertLabel }
    }

    override fun archive(): Completable {
        require(!isArchived)
        require(!isDefault)
        return toggleArchived()
    }

    override fun unarchive(): Completable {
        require(isArchived)
        return toggleArchived()
    }

    private fun toggleArchived(): Completable {
        val isArchived = this.isArchived
        setArchivedBits(!isArchived)

        return payloadDataManager.syncPayloadWithServer()
            .doOnError { setArchivedBits(isArchived) } // Revert
            .then { payloadDataManager.updateAllTransactions() }
            .then { getAccountBalance(true).ignoreElement() }
            .doOnComplete { forceRefresh() }
    }

    private fun setArchivedBits(newIsArchived: Boolean) {
        if (isHDAccount) {
            (internalAccount as Account).isArchived = newIsArchived
        } else {
            with(internalAccount as LegacyAddress) {
                tag = if (newIsArchived) LegacyAddress.ARCHIVED_ADDRESS else LegacyAddress.NORMAL_ADDRESS
            }
        }
    }

    override fun setAsDefault(): Completable {
        require(!isDefault)
        require(isHDAccount)

        val revertDefault = payloadDataManager.defaultAccountIndex
        payloadDataManager.setDefaultIndex(hdAccountIndex)
        return payloadDataManager.syncPayloadWithServer()
            .doOnError { payloadDataManager.setDefaultIndex(revertDefault) }
            .doOnComplete { forceRefresh() }
    }

    override val xpubAddress: String
        get() = when (internalAccount) {
            is Account -> internalAccount.xpub
            is LegacyAddress -> internalAccount.address
            else -> throw java.lang.IllegalStateException("Unknown wallet type")
        }

    fun getSigningKeys(utxo: SpendableUnspentOutputs, secondPassword: String): Single<List<ECKey>> {
        if (isHDAccount) {
            if (payloadDataManager.isDoubleEncrypted) {
                payloadDataManager.decryptHDWallet(secondPassword)
            }

            return Single.just(
                payloadDataManager.getHDKeysForSigning(
                    account = internalAccount as Account,
                    unspentOutputBundle = utxo
                )
            )
        } else {
            val password = if (payloadDataManager.isDoubleEncrypted) secondPassword else null
            return Single.just(
                listOf(
                    payloadDataManager.getAddressECKey(
                        legacyAddress = internalAccount as LegacyAddress,
                        secondPassword = password
                    ) ?: throw IllegalStateException("Private key not found for legacy BTC address"))
            )
        }
    }

    fun getChangeAddress(): Single<String> {
        return if (isHDAccount) {
            payloadDataManager.getNextChangeAddress(internalAccount as Account)
                .singleOrError()
        } else {
            Single.just((internalAccount as LegacyAddress).address)
        }
    }

    fun incrementReceiveAddress() {
        if (isHDAccount) {
            val account = internalAccount as Account
            payloadDataManager.incrementChangeAddress(account)
            payloadDataManager.incrementReceiveAddress(account)
        }
    }

    fun getReceiveAddressAtPosition(position: Int): String? {
        require(isHDAccount)
        return payloadDataManager.getReceiveAddressAtPosition(internalAccount as Account, position)
    }

    override fun matches(other: CryptoAccount): Boolean =
        other is BtcCryptoWalletAccount && other.xpubAddress == xpubAddress

    internal fun forceRefresh() {
        refreshTrigger.forceAccountsRefresh()
    }

    companion object {
        fun createHdAccount(
            jsonAccount: Account,
            payloadManager: PayloadDataManager,
            hdAccountIndex: Int,
            sendDataManager: SendDataManager,
            feeDataManager: FeeDataManager,
            exchangeRates: ExchangeRateDataManager,
            networkParameters: NetworkParameters,
            walletPreferences: WalletStatus,
            custodialWalletManager: CustodialWalletManager,
            refreshTrigger: AccountRefreshTrigger
        ) = BtcCryptoWalletAccount(
            payloadManager = payloadManager,
            hdAccountIndex = hdAccountIndex,
            sendDataManager = sendDataManager,
            feeDataManager = feeDataManager,
            exchangeRates = exchangeRates,
            networkParameters = networkParameters,
            internalAccount = jsonAccount,
            isHDAccount = true,
            walletPreferences = walletPreferences,
            custodialWalletManager = custodialWalletManager,
            refreshTrigger = refreshTrigger
        )

        fun createLegacyAccount(
            legacyAccount: LegacyAddress,
            payloadManager: PayloadDataManager,
            sendDataManager: SendDataManager,
            feeDataManager: FeeDataManager,
            exchangeRates: ExchangeRateDataManager,
            networkParameters: NetworkParameters,
            walletPreferences: WalletStatus,
            custodialWalletManager: CustodialWalletManager,
            refreshTrigger: AccountRefreshTrigger
        ) = BtcCryptoWalletAccount(
            payloadManager = payloadManager,
            hdAccountIndex = LEGACY_ACCOUNT_NO_INDEX,
            sendDataManager = sendDataManager,
            feeDataManager = feeDataManager,
            exchangeRates = exchangeRates,
            networkParameters = networkParameters,
            internalAccount = legacyAccount,
            isHDAccount = false,
            walletPreferences = walletPreferences,
            custodialWalletManager = custodialWalletManager,
            refreshTrigger = refreshTrigger
        )

        private const val LEGACY_ACCOUNT_NO_INDEX = -1
    }
}