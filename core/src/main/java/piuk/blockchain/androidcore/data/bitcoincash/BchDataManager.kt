package piuk.blockchain.androidcore.data.bitcoincash

import androidx.annotation.VisibleForTesting
import com.blockchain.wallet.DefaultLabels
import info.blockchain.api.blockexplorer.BlockExplorer
import info.blockchain.api.data.UnspentOutput
import info.blockchain.balance.CryptoCurrency
import info.blockchain.balance.CryptoValue
import info.blockchain.wallet.BitcoinCashWallet
import info.blockchain.wallet.coin.GenericMetadataAccount
import info.blockchain.wallet.coin.GenericMetadataWallet
import info.blockchain.wallet.crypto.DeterministicAccount
import info.blockchain.wallet.multiaddress.TransactionSummary
import info.blockchain.wallet.payload.data.isArchived
import io.reactivex.Completable
import io.reactivex.Maybe
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import org.bitcoinj.core.Address
import piuk.blockchain.androidcore.data.api.EnvironmentConfig
import piuk.blockchain.androidcore.data.metadata.MetadataManager
import piuk.blockchain.androidcore.data.payload.PayloadDataManager
import piuk.blockchain.androidcore.data.rxjava.RxBus
import piuk.blockchain.androidcore.data.rxjava.RxPinning
import piuk.blockchain.androidcore.utils.annotations.WebRequest
import piuk.blockchain.androidcore.utils.extensions.applySchedulers
import piuk.blockchain.androidcore.utils.extensions.then
import timber.log.Timber
import java.math.BigInteger

class BchDataManager(
    private val payloadDataManager: PayloadDataManager,
    private val bchDataStore: BchDataStore,
    private val environmentSettings: EnvironmentConfig,
    private val blockExplorer: BlockExplorer,
    private val defaultLabels: DefaultLabels,
    private val metadataManager: MetadataManager,
    rxBus: RxBus
) {

    private val rxPinning = RxPinning(rxBus)

    /**
     * Clears the currently stored BCH wallet from memory.
     */
    fun clearAccountDetails() {
        bchDataStore.clearData()
    }

    /**
     * Fetches EthereumWallet stored in metadata. If metadata entry doesn't exists it will be created.
     *
     * @param defaultLabel The ETH address default label to be used if metadata entry doesn't exist
     * @return An [Completable]
     */
    fun initBchWallet(defaultLabel: String): Completable =
        fetchMetadata(defaultLabel, payloadDataManager.accounts.size).map { wallet ->
            MetadataPair(
                wallet,
                false
            )
        }
            .defaultIfEmpty(
                MetadataPair(
                    createMetadata(defaultLabel, payloadDataManager.accounts.size),
                    true
                ))
            .doOnSuccess { (metadata, _) ->
                bchDataStore.bchMetadata = metadata
                restoreBchWallet(metadata)
            }
            .flatMapCompletable { (_, needsSave) ->
                val saveToMetadataCompletable = if (needsSave) {
                    metadataManager.saveToMetadata(
                        bchDataStore.bchMetadata!!.toJson(),
                        BitcoinCashWallet.METADATA_TYPE_EXTERNAL
                    )
                } else {
                    Completable.complete()
                }
                saveToMetadataCompletable.then {
                    if (correctBtcOffsetIfNeed()) {
                        payloadDataManager.syncPayloadWithServer()
                    } else
                        Completable.complete()
                }
            }
            .subscribeOn(Schedulers.io())

    @Deprecated("Use syncWithServer")
    fun serializeForSaving(): String = bchDataStore.bchMetadata!!.toJson()

    fun syncWithServer(): Completable =
        metadataManager.saveToMetadata(
            bchDataStore.bchMetadata!!.toJson(),
            BitcoinCashWallet.METADATA_TYPE_EXTERNAL
        )

    fun updateTransactions() =
        Completable.fromObservable(getWalletTransactions(50, 50))

    @VisibleForTesting
    internal fun fetchMetadata(
        defaultLabel: String,
        accountTotal: Int
    ): Maybe<GenericMetadataWallet> =
        metadataManager.fetchMetadata(BitcoinCashWallet.METADATA_TYPE_EXTERNAL)
            .map { walletJson ->
                // Fetch wallet
                val metaData = GenericMetadataWallet.fromJson(walletJson)

                // Sanity check (Add missing metadata accounts)
                metaData?.accounts?.run {
                    val bchAccounts = getMetadataAccounts(defaultLabel, size, accountTotal)
                    addAll(bchAccounts)
                }
                if (bchDataStore.bchMetadata == null || !listContentEquals(bchDataStore.bchMetadata!!.accounts,
                        metaData.accounts)
                ) {
                    bchDataStore.bchMetadata = metaData
                }
                metaData
            }

    @VisibleForTesting
    internal fun createMetadata(defaultLabel: String, accountTotal: Int): GenericMetadataWallet {
        val bchAccounts = getMetadataAccounts(defaultLabel, 0, accountTotal)

        return GenericMetadataWallet().apply {
            accounts = bchAccounts
            isHasSeen = true
        }
    }

    private fun listContentEquals(
        listA: MutableList<GenericMetadataAccount>,
        listB: MutableList<GenericMetadataAccount>
    ): Boolean {

        listA.forEach { accountA ->
            val filteredItems = listB.filter { accountB ->
                (accountB.label == accountA.label) && (accountB.isArchived == accountA.isArchived)
            }

            if (filteredItems.isEmpty()) {
                return false
            }
        }

        return true
    }

    private fun getMetadataAccounts(
        defaultLabel: String,
        startingAccountIndex: Int,
        accountTotal: Int
    ): ArrayList<GenericMetadataAccount> {
        val bchAccounts = arrayListOf<GenericMetadataAccount>()
        ((startingAccountIndex + 1)..accountTotal)
            .map {
                return@map when (it) {
                    in 2..accountTotal -> "$defaultLabel $it"
                    else -> defaultLabel
                }
            }
            .forEach { bchAccounts.add(GenericMetadataAccount(it, false)) }

        return bchAccounts
    }

    /**
     * Restore bitcoin cash wallet
     */
    @VisibleForTesting
    internal fun restoreBchWallet(walletMetadata: GenericMetadataWallet) {
        if (!payloadDataManager.isDoubleEncrypted) {
            bchDataStore.bchWallet = BitcoinCashWallet.restore(
                blockExplorer,
                environmentSettings.bitcoinCashNetworkParameters,
                BitcoinCashWallet.BITCOIN_COIN_PATH,
                payloadDataManager.mnemonic,
                ""
            )

            // BCH Metadata does not store xpub - get from btc wallet since PATH is the same
            payloadDataManager.accounts.forEachIndexed { i, account ->
                bchDataStore.bchWallet?.addAccount()
                walletMetadata.accounts[i].xpub = account.xpub
            }
        } else {

            bchDataStore.bchWallet = BitcoinCashWallet.createWatchOnly(
                blockExplorer,
                environmentSettings.bitcoinCashNetworkParameters
            )

            // NB! A watch-only account xpub != account xpub, they do however derive the same addresses.
            // Only use this [DeterministicAccount] to derive receive/change addresses. Don't use xpub as multiaddr etc parameter.
            payloadDataManager.accounts.forEachIndexed { i, account ->
                bchDataStore.bchWallet?.addWatchOnlyAccount(account.xpub)
                walletMetadata.accounts[i].xpub = account.xpub
            }
        }
    }

    /**
     * Create more btc accounts to catch up to BCH stored in metadata if required.
     *
     * BCH metadata might have more accounts than a restored BTC wallet. When a BTC wallet is restored
     * from mnemonic we will only look ahead 5 accounts to see if the account contains any transactions.
     *
     * @return Boolean value to indicate if bitcoin wallet payload needs to sync to the server
     */
    fun correctBtcOffsetIfNeed(): Boolean {
        val startingAccountIndex = payloadDataManager.accounts.size
        val bchAccountSize = bchDataStore.bchMetadata?.accounts?.size ?: 0
        val difference = bchAccountSize.minus(startingAccountIndex)

        if (difference > 0) {
            (startingAccountIndex until bchAccountSize)
                .forEach {
                    val accountNumber = it + 1

                    val label = defaultLabels.getDefaultNonCustodialWalletLabel(CryptoCurrency.BTC)
                    val newAccountLabel = "$label $accountNumber"
                    val acc =
                        payloadDataManager.wallet!!.hdWallets[0].addAccount(newAccountLabel)

                    bchDataStore.bchMetadata!!.accounts[it].apply {
                        this.xpub = acc.xpub
                    }
                }
        }

        return difference > 0
    }

    /**
     * Restore bitcoin cash wallet from mnemonic.
     */
    fun decryptWatchOnlyWallet(mnemonic: List<String>) {

        bchDataStore.bchWallet = BitcoinCashWallet.restore(
            blockExplorer,
            environmentSettings.bitcoinCashNetworkParameters,
            BitcoinCashWallet.BITCOIN_COIN_PATH,
            mnemonic,
            ""
        )

        payloadDataManager.accounts.forEachIndexed { i, account ->
            bchDataStore.bchWallet?.addAccount()
            bchDataStore.bchMetadata!!.accounts[i].xpub = account.xpub
        }
    }

    /**
     * Adds a [GenericMetadataAccount] to the BCH wallet. The wallet will have to be saved at this
     * point. This assumes that a new [info.blockchain.wallet.payload.data.Account] has already
     * been added to the user's Payload, otherwise xPubs could get out of sync.
     */
    fun createAccount(bitcoinXpub: String) {
        if (bchDataStore.bchWallet!!.isWatchOnly) {
            bchDataStore.bchWallet!!.addWatchOnlyAccount(bitcoinXpub)
        } else {
            bchDataStore.bchWallet!!.addAccount()
        }

        val defaultLabel = defaultLabels.getDefaultNonCustodialWalletLabel(CryptoCurrency.BCH)
        val count = bchDataStore.bchWallet!!.accountTotal
        bchDataStore.bchMetadata!!.addAccount(
            GenericMetadataAccount(
                """$defaultLabel $count""",
                false
            ).apply { xpub = bitcoinXpub }
        )
    }

    fun getActiveXpubs(): List<String> =
        bchDataStore.bchMetadata?.accounts?.filterNot { it.isArchived }?.map { it.xpub }
            ?: emptyList()

    private fun getActiveXpubsAndImportedAddresses(): List<String> =
        getActiveXpubs() + getLegacyAddressStringList()

    fun getLegacyAddressStringList(): List<String> = payloadDataManager.legacyAddressStringList

    fun updateAllBalances(): Completable {
        val legacyAddresses = payloadDataManager.legacyAddresses
            .filterNot { it.isArchived }
            .map { it.address }
            .toSet()

        val xpubs = getActiveXpubs().toSet()

        return rxPinning.call {
            bchDataStore.bchWallet!!.updateAllBalances(xpubs, legacyAddresses)
        }
    }

    fun getAddressBalance(address: String): CryptoValue =
        CryptoValue(CryptoCurrency.BCH, bchDataStore.bchBalances[address] ?: BigInteger.ZERO)

    private fun updateBalanceForAddress(address: String, balance: BigInteger) {
        bchDataStore.bchBalances[address] = balance
    }

    fun getBalance(address: String): Single<BigInteger> =
        payloadDataManager.getBalanceOfBchAddresses(listOf(address))
            .map { it[address]!!.finalBalance }
            .doOnError(Timber::e)
            .singleOrError()
            .doOnSuccess { updateBalanceForAddress(address, it) }
            .onErrorReturn { BigInteger.ZERO }

    fun getAddressTransactions(
        address: String,
        limit: Int,
        offset: Int
    ): Observable<List<TransactionSummary>> =
        rxPinning.call<List<TransactionSummary>> {
            Observable.fromCallable { fetchAddressTransactions(address, limit, offset) }
        }.applySchedulers()

    fun getWalletTransactions(limit: Int = 50, offset: Int = 0): Observable<List<TransactionSummary>> =
        rxPinning.call<List<TransactionSummary>> {
            Observable.fromCallable { fetchWalletTransactions(limit, offset) }
        }.applySchedulers()

    /**
     * Returns all non-archived accounts
     * @return Generic account data that contains label and xpub/address
     */
    fun getActiveAccounts(): List<GenericMetadataAccount> {
        return getAccountMetadataList().filterNot { it.isArchived }
    }

    fun getAccountMetadataList(): List<GenericMetadataAccount> =
        bchDataStore.bchMetadata?.accounts ?: emptyList()

    fun getAccountList(): List<DeterministicAccount> = bchDataStore.bchWallet!!.accounts

    fun getDefaultAccountPosition(): Int = bchDataStore.bchMetadata?.defaultAcccountIdx ?: 0

    fun setDefaultAccountPosition(position: Int) {
        bchDataStore.bchMetadata!!.defaultAcccountIdx = position
    }

    fun getDefaultGenericMetadataAccount(): GenericMetadataAccount? =
        getAccountMetadataList().elementAtOrNull(getDefaultAccountPosition())

    /**
     * Allows you to generate a BCH receive address at an arbitrary number of positions on the chain
     * from the next valid unused address. For example, the passing 5 as the position will generate
     * an address which correlates with the next available address + 5 positions.
     *
     * @param accountIndex The index of the [GenericMetadataAccount] you wish to generate an address from
     * @param addressIndex Represents how many positions on the chain beyond what is already used that
     * you wish to generate
     * @return A Bitcoin Cash receive address in Base58 format
     */
    fun getReceiveAddressAtPosition(accountIndex: Int, addressIndex: Int): String? =
        bchDataStore.bchWallet?.getReceiveAddressAtPosition(accountIndex, addressIndex)

    fun getNextReceiveAddress(accountIndex: Int): Observable<String> = Observable.fromCallable {
        bchDataStore.bchWallet!!.getNextReceiveAddress(accountIndex)
    }

    /**
     * Generates a Base58 Bitcoin Cash receive address for an account at a given position and then formats this address
     * to CashAddress
     * Example: 14yYiZ5kzWhzSr6UKbWe6AKi46SfxvYneb --> bitcoincash:qq4e5fv3mdapcmhr9rm290ywzeu94288svt60rh64g
     *
     * @param accountIndex The index of the [DeterministicAccount] you wish to generate an address from
     * @return A Bitcoin Cash receive address in Base58 format
     */
    fun getNextCashReceiveAddress(accountIndex: Int): Observable<String> =
        Observable.fromCallable {
            bchDataStore.bchWallet!!.getNextReceiveAddress(accountIndex)
        }.map {
            val address = Address.fromBase58(environmentSettings.bitcoinCashNetworkParameters, it)
            address.toCashAddress()
        }

    /**
     * Generates a bech32 Bitcoin Cash receive address for an account at a given position. The
     * address returned will be the next unused in the chain.
     *
     * @param accountIndex The index of the [DeterministicAccount] you wish to generate an address from
     * @return A Bitcoin Cash receive address in bech32 format
     */
    fun getNextReceiveCashAddress(accountIndex: Int): Observable<String> =
        Observable.fromCallable {
            bchDataStore.bchWallet!!.getNextReceiveCashAddress(accountIndex)
        }

    /**
     * Generates a Base58 Bitcoin Cash change address for an account at a given position. The
     * address returned will be the next unused in the chain.
     *
     * @param accountIndex The index of the [DeterministicAccount] you wish to generate an address from
     * @return A Bitcoin Cash change address in Base58 format
     */
    fun getNextChangeAddress(accountIndex: Int): Observable<String> =
        Observable.fromCallable {
            bchDataStore.bchWallet!!.getNextChangeAddress(accountIndex)
        }

    /**
     * Generates a bech32 Bitcoin Cash change address for an account at a given position. The
     * address returned will be the next unused in the chain.
     *
     * @param accountIndex The index of the [DeterministicAccount] you wish to generate an address from
     * @return A Bitcoin Cash change address in bech32 format
     */
    fun getNextChangeCashAddress(accountIndex: Int): Observable<String> =
        Observable.fromCallable {
            bchDataStore.bchWallet!!.getNextChangeCashAddress(accountIndex)
        }

    /**
     * Allows you to generate a BCH change address at an arbitrary number of positions on the chain
     * from the next valid unused address. For example, the passing 5 as the position will generate
     * an address which correlates with the next available address + 5 positions.
     *
     * @param accountIndex The index of the [Account] you wish to generate an address from
     * @param addressIndex Represents how many positions on the chain beyond what is already used that
     * you wish to generate
     * @return A Bitcoin Cash change address in Base58 format
     */
    fun getChangeAddressAtPosition(accountIndex: Int, addressIndex: Int): Observable<String> =
        Observable.fromCallable {
            bchDataStore.bchWallet!!.getChangeAddressAtPosition(accountIndex, addressIndex)
        }

    fun incrementNextReceiveAddress(xpub: String): Completable =
        Completable.fromCallable {
            bchDataStore.bchWallet!!.incrementNextReceiveAddress(xpub)
        }

    fun incrementNextChangeAddress(xpub: String): Completable =
        Completable.fromCallable {
            bchDataStore.bchWallet!!.incrementNextChangeAddress(xpub)
        }

    fun isOwnAddress(address: String) = bchDataStore.bchWallet?.isOwnAddress(address) ?: false

    /**
     * Converts any Bitcoin Cash address to a label.
     *
     * @param address Accepts account receive or change chain address, as well as legacy address.
     * @return Account or legacy address label
     */
    fun getLabelFromBchAddress(address: String): String? {
        val xpub = bchDataStore.bchWallet?.getXpubFromAddress(address)

        return bchDataStore.bchMetadata?.accounts?.find { it.xpub == xpub }?.label
    }

// /////////////////////////////////////////////////////////////////////////
// Web requests that require wrapping in Observables
// /////////////////////////////////////////////////////////////////////////

    @WebRequest
    private fun fetchAddressTransactions(
        address: String,
        limit: Int,
        offset: Int
    ): MutableList<TransactionSummary> =
        bchDataStore.bchWallet!!.getTransactions(
            null, // legacy list
            getActiveXpubsAndImportedAddresses(),
            address,
            limit,
            offset
        )

    @WebRequest
    private fun fetchWalletTransactions(limit: Int, offset: Int): MutableList<TransactionSummary> =
        bchDataStore.bchWallet!!.getTransactions(
            null, // legacy list
            getActiveXpubsAndImportedAddresses(),
            null,
            limit,
            offset
        )

    fun getXpubFromAddress(address: String) =
        bchDataStore.bchWallet!!.getXpubFromAddress(address)

    fun getHDKeysForSigning(
        account: DeterministicAccount,
        unspentOutputs: List<UnspentOutput>
    ) = bchDataStore.bchWallet!!.getHDKeysForSigning(account, unspentOutputs)

    fun subtractAmountFromAddressBalance(account: String, amount: BigInteger) =
        bchDataStore.bchWallet!!.subtractAmountFromAddressBalance(account, amount)

    private data class MetadataPair(val metadata: GenericMetadataWallet, val needsSave: Boolean)
}
