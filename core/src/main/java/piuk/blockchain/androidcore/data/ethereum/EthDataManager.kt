package piuk.blockchain.androidcore.data.ethereum

import com.blockchain.logging.LastTxUpdater
import info.blockchain.balance.CryptoCurrency
import info.blockchain.balance.CryptoCurrency.Companion.IS_ERC20
import info.blockchain.balance.CryptoValue
import info.blockchain.wallet.api.Environment
import info.blockchain.wallet.ethereum.Erc20TokenData
import info.blockchain.wallet.ethereum.EthAccountApi
import info.blockchain.wallet.ethereum.EthereumAccount
import info.blockchain.wallet.ethereum.EthereumWallet
import info.blockchain.wallet.ethereum.data.Erc20AddressResponse
import info.blockchain.wallet.ethereum.data.EthAddressResponseMap
import info.blockchain.wallet.ethereum.data.EthLatestBlockNumber
import info.blockchain.wallet.ethereum.data.EthTransaction
import info.blockchain.wallet.ethereum.data.TransactionState
import info.blockchain.wallet.exceptions.HDWalletException
import info.blockchain.wallet.exceptions.InvalidCredentialsException
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import org.bitcoinj.core.ECKey
import org.spongycastle.util.encoders.Hex
import org.web3j.abi.TypeEncoder
import org.web3j.abi.datatypes.Address
import org.web3j.crypto.RawTransaction
import piuk.blockchain.androidcore.data.api.EnvironmentConfig
import piuk.blockchain.androidcore.data.erc20.Erc20DataModel
import piuk.blockchain.androidcore.data.erc20.Erc20Transfer
import piuk.blockchain.androidcore.data.erc20.datastores.Erc20DataStore
import piuk.blockchain.androidcore.data.ethereum.datastores.EthDataStore
import piuk.blockchain.androidcore.data.ethereum.models.CombinedEthModel
import piuk.blockchain.androidcore.data.metadata.MetadataManager
import piuk.blockchain.androidcore.data.payload.PayloadDataManager
import piuk.blockchain.androidcore.data.rxjava.RxBus
import piuk.blockchain.androidcore.data.rxjava.RxPinning
import piuk.blockchain.androidcore.data.walletoptions.WalletOptionsDataManager
import piuk.blockchain.androidcore.utils.extensions.applySchedulers
import timber.log.Timber
import java.math.BigInteger
import java.util.HashMap

class EthDataManager(
    private val payloadDataManager: PayloadDataManager,
    private val ethAccountApi: EthAccountApi,
    private val ethDataStore: EthDataStore,
    private val erc20DataStore: Erc20DataStore,
    private val walletOptionsDataManager: WalletOptionsDataManager,
    private val metadataManager: MetadataManager,
    private val environmentSettings: EnvironmentConfig,
    private val lastTxUpdater: LastTxUpdater,
    rxBus: RxBus
) {

    private val rxPinning = RxPinning(rxBus)

    /**
     * Clears the currently stored ETH account from memory.
     */
    fun clearAccountDetails() {
        ethDataStore.clearData()
        erc20DataStore.clearData()
    }

    /**
     * Returns an [CombinedEthModel] object for a given ETH address as an [Observable]. An
     * [CombinedEthModel] contains a list of transactions associated with the account, as well
     * as a final balance. Calling this function also caches the [CombinedEthModel].
     *
     * @return An [Observable] wrapping an [CombinedEthModel]
     */
    fun fetchEthAddress(): Observable<CombinedEthModel> =
        if (environmentSettings.environment == Environment.TESTNET) {
            // TODO(eth testnet explorer coming soon)
            Observable.just(CombinedEthModel(EthAddressResponseMap()))
                .doOnNext { ethDataStore.ethAddressResponse = null }
        } else {
            rxPinning.call<CombinedEthModel> {
                ethAccountApi.getEthAddress(listOf(ethDataStore.ethWallet!!.account.address))
                    .map(::CombinedEthModel)
                    .doOnNext { ethDataStore.ethAddressResponse = it }
                    .subscribeOn(Schedulers.io())
            }
        }

    fun fetchErc20DataModel(asset: CryptoCurrency): Observable<Erc20DataModel> {
        require(asset.hasFeature(IS_ERC20))
        return getErc20Address(asset)
            .map { Erc20DataModel(it, asset) }
            .doOnNext { erc20DataStore.erc20DataModel[asset] = it }
            .subscribeOn(Schedulers.io())
    }

    fun refreshErc20Model(asset: CryptoCurrency): Completable =
        Completable.fromObservable(fetchErc20DataModel(asset))

    fun getBalance(account: String): Single<BigInteger> =
        ethAccountApi.getEthAddress(listOf(account))
            .map(::CombinedEthModel)
            .map { it.getTotalBalance() }
            .singleOrError()
            .doOnError(Timber::e)
            .onErrorReturn { BigInteger.ZERO }
            .subscribeOn(Schedulers.io())

    fun getErc20Balance(cryptoCurrency: CryptoCurrency): Single<CryptoValue> {
        require(cryptoCurrency.hasFeature(IS_ERC20))

        return getErc20Address(cryptoCurrency)
            .map {
                it.balance
            }.singleOrError()
            .map { CryptoValue.fromMinor(cryptoCurrency, it) }
    }

    private fun getErc20Address(currency: CryptoCurrency): Observable<Erc20AddressResponse> {
        // If the metadata is not yet loaded, ethDataStore.ethWallet will be null.
        // So defer() this call, so that the exception occurs after-subscription, rather than
        // when constructing the Rx chain, so it will can be handled by onError() etc
        return Observable.defer {
            val address = ethDataStore.ethWallet!!.account.address
            val contractAddress = getErc20TokenData(currency).contractAddress
            ethAccountApi.getErc20Address(
                address,
                contractAddress
            )
        }.subscribeOn(Schedulers.io())
    }

    fun getErc20AccountHash(asset: CryptoCurrency): Single<String> =
        erc20DataStore.erc20DataModel[asset]?.let { model ->
            Single.just(model.accountHash)
        } ?: Single.error(IllegalStateException("erc20 token ${asset.networkTicker} uninitialised"))

    fun getErc20Transactions(asset: CryptoCurrency): Observable<List<Erc20Transfer>> =
        erc20DataStore.erc20DataModel[asset]?.let { model ->
            Observable.just(model.transfers)
        } ?: Observable.just(emptyList())

    /**
     * Returns the user's ETH account object if previously fetched.
     *
     * @return A nullable [CombinedEthModel] object
     */
    fun getEthResponseModel(): CombinedEthModel? = ethDataStore.ethAddressResponse

    /**
     * Returns the user's [EthereumWallet] object if previously fetched.
     *
     * @return A nullable [EthereumWallet] object
     */
    fun getEthWallet(): EthereumWallet? = ethDataStore.ethWallet

    fun getEthWalletAddress(): String? = ethDataStore.ethWallet?.account?.address

    fun getDefaultEthAddress(): Single<String?> =
        Single.just(getEthWallet()?.account?.address)

    /**
     * Returns a stream of [EthTransaction] objects associated with a user's ETH address specifically
     * for displaying in the transaction list. These are cached and may be empty if the account
     * hasn't previously been fetched.
     *
     * @return An [Observable] stream of [EthTransaction] objects
     */
    fun getEthTransactions(): Single<List<EthTransaction>> =
        ethDataStore.ethWallet?.account?.address?.let {
            ethAccountApi.getEthTransactions(listOf(it)).applySchedulers()
        } ?: Single.just(emptyList())

    /**
     * Returns whether or not the user's ETH account currently has unconfirmed transactions, and
     * therefore shouldn't be allowed to send funds until confirmation.
     * We compare the last submitted tx hash with the newly created tx hash - if they match it means
     * that the previous tx has not yet been processed.
     *
     * @return An [Observable] wrapping a [Boolean]
     */
    fun isLastTxPending(): Single<Boolean> =
        ethDataStore.ethWallet?.account?.address?.let {
            ethAccountApi.getLastEthTransaction(listOf(it)).map { tx ->
                tx.state.toLocalState() == TransactionState.PENDING
            }.defaultIfEmpty(false).toSingle()
        } ?: Single.just(false)

    /*
    If x time passed and transaction was not successfully mined, the last transaction will be
    deemed dropped and the account will be allowed to create a new transaction.
     */
    private fun isTransactionDropped(lastTxTimestamp: Long) =
        walletOptionsDataManager.getLastEthTransactionFuse()
            .map { System.currentTimeMillis() > lastTxTimestamp + (it * 1000) }

    private fun hasLastTxBeenProcessed(lastTxHash: String) =
        fetchEthAddress().flatMapIterable { it.getTransactions() }
            .filter { list -> list.hash == lastTxHash }
            .toList()
            .flatMapObservable { Observable.just(it.size > 0) }

    /**
     * Returns a [Number] representing the most recently
     * mined block.
     *
     * @return An [Observable] wrapping a [Number]
     */
    fun getLatestBlockNumber(): Single<EthLatestBlockNumber> =
        if (environmentSettings.environment == Environment.TESTNET) {
            // TODO(eth testnet explorer coming soon)
            Single.just(EthLatestBlockNumber())
        } else {
            ethAccountApi.latestBlockNumber.applySchedulers()
        }

    /**
     * Returns true if a given ETH address is associated with an Ethereum contract.
     * This should be used to validate any proposed destination address for
     * funds.
     *
     * @param address The ETH address to be queried
     * @return An [Observable] returning true or false based on the address's contract status
     */
    @Deprecated(message = "Use the Single<> version")
    fun getIfContract(address: String): Observable<Boolean> =
        if (environmentSettings.environment == Environment.TESTNET) {
            // TODO(eth testnet explorer coming soon)
            Observable.just(false)
        } else {
            rxPinning.call<Boolean> {
                ethAccountApi.getIfContract(address)
                    .applySchedulers()
            }
        }

    fun isContractAddress(address: String): Single<Boolean> =
        rxPinning.call<Boolean> {
            ethAccountApi.getIfContract(address)
                .applySchedulers()
        }.singleOrError()

    private fun String.toLocalState() =
        when (this) {
            "PENDING" -> TransactionState.PENDING
            "CONFIRMED" -> TransactionState.CONFIRMED
            "REPLACED" -> TransactionState.REPLACED
            else -> TransactionState.UNKNOWN
        }

    /**
     * Returns the transaction notes for a given transaction hash, or null if not found.
     */
    fun getTransactionNotes(hash: String): String? = ethDataStore.ethWallet?.txNotes?.get(hash)

    /**
     * Puts a given note in the [HashMap] of transaction notes keyed to a transaction hash. This
     * information is then saved in the metadata service.
     *
     * @return A [Completable] object
     */
    fun updateTransactionNotes(hash: String, note: String): Completable =
        ethDataStore.ethWallet?.let {
            it.txNotes[hash] = note
            return@let save()
        } ?: Completable.error { IllegalStateException("ETH Wallet is null") }
            .applySchedulers()

    fun updateErc20TransactionNotes(hash: String, note: String, asset: CryptoCurrency): Completable {
        require(asset.hasFeature(IS_ERC20))

        return rxPinning.call {
            getErc20TokenData(asset).putTxNote(hash, note)
            return@call save()
        }.applySchedulers()
    }

    /**
     * Fetches EthereumWallet stored in metadata. If metadata entry doesn't exists it will be created.
     *
     * @param labelsMap The list of ETH & ERC-20 address default labels to be used if metadata entry doesn't exist
     * @return An [Completable]
     */
    fun initEthereumWallet(
        labelsMap: Map<CryptoCurrency, String>
    ): Completable =
        fetchOrCreateEthereumWallet(labelsMap)
            .flatMapCompletable { (wallet, needsSave) ->
                ethDataStore.ethWallet = wallet
                if (needsSave) {
                    save()
                } else {
                    Completable.complete()
                }
            }

    /**
     * @param gasPriceWei Represents the fee the sender is willing to pay for gas. One unit of gas
     *                 corresponds to the execution of one atomic instruction, i.e. a computational step
     * @param gasLimitGwei Represents the maximum number of computational steps the transaction
     *                 execution is allowed to take
     * @param weiValue The amount of wei to transfer from the sender to the recipient
     */
    fun createEthTransaction(
        nonce: BigInteger,
        to: String,
        gasPriceWei: BigInteger,
        gasLimitGwei: BigInteger,
        weiValue: BigInteger
    ): RawTransaction? = RawTransaction.createEtherTransaction(
        nonce,
        gasPriceWei,
        gasLimitGwei,
        to,
        weiValue
    )

    fun createErc20Transaction(
        nonce: BigInteger,
        to: String,
        contractAddress: String,
        gasPriceWei: BigInteger,
        gasLimitGwei: BigInteger,
        amount: BigInteger
    ): RawTransaction? =
        RawTransaction.createTransaction(
            nonce,
            gasPriceWei,
            gasLimitGwei,
            contractAddress,
            0.toBigInteger(),
            erc20TransferMethod(to, amount)
        )

    private fun erc20TransferMethod(to: String, amount: BigInteger): String {
        val transferMethodHex = "0xa9059cbb"

        return transferMethodHex + TypeEncoder.encode(Address(to)) +
            TypeEncoder.encode(org.web3j.abi.datatypes.generated.Uint256(amount))
    }

    fun erc20ContractAddress(cryptoCurrency: CryptoCurrency): String {
        require(cryptoCurrency.hasFeature(CryptoCurrency.IS_ERC20))
        return getErc20TokenData(cryptoCurrency).contractAddress
    }

    fun getTransaction(hash: String): Observable<EthTransaction> =
        rxPinning.call<EthTransaction> {
            ethAccountApi.getTransaction(hash)
                .applySchedulers()
        }

    fun getNonce(): Single<BigInteger> =
        fetchEthAddress()
            .singleOrError()
            .map {
                it.getNonce()
            }

    @Deprecated("Why pass the key in when we can derive it here? Use the other overload")
    fun signEthTransaction(rawTransaction: RawTransaction, ecKey: ECKey): Observable<ByteArray> =
        Observable.fromCallable {
            ethDataStore.ethWallet!!.account!!.signTransaction(rawTransaction, ecKey)
        }

    fun signEthTransaction(rawTransaction: RawTransaction, secondPassword: String = ""): Single<ByteArray> =
        Single.fromCallable {
            if (payloadDataManager.isDoubleEncrypted) {
                payloadDataManager.decryptHDWallet(secondPassword)
            }
            val ecKey = EthereumAccount.deriveECKey(payloadDataManager.masterKey, 0)
            ethDataStore.ethWallet!!.account!!.signTransaction(rawTransaction, ecKey)
        }

    fun pushEthTx(signedTxBytes: ByteArray): Observable<String> =
        if (environmentSettings.environment == Environment.TESTNET) {
            Observable.error(NotImplementedError("ETH Testnet not implemented"))
        } else {
            rxPinning.call<String> {
                ethAccountApi.pushTx("0x" + String(Hex.encode(signedTxBytes)))
                    .flatMap {
                        lastTxUpdater.updateLastTxTime()
                            .onErrorComplete()
                            .andThen(Observable.just(it))
                    }
                    .applySchedulers()
            }
        }

    fun pushTx(signedTxBytes: ByteArray): Single<String> =
        pushEthTx(signedTxBytes).singleOrError()

    fun setLastTxHashObservable(txHash: String, timestamp: Long): Observable<String> =
        rxPinning.call<String> {
            setLastTxHash(txHash, timestamp)
                .applySchedulers()
        }

    fun setLastTxHashNowSingle(txHash: String): Single<String> =
        setLastTxHashObservable(txHash, System.currentTimeMillis())
            .singleOrError()

    private fun setLastTxHash(txHash: String, timestamp: Long): Observable<String> {
        ethDataStore.ethWallet!!.lastTransactionHash = txHash
        ethDataStore.ethWallet!!.lastTransactionTimestamp = timestamp

        return save().andThen(Observable.just(txHash))
    }

    private fun fetchOrCreateEthereumWallet(
        labelsMap: Map<CryptoCurrency, String>
    ):
        Single<Pair<EthereumWallet, Boolean>> =
        metadataManager.fetchMetadata(EthereumWallet.METADATA_TYPE_EXTERNAL).defaultIfEmpty("")
            .map { metadata ->
                val walletJson = if (metadata != "") metadata else null

                var ethWallet = EthereumWallet.load(walletJson)
                var needsSave = false

                if (ethWallet?.account == null || !ethWallet.account.isCorrect) {
                    try {
                        val masterKey = payloadDataManager.masterKey
                        ethWallet =
                            EthereumWallet(masterKey, labelsMap)
                        needsSave = true
                    } catch (e: HDWalletException) {
                        // Wallet private key unavailable. First decrypt with second password.
                        throw InvalidCredentialsException(e.message)
                    }
                }

                if (ethWallet.updateErc20Tokens(labelsMap.filter { it.key.hasFeature(IS_ERC20) })) {
                    needsSave = true
                }

                if (!ethWallet.account.isAddressChecksummed()) {
                    ethWallet.account.apply {
                        address = withChecksummedAddress()
                    }
                    needsSave = true
                }
                ethWallet to needsSave
            }.toSingle()

    fun save(): Completable =
        metadataManager.saveToMetadata(
            ethDataStore.ethWallet!!.toJson(),
            EthereumWallet.METADATA_TYPE_EXTERNAL
        )

    fun getErc20TokenData(currency: CryptoCurrency): Erc20TokenData {
        return when (currency) {
            CryptoCurrency.PAX -> getEthWallet()!!.getErc20TokenData(Erc20TokenData.PAX_CONTRACT_NAME)
            CryptoCurrency.USDT -> getEthWallet()!!.getErc20TokenData(Erc20TokenData.USDT_CONTRACT_NAME)
            CryptoCurrency.DGLD -> getEthWallet()!!.getErc20TokenData(Erc20TokenData.DGLD_CONTRACT_NAME)
            else -> throw IllegalArgumentException("Not an ERC20 token")
        }
    }

    val requireSecondPassword: Boolean
        get() = payloadDataManager.isDoubleEncrypted
}