package piuk.blockchain.android.data.coinswebsocket.strategy

import com.blockchain.network.websocket.ConnectionEvent
import com.blockchain.network.websocket.WebSocket
import com.google.gson.Gson
import info.blockchain.balance.CryptoCurrency
import info.blockchain.balance.CryptoValue
import info.blockchain.wallet.ethereum.Erc20TokenData
import info.blockchain.wallet.exceptions.DecryptionException
import io.reactivex.Completable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import org.web3j.utils.Convert
import piuk.blockchain.android.R
import piuk.blockchain.android.data.coinswebsocket.models.BtcBchResponse
import piuk.blockchain.android.data.coinswebsocket.models.Coin
import piuk.blockchain.android.data.coinswebsocket.models.Entity
import piuk.blockchain.android.data.coinswebsocket.models.EthResponse
import piuk.blockchain.android.data.coinswebsocket.models.EthTransaction
import piuk.blockchain.android.data.coinswebsocket.models.Input
import piuk.blockchain.android.data.coinswebsocket.models.Output
import piuk.blockchain.android.data.coinswebsocket.models.Parameters
import piuk.blockchain.android.data.coinswebsocket.models.SocketRequest
import piuk.blockchain.android.data.coinswebsocket.models.SocketResponse
import piuk.blockchain.android.data.coinswebsocket.models.TokenTransfer
import piuk.blockchain.android.data.coinswebsocket.models.TransactionState
import piuk.blockchain.android.data.coinswebsocket.service.MessagesSocketHandler
import piuk.blockchain.android.ui.launcher.LauncherActivity
import piuk.blockchain.android.util.AppUtil
import piuk.blockchain.android.util.AssetResourceFactory
import piuk.blockchain.android.util.StringUtils
import piuk.blockchain.androidcore.data.access.AccessState
import piuk.blockchain.androidcore.data.bitcoincash.BchDataManager
import piuk.blockchain.androidcore.data.ethereum.EthDataManager
import piuk.blockchain.androidcore.data.events.ActionEvent
import piuk.blockchain.androidcore.data.events.TransactionsUpdatedEvent
import piuk.blockchain.androidcore.data.events.WalletAndTransactionsUpdatedEvent
import piuk.blockchain.androidcore.data.payload.PayloadDataManager
import piuk.blockchain.androidcore.data.rxjava.RxBus
import piuk.blockchain.androidcore.utils.PersistentPrefs
import timber.log.Timber
import java.math.BigDecimal
import java.util.Locale

data class WebSocketReceiveEvent constructor(val address: String, val hash: String)

private data class CoinWebSocketInput(
    val guid: String,
    val ethAddress: String?,
    val erc20PaxContractAddress: String?,
    val erc20UsdtContractAddress: String?,
    val receiveBtcAddresses: List<String>,
    val receiveBhcAddresses: List<String>,
    val xPubsBtc: List<String>,
    val xPubsBch: List<String>
)

class CoinsWebSocketStrategy(
    private val coinsWebSocket: WebSocket<String, String>,
    private val ethDataManager: EthDataManager,
    private val stringUtils: StringUtils,
    private val gson: Gson,
    private val rxBus: RxBus,
    private val prefs: PersistentPrefs,
    private val accessState: AccessState,
    private val appUtil: AppUtil,
    private val payloadDataManager: PayloadDataManager,
    private val bchDataManager: BchDataManager,
    private val assetResources: AssetResourceFactory
) {

    private var coinWebSocketInput: CoinWebSocketInput? = null
    private val compositeDisposable = CompositeDisposable()
    private var messagesSocketHandler: MessagesSocketHandler? = null

    fun setMessagesHandler(messagesSocketHandler: MessagesSocketHandler) {
        this.messagesSocketHandler = messagesSocketHandler
    }

    fun open() {
        initInput()
        subscribeToEvents()
        coinsWebSocket.open()
    }

    private fun subscribeToEvents() {
        compositeDisposable += coinsWebSocket.connectionEvents.subscribe {
            when (it) {
                is ConnectionEvent.Connected -> run {
                    ping()
                    subscribe()
                }
            }
        }

        compositeDisposable += coinsWebSocket.responses.distinctUntilChanged()
            .subscribe { response ->
                val socketResponse = gson.fromJson(response, SocketResponse::class.java)
                if (socketResponse.op == "on_change")
                    checkForWalletChange(socketResponse.checksum)
                when (socketResponse.coin) {
                    Coin.ETH -> handleEthTransaction(response)
                    Coin.BTC -> handleBtcTransaction(response)
                    Coin.BCH -> handleBchTransaction(response)
                    else -> {
                    }
                }
            }
    }

    private fun checkForWalletChange(checksum: String?) {
        if (checksum == null) return
        val localChecksum = payloadDataManager.payloadChecksum
        val isSameChecksum = checksum == localChecksum

        if (!isSameChecksum && payloadDataManager.tempPassword != null) {
            compositeDisposable += downloadChangedPayload()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeBy(
                    onComplete = {
                        messagesSocketHandler?.showToast(R.string.wallet_updated)
                    },
                    onError = {
                        Timber.e(it)
                    }
                )
        }
    }

    private fun downloadChangedPayload(): Completable =
        payloadDataManager.initializeAndDecrypt(
            payloadDataManager.sharedKey,
            payloadDataManager.guid,
            payloadDataManager.tempPassword!!
        ).subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .doOnComplete { this.updateBtcBalancesAndTransactions() }
            .doOnError { throwable ->
                Timber.e(throwable)
                if (throwable is DecryptionException) {
                    messagesSocketHandler?.showToast(R.string.wallet_updated)
                    accessState.unpairWallet()
                    appUtil.restartApp(LauncherActivity::class.java)
                }
            }

    private fun handleTransactionInputsAndOutputs(
        inputs: List<Input>,
        outputs: List<Output>,
        hash: String?,
        containsAddress: (address: String) -> Boolean?
    ): Pair<String?, BigDecimal> {
        var value = 0.toBigDecimal()
        var totalValue = 0.toBigDecimal()
        var inAddr: String? = null

        inputs.forEach { input ->
            input.prevOut?.let { output ->
                if (output.value != null) {
                    value = output.value
                }
                if (output.xpub != null) {
                    totalValue -= value
                } else if (output.addr != null) {
                    if (containsAddress(output.addr) == true) {
                        totalValue -= value
                    } else if (inAddr == null) {
                        inAddr = output.addr
                    }
                }
            }
        }

        outputs.forEach { output ->
            output.value?.let {
                value = output.value
            }
            if (output.addr != null && hash != null) {
                rxBus.emitEvent(
                    WebSocketReceiveEvent::class.java, WebSocketReceiveEvent(
                        output.addr,
                        hash
                    )
                )
            }
            if (output.xpub != null) {
                totalValue += value
            } else if (output.addr != null && containsAddress(output.addr) == true) {
                totalValue += value
            }
        }
        return inAddr to totalValue
    }

    private fun handleBtcTransaction(response: String) {
        val btcResponse = gson.fromJson(response, BtcBchResponse::class.java)
        val transaction = btcResponse.transaction ?: return

        handleTransactionInputsAndOutputs(
            transaction.inputs,
            transaction.outputs,
            transaction.hash
        ) { x ->
            payloadDataManager.wallet?.containsLegacyAddress(x)
        }

        updateBtcBalancesAndTransactions()
    }

    private fun handleBchTransaction(response: String) {
        val bchResponse = gson.fromJson(response, BtcBchResponse::class.java)
        val transaction = bchResponse.transaction ?: return

        val (inAddr, totalValue) =
            handleTransactionInputsAndOutputs(
                transaction.inputs, transaction.outputs,
                transaction.hash
            ) { x ->
                bchDataManager.getLegacyAddressStringList().contains(x)
            }

        updateBchBalancesAndTransactions()

        val title = stringUtils.getString(R.string.app_name)

        if (totalValue > BigDecimal.ZERO) {
            val amount = CryptoValue.fromMinor(CryptoCurrency.BCH, totalValue)
            val marquee =
                stringUtils.getString(R.string.received_bitcoin_cash) + amount.toStringWithSymbol()

            var text = marquee
            text += " ${stringUtils.getString(R.string.common_from).toLowerCase(Locale.US)} $inAddr"
            messagesSocketHandler?.triggerNotification(
                title, marquee, text
            )
        }
    }

    private fun updateBtcBalancesAndTransactions() {
        compositeDisposable += payloadDataManager.updateAllBalances()
            .andThen(payloadDataManager.updateAllTransactions())
            .subscribe {
                rxBus.emitEvent(ActionEvent::class.java, WalletAndTransactionsUpdatedEvent())
            }
    }

    private fun updateBchBalancesAndTransactions() {
        compositeDisposable += bchDataManager.updateAllBalances()
            .andThen(bchDataManager.getWalletTransactions(50, 0))
            .subscribe {
                rxBus.emitEvent(ActionEvent::class.java, WalletAndTransactionsUpdatedEvent())
            }
    }

    private fun handleEthTransaction(response: String) {
        val ethResponse = gson.fromJson(response, EthResponse::class.java)
        val title = stringUtils.getString(R.string.app_name)

        if (ethResponse.transaction != null && ethResponse.getTokenType() == CryptoCurrency.ETHER) {
            val transaction: EthTransaction = ethResponse.transaction
            val ethAddress = ethAddress()
            if (transaction.state == TransactionState.CONFIRMED && transaction.to.equals(ethAddress, true)
            ) {
                val marquee = stringUtils.getString(R.string.received_ethereum) + " " +
                    Convert.fromWei(BigDecimal(transaction.value), Convert.Unit.ETHER) + " ETH"
                val text = "$marquee " + stringUtils.getString(R.string.common_from)
                    .toLowerCase(Locale.US) + " " + transaction.from

                messagesSocketHandler?.triggerNotification(title, marquee, text)
            }
            updateEthTransactions()
        }

        if (ethResponse.entity == Entity.TokenAccount &&
            ethResponse.tokenTransfer != null &&
            ethResponse.tokenTransfer.to.equals(ethAddress(), true)
        ) {
            val tokenTransaction = ethResponse.tokenTransfer
            val asset = ethResponse.getTokenType()
            if (asset.hasFeature(CryptoCurrency.IS_ERC20)) {
                triggerErc20NotificationAndUpdate(asset, tokenTransaction, title)
            }
        }
    }

    private fun triggerErc20NotificationAndUpdate(
        asset: CryptoCurrency,
        tokenTransaction: TokenTransfer,
        title: String
    ) {
        val amountString = CryptoValue.fromMinor(asset, tokenTransaction.value).toStringWithSymbol()
        val formatMarquee = stringUtils.getString(R.string.received_erc20_marquee)
        val marquee = formatMarquee.format(
            assetResources.assetName(asset),
            amountString
        )
        val formatText = stringUtils.getString(R.string.received_erc20_text)
        val text = formatText.format(
            assetResources.assetName(asset),
            amountString,
            tokenTransaction.from
        )

        messagesSocketHandler?.triggerNotification(title, marquee, text)
        updateErc20Transactions(asset)
    }

    private fun updateErc20Transactions(asset: CryptoCurrency) {
        compositeDisposable += ethDataManager.refreshErc20Model(asset)
            .subscribeBy(
                onComplete = {
                    messagesSocketHandler?.sendBroadcast(TransactionsUpdatedEvent())
                },
                onError = { throwable ->
                    Timber.e(throwable, "update transaction (${asset.networkTicker} failed")
                }
            )
    }

    private fun updateEthTransactions() {
        compositeDisposable += ethDataManager.fetchEthAddress()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeBy(
                onComplete = { messagesSocketHandler?.sendBroadcast(TransactionsUpdatedEvent()) },
                onError = { throwable -> Timber.e(throwable, "downloadEthTransactions failed") }
            )
    }

    fun subscribeToXpubBtc(xpub: String) {
        val updatedList = (coinWebSocketInput?.xPubsBtc?.toMutableList() ?: mutableListOf()) + xpub
        coinWebSocketInput = coinWebSocketInput?.copy(xPubsBtc = updatedList)

        subscribeToXpub(Coin.BTC, xpub)
    }

    fun subscribeToExtraBtcAddress(address: String) =
        coinWebSocketInput?.let { input ->
            val updatedList = input.receiveBtcAddresses.toMutableList() + address
            coinWebSocketInput = input.copy(receiveBtcAddresses = updatedList)
            coinsWebSocket.send(
                gson.toJson(
                    SocketRequest.SubscribeRequest(
                        Entity.Account,
                        Coin.BTC,
                        Parameters.SimpleAddress(address = address)
                    )
                )
            )
        }

    fun close() {
        unsubscribeFromAddresses()
        coinsWebSocket.close()
        compositeDisposable.clear()
    }

    private fun unsubscribeFromAddresses() {
        coinWebSocketInput?.let { input ->
            input.receiveBtcAddresses.forEach { address ->
                coinsWebSocket.send(
                    gson.toJson(
                        SocketRequest.UnSubscribeRequest(
                            Entity.Account,
                            Coin.BTC,
                            Parameters.SimpleAddress(
                                address = address
                            )
                        )
                    )
                )
            }

            input.receiveBhcAddresses.forEach { address ->
                coinsWebSocket.send(
                    gson.toJson(
                        SocketRequest.UnSubscribeRequest(
                            Entity.Account,
                            Coin.BTC,
                            Parameters.SimpleAddress(
                                address = address
                            )
                        )
                    )
                )
            }

            input.xPubsBtc.forEach { xPub ->
                unsubscribeFromXpub(Coin.BTC, xPub)
            }

            input.xPubsBch.forEach { xPub ->
                unsubscribeFromXpub(Coin.BCH, xPub)
            }

            input.ethAddress?.let { ethAddress ->
                coinsWebSocket.send(
                    gson.toJson(
                        SocketRequest.UnSubscribeRequest(
                            Entity.Account,
                            Coin.ETH,
                            Parameters.SimpleAddress(ethAddress)
                        )
                    )
                )

                input.erc20PaxContractAddress?.let { contractAddress ->
                    coinsWebSocket.send(
                        gson.toJson(
                            SocketRequest.UnSubscribeRequest(
                                Entity.TokenAccount,
                                Coin.ETH,
                                Parameters.TokenedAddress(
                                    address = ethAddress,
                                    tokenAddress = contractAddress
                                )
                            )
                        )
                    )
                }
            }

            coinsWebSocket.send(
                gson.toJson(
                    SocketRequest.UnSubscribeRequest(
                        Entity.Wallet, Coin.None,
                        Parameters.Guid(input.guid)
                    )
                )
            )
        }
    }

    private fun initInput() {
        coinWebSocketInput = CoinWebSocketInput(
            guid(),
            ethAddress(),
            erc20PaxContractAddress(),
            erc20UsdtContractAddress(),
            btcReceiveAddresses(),
            bchReceiveAddresses(),
            xPubsBtc(),
            xPubsBch()
        )
    }

    private fun guid(): String = prefs.getValue(PersistentPrefs.KEY_WALLET_GUID, "")

    private fun xPubsBch(): List<String> {
        val nbAccounts: Int
        return if (payloadDataManager.wallet?.isUpgraded == true) {
            nbAccounts = bchDataManager.getActiveXpubs().size
            val xpubs = mutableListOf<String>()
            for (i in 0 until nbAccounts) {
                val activeXpubs = bchDataManager.getActiveXpubs()
                if (activeXpubs[i].isNotEmpty()) {
                    xpubs.add(bchDataManager.getActiveXpubs()[i])
                }
            }
            xpubs
        } else {
            emptyList()
        }
    }

    private fun xPubsBtc(): List<String> {
        val nbAccounts: Int
        if (payloadDataManager.wallet?.isUpgraded == true) {
            nbAccounts = try {
                payloadDataManager.totalAccounts()
            } catch (e: IndexOutOfBoundsException) {
                0
            }

            val xpubs = mutableListOf<String>()
            for (i in 0 until nbAccounts) {
                val xPub = payloadDataManager.wallet!!.hdWallets[0].accounts[i].xpub
                if (xPub != null && xPub.isNotEmpty()) {
                    xpubs.add(xPub)
                }
            }
            return xpubs
        } else {
            return emptyList()
        }
    }

    private fun btcReceiveAddresses(): List<String> =
        payloadDataManager.wallet?.let {
            mutableListOf<String>().apply {
                val legacyList = it.legacyAddressList
                legacyList.forEach { element ->
                    val address = element.address
                    if (address.isNullOrEmpty().not()) {
                        add(address!!)
                    }
                }
            }
        } ?: emptyList()

    private fun bchReceiveAddresses(): List<String> =
        payloadDataManager.wallet?.let {
            mutableListOf<String>().apply {
                val legacyList = bchDataManager.getLegacyAddressStringList()
                legacyList.forEach { address ->
                    if (address.isNotEmpty()) {
                        add(address)
                    }
                }
            }
        } ?: emptyList()

    private fun erc20PaxContractAddress(): String? =
        ethDataManager.getEthWallet()
            ?.getErc20TokenData(Erc20TokenData.PAX_CONTRACT_NAME)?.contractAddress

    private fun erc20UsdtContractAddress(): String? =
        ethDataManager.getEthWallet()
            ?.getErc20TokenData(Erc20TokenData.USDT_CONTRACT_NAME)?.contractAddress

    private fun ethAddress(): String? =
        ethDataManager.getEthWalletAddress()

    private fun subscribe() =
        coinWebSocketInput?.let { input ->
            coinsWebSocket.send(
                gson.toJson(
                    SocketRequest.SubscribeRequest(
                        Entity.Wallet, Coin.None,
                        Parameters.Guid(input.guid)
                    )
                )
            )

            input.receiveBtcAddresses.forEach { address ->
                coinsWebSocket.send(
                    gson.toJson(
                        SocketRequest.SubscribeRequest(
                            Entity.Account,
                            Coin.BTC,
                            Parameters.SimpleAddress(address = address)
                        )
                    )
                )
            }

            input.receiveBhcAddresses.forEach { address ->
                coinsWebSocket.send(
                    gson.toJson(
                        SocketRequest.SubscribeRequest(
                            Entity.Account,
                            Coin.BTC,
                            Parameters.SimpleAddress(address = address)
                        )
                    )
                )
            }

            input.xPubsBtc.forEach { xPub ->
                subscribeToXpub(Coin.BTC, xPub)
            }

            input.xPubsBch.forEach { xPub ->
                subscribeToXpub(Coin.BCH, xPub)
            }

            input.ethAddress?.let { ethAddress ->
                coinsWebSocket.send(
                    gson.toJson(
                        SocketRequest.SubscribeRequest(
                            Entity.Account,
                            Coin.ETH,
                            Parameters.SimpleAddress(ethAddress)
                        )
                    )
                )

                input.erc20PaxContractAddress?.let { contractAddress ->
                    coinsWebSocket.send(
                        gson.toJson(
                            SocketRequest.SubscribeRequest(
                                Entity.TokenAccount,
                                Coin.ETH,
                                Parameters.TokenedAddress(
                                    address = ethAddress,
                                    tokenAddress = contractAddress
                                )
                            )
                        )
                    )
                }
            }
        }

    private fun subscribeToXpub(coin: Coin, xpub: String) =
        coinsWebSocket.send(
            gson.toJson(
                SocketRequest.SubscribeRequest(
                    Entity.Xpub,
                    coin,
                    Parameters.SimpleAddress(address = xpub)
                )
            )
        )

    private fun unsubscribeFromXpub(coin: Coin, xpub: String) =
        coinsWebSocket.send(
            gson.toJson(
                SocketRequest.UnSubscribeRequest(
                    Entity.Xpub,
                    coin,
                    Parameters.SimpleAddress(address = xpub)
                )
            )
        )

    private fun ping() {
        coinsWebSocket.send(gson.toJson(SocketRequest.PingRequest))
    }

    private fun EthResponse.getTokenType(): CryptoCurrency {
        require(entity == Entity.Account || entity == Entity.TokenAccount)
        return when {
            entity == Entity.Account && !isErc20Token() -> CryptoCurrency.ETHER
            entity == Entity.TokenAccount && isErc20ParamType(CryptoCurrency.PAX) -> CryptoCurrency.PAX
            entity == Entity.TokenAccount && isErc20ParamType(CryptoCurrency.USDT) -> CryptoCurrency.USDT
            entity == Entity.TokenAccount && isErc20ParamType(CryptoCurrency.DGLD) -> CryptoCurrency.DGLD
            else -> {
                throw IllegalStateException("This should never trigger, did we add a new ERC20 token?")
            }
        }
    }

    private fun EthResponse.isErc20ParamType(cryptoCurrency: CryptoCurrency) =
        param?.tokenAddress.equals(ethDataManager.getErc20TokenData(cryptoCurrency).contractAddress, true)

    private fun EthResponse.isErc20Token(): Boolean =
        isErc20TransactionType(CryptoCurrency.PAX) ||
            isErc20TransactionType(CryptoCurrency.USDT) ||
            isErc20TransactionType(CryptoCurrency.DGLD)

    private fun EthResponse.isErc20TransactionType(cryptoCurrency: CryptoCurrency) =
        transaction?.to.equals(ethDataManager.getErc20TokenData(cryptoCurrency).contractAddress, true)

    private fun PayloadDataManager.totalAccounts(): Int =
        wallet?.hdWallets?.get(0)?.accounts?.size ?: 0
}