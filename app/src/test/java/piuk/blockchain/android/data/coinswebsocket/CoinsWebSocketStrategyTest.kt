package piuk.blockchain.android.data.coinswebsocket

import com.blockchain.android.testutils.rxInit
import com.blockchain.network.websocket.ConnectionEvent
import com.blockchain.network.websocket.WebSocket
import com.google.gson.Gson
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.never
import com.nhaarman.mockito_kotlin.verify
import info.blockchain.balance.CryptoCurrency
import info.blockchain.wallet.ethereum.Erc20TokenData
import info.blockchain.wallet.ethereum.EthereumWallet
import info.blockchain.wallet.ethereum.data.EthAddressResponseMap
import info.blockchain.wallet.payload.data.Wallet
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import org.amshove.kluent.`it returns`
import org.amshove.kluent.itReturns
import org.amshove.kluent.mock
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import piuk.blockchain.android.R
import piuk.blockchain.android.data.coinswebsocket.service.MessagesSocketHandler
import piuk.blockchain.android.data.coinswebsocket.strategy.CoinsWebSocketStrategy
import piuk.blockchain.android.util.AssetResourceFactory
import piuk.blockchain.android.util.StringUtils
import piuk.blockchain.androidcore.data.bitcoincash.BchDataManager
import piuk.blockchain.androidcore.data.ethereum.EthDataManager
import piuk.blockchain.androidcore.data.ethereum.models.CombinedEthModel
import piuk.blockchain.androidcore.data.payload.PayloadDataManager
import piuk.blockchain.androidcore.data.rxjava.RxBus
import piuk.blockchain.androidcore.utils.PersistentPrefs

class CoinsWebSocketStrategyTest {

    @get:Rule
    val rxSchedulers = rxInit {
        mainTrampoline()
        ioTrampoline()
    }

    private val messagesSocketHandler: MessagesSocketHandler = mock()

    val wallet = mock<EthereumWallet> {
        on { getErc20TokenData(Erc20TokenData.PAX_CONTRACT_NAME) } `it returns`
            Erc20TokenData.createPaxTokenData("")

        on { getErc20TokenData(Erc20TokenData.USDT_CONTRACT_NAME) } `it returns`
            Erc20TokenData.createUsdtTokenData("")
    }

    private val ethDataManager: EthDataManager = mock {
        on { getEthWallet() } `it returns` wallet
        on { getErc20TokenData(CryptoCurrency.PAX) } `it returns` Erc20TokenData.createPaxTokenData("")
        on { getErc20TokenData(CryptoCurrency.USDT) } `it returns` Erc20TokenData.createUsdtTokenData("")
        on { getErc20TokenData(CryptoCurrency.DGLD) } `it returns` Erc20TokenData.createDgldTokenData("")
        on { fetchEthAddress() } `it returns` Observable.just(CombinedEthModel(EthAddressResponseMap()))
        on { getEthWalletAddress() } `it returns` "0x4058a004dd718babab47e14dd0d744742e5b9903"
        on { refreshErc20Model(any()) } `it returns` Completable.complete()
    }

    private val stringUtils: StringUtils = mock {
        on { getString(R.string.app_name) } `it returns` "Blockchain"
        on { getString(R.string.received_ethereum) } `it returns` "Received Ether"
        on { getString(R.string.received_usd_pax_1) } `it returns` "Received USD Digital"
        on { getString(R.string.received_usdt) } `it returns` "Received Tether"
        on { getString(R.string.received_dgld) } `it returns` "Received Wrapped-DGLD"
        on { getString(R.string.common_from) } `it returns` "From"
        on { getString(R.string.received_erc20_marquee) } itReturns "Received %1\$s %2\$s"
        on { getString(R.string.received_erc20_text) } itReturns "Received %1\$s %2\$s from %3\$s"
    }

    private val rxBus: RxBus = mock()

    private val payloadDataManager: PayloadDataManager = mock {
        on { updateAllBalances() } `it returns` Completable.complete()
        on { updateAllTransactions() } `it returns` Completable.complete()
        on { payloadChecksum } `it returns` "741cd20c1f076c6393a07a2dc7b072188cd4e3ecea3184a1e6a5ed387daadb193245"
        on { tempPassword } `it returns` "2333"
        on { wallet } `it returns` Wallet()
        on { sharedKey } itReturns("")
        on { guid } itReturns("")
        on {
            initializeAndDecrypt(
                any(),
                any(),
                any()
            )
        } `it returns` Completable.complete()
    }

    private val bchDataManager: BchDataManager = mock {
        on { updateAllBalances() } `it returns` Completable.complete()
        on { getWalletTransactions(any(), any()) } `it returns` Observable.just(emptyList())
    }

    private val prefs: PersistentPrefs = mock {
        on { getValue(PersistentPrefs.KEY_WALLET_GUID, "") } `it returns` "1234"
    }

    private val assetResources: AssetResourceFactory = mock() {
        on { assetName(CryptoCurrency.USDT) } itReturns "Tether"
        on { assetName(CryptoCurrency.PAX) } itReturns "USD Digital"
        on { assetName(CryptoCurrency.DGLD) } itReturns "Wrapped-DGLD"
    }

    private val mockWebSocket: WebSocket<String, String> = mock()
    private val webSocket = FakeWebSocket(mockWebSocket)

    private val strategy = CoinsWebSocketStrategy(
        coinsWebSocket = webSocket,
        ethDataManager = ethDataManager,
        stringUtils = stringUtils,
        gson = Gson(),
        bchDataManager = bchDataManager,
        payloadDataManager = payloadDataManager,
        accessState = mock(),
        appUtil = mock(),
        prefs = prefs,
        rxBus = rxBus,
        assetResources = assetResources
    )

    @Before
    fun setup() {
        strategy.setMessagesHandler(messagesSocketHandler)
        strategy.open()
    }

    @Test
    fun `notification should be triggered on confirmed eth transaction`() {
        webSocket.send(confirmedEtheTransaction)

        verify(mockWebSocket).open()
        verify(messagesSocketHandler).triggerNotification("Blockchain",
            "Received Ether 0.00604741 ETH",
            "Received Ether 0.00604741 ETH from 0x4058a004dd718babab47e14dd0d744742e5b9903")
    }

    @Test
    fun `notification should not be triggered on pending eth transaction`() {
        webSocket.send(pendingEthTransaction)

        verify(mockWebSocket).open()
        verify(messagesSocketHandler, never()).triggerNotification(any(), any(), any())
    }

    @Test
    fun `eth transaction should be update eth transactions and broadcasted`() {
        webSocket.send(pendingEthTransaction)

        verify(mockWebSocket).open()
        verify(ethDataManager).fetchEthAddress()
        verify(ethDataManager, never()).refreshErc20Model(any())
        verify(messagesSocketHandler).sendBroadcast(any())
    }

    @Test
    fun `pax transaction should be update pax transactions and broadcasted`() {
        webSocket.send(paxTransaction)

        verify(mockWebSocket).open()
        verify(ethDataManager, never()).fetchEthAddress()
        verify(ethDataManager).refreshErc20Model(CryptoCurrency.PAX)
        verify(messagesSocketHandler).triggerNotification("Blockchain",
            "Received USD Digital 1.21 USD-D",
            "Received USD Digital 1.21 USD-D from 0x4058a004dd718babab47e14dd0d744742e5b9903")
        verify(messagesSocketHandler).sendBroadcast(any())
    }

    @Test
    fun `usdt transaction should be update usdt transactions and broadcasted`() {
        webSocket.send(usdtTransaction)

        verify(mockWebSocket).open()
        verify(ethDataManager, never()).fetchEthAddress()
        verify(ethDataManager).refreshErc20Model(CryptoCurrency.USDT)
        verify(messagesSocketHandler).triggerNotification("Blockchain",
            "Received Tether 1.21 USDT",
            "Received Tether 1.21 USDT from 0x4058a004dd718babab47e14dd0d744742e5b9903")
        verify(messagesSocketHandler).sendBroadcast(any())
    }

    @Test
    fun `wdgld transaction should be update wdgld transactions and broadcasted`() {
        webSocket.send(wdgldTransaction)

        verify(mockWebSocket).open()
        verify(ethDataManager, never()).fetchEthAddress()
        verify(ethDataManager).refreshErc20Model(CryptoCurrency.DGLD)
        verify(messagesSocketHandler).triggerNotification("Blockchain",
            "Received Wrapped-DGLD 0.0121 wDGLD",
            "Received Wrapped-DGLD 0.0121 wDGLD from 0x4058a004dd718babab47e14dd0d744742e5b9903")
        verify(messagesSocketHandler).sendBroadcast(any())
    }

    @Test
    fun `btc transaction should be update btc balance and transactions`() {
        webSocket.send(btcTransaction)
        verify(mockWebSocket).open()
        verify(payloadDataManager).updateAllBalances()
        verify(payloadDataManager).updateAllTransactions()
    }

    @Test
    fun `bch transaction should be update bch balance and transactions`() {
        webSocket.send(bchTransaction)
        verify(mockWebSocket).open()
        verify(bchDataManager).updateAllBalances()
        verify(bchDataManager).getWalletTransactions(50, 0)
    }

    @Test
    fun `test changed payload`() {
        webSocket.send(changedPayloadMessage)
        verify(mockWebSocket).open()
        verify(payloadDataManager).updateAllBalances()
        verify(payloadDataManager).updateAllTransactions()
        verify(rxBus).emitEvent(any(), any())
        verify(messagesSocketHandler).showToast(R.string.wallet_updated)
    }

    private class FakeWebSocket(mock: WebSocket<String, String>) : WebSocket<String, String> by mock {
        private val _sendSubject = PublishSubject.create<String>()

        override val connectionEvents: Observable<ConnectionEvent>
            get() = Observable.just(ConnectionEvent.Connected)

        override fun send(message: String) {
            _sendSubject.onNext(message)
        }

        override val responses: Observable<String>
            get() = _sendSubject
    }

    private val confirmedEtheTransaction =
        "{\"coin\":\"eth\",\"entity\":\"account\",\"address\":\"0x4058a004dd718babab47e14dd0d744742e5b9903\"," +
                "\"txHash\":\"0xe1ff1e0ea7023c80308302d809684f90d1c094f969a13343e6081197f3552c97\"," +
                "\"transaction\":{\"hash\":" +
                "\"0xe1ff1e0ea7023c80308302d809684f90d1c094f969a13343e6081197f3552c97\",\"blockHash\":\"0xd240c9a0" +
                "9f605854926d4259c6ea95d72553087a7a20b25a34f26189d9a6930e\",\"blockNumber\":8381040,\"from\":\"0x4" +
                "058a004dd718babab47e14dd0d744742e5b9903\",\"to\":\"0x4058a004dd718babab47e14dd0d744742e5b9903\",\"co" +
                "ntractAddress\":\"0x\",\"value\":\"6047410000000000\",\"nonce\":171,\"gasPrice\":\"4000000000\",\"ga" +
                "sLimit\":21000,\"gasUsed\":21000,\"data\":\"\",\"transactionIndex\":59,\"success\":true,\"err" +
                "or\":\"\",\"firstSeen\":0,\"timestamp\":1566220763,\"state\":\"confirmed\"}}"
    private val pendingEthTransaction =
        "{\"coin\":\"eth\",\"entity\":\"account\",\"address\":\"0x4058a004dd718babab47e14dd0d744742e5b9903\",\"txHa" +
                "sh\":\"0xe1ff1e0ea7023c80308302d809684f90d1c094f969a13343e6081197f3552c97\",\"transaction\"" +
                ":{\"hash\":\"0xe1ff1e0ea7023c80308302d809684f90d1c094" +
                "f969a13343e6081197f3552c97\",\"blockHash\":\"0xd240c9a09f605" +
                "854926d4259c6ea95d72553087a7a20b25a34f26189d9a6930e\",\"blockNumber\":8381040,\"from\":\"0x4058a004" +
                "dd718babab47e14dd0d744742e5b9903\",\"to\":\"0x4058a004dd718babab47e14dd0d744742e5b9903\",\"contract" +
                "Address\":\"0x\",\"value\":\"6047410000000000\",\"nonce\":171,\"gasPrice\":\"4000000000\",\"gasLi" +
                "mit\":21000,\"gasUsed\":21000,\"data\":\"\",\"transactionIndex\":59,\"success\":true,\"error\":" +
                "\"\",\"firstSeen\":0,\"timestamp\":1566220763,\"state\":\"pending\"}}"

    private val changedPayloadMessage =
        "{\"checksum\":\"741cd20c1f076c6393a07a2dc7b072188cd4e3ecea3184a1e6a5ed387daadb19\",\"op\":\"on_change\"," +
                "\"guid\":\"9e2751de-d47e-42c8-b7e2-22623d71a356\"}"

    private val paxTransaction =
        "{\"coin\":\"eth\",\"entity\":\"token_account\",\"param\":{\"accountAddress\":\"0x4058a004dd718babab47e14dd0" +
            "d744742e5b9903\",\"tokenAddress\":\"0x8e870d67f660d95d5be530380d0ec0bd388289e1\"},\"tokenTransf" +
            "er\":{\"blockHash\":\"0x1293676c93d91660ca4ec40df09b6ec4fa080138d975c19813b914befc1187c\",\"transact" +
            "ionHash\":\"0x3cd2e95358c58af6e9ecd2f0af6739c3db945e2259bf2a4bc91fb5e2f397ad89\",\"blockNumber\":83" +
            "62036,\"tokenHash\":\"0x8e870d67f660d95d5be530380d0ec0bd388289e1\",\"logIndex\":67,\"from\":\"0x4058" +
            "a004dd718babab47e14dd0d744742e5b9903\",\"to\":\"0x4058a004dd718babab47e14dd0d744742e5b9903\",\"val" +
            "ue\":1210000000000000000,\"decimals\":18,\"timestamp\":0}}"

    private val usdtTransaction =
        "{\"coin\":\"eth\",\"entity\":\"token_account\",\"param\":{\"accountAddress\":\"0x4058a004dd718babab47e14dd0" +
            "d744742e5b9903\",\"tokenAddress\":\"0xdAC17F958D2ee523a2206206994597C13D831ec7\"},\"tokenTransf" +
            "er\":{\"blockHash\":\"0x1293676c93d91660ca4ec40df09b6ec4fa080138d975c19813b914befc1187c\",\"transact" +
            "ionHash\":\"0x3cd2e95358c58af6e9ecd2f0af6739c3db945e2259bf2a4bc91fb5e2f397ad89\",\"blockNumber\":83" +
            "62036,\"tokenHash\":\"0x8e870d67f660d95d5be530380d0ec0bd388289e1\",\"logIndex\":67,\"from\":\"0x4058" +
            "a004dd718babab47e14dd0d744742e5b9903\",\"to\":\"0x4058a004dd718babab47e14dd0d744742e5b9903\",\"val" +
            "ue\":1210000,\"decimals\":6,\"timestamp\":0}}"

    private val wdgldTransaction =
        "{\"coin\":\"eth\",\"entity\":\"token_account\",\"param\":{\"accountAddress\":\"0x4058a004dd718babab47e14dd0" +
            "d744742e5b9903\",\"tokenAddress\":\"0x123151402076fc819b7564510989e475c9cd93ca\"},\"tokenTransf" +
            "er\":{\"blockHash\":\"0x1293676c93d91660ca4ec40df09b6ec4fa080138d975c19813b914befc1187c\",\"transact" +
            "ionHash\":\"0x3cd2e95358c58af6e9ecd2f0af6739c3db945e2259bf2a4bc91fb5e2f397ad89\",\"blockNumber\":83" +
            "62036,\"tokenHash\":\"0x8e870d67f660d95d5be530380d0ec0bd388289e1\",\"logIndex\":67,\"from\":\"0x4058" +
            "a004dd718babab47e14dd0d744742e5b9903\",\"to\":\"0x4058a004dd718babab47e14dd0d744742e5b9903\",\"val" +
            "ue\":1210000,\"decimals\":6,\"timestamp\":0}}"

    private val btcTransaction = "{\"coin\":\"btc\",\"entity\":\"xpub\",\"transaction\":" +
            "{\"lock_time\":0,\"ver\":1,\"size\":225,\"inputs\":[{\"address\":\"1Cox48WAm4NKTYbSjQ8DEswpaBNCfFwo9x" +
            "\",\"value\":66456,\"sequence\":4294967295,\"prev_out\":{\"spent\":true,\"tx_index\":1099871852,\"type" +
            "\":0,\"addr\":\"1Cox48WAm4NKTYbSjQ8DEswpaBNCfFwo9x\",\"value\":66456,\"n\":0,\"script\":\"76a914818a797e" +
            "c6bcf32151c5636d9e3859c646155e4388ac\"},\"script\":\"473044022037bb73b0e8c07c1ca4678d83c5a1ada9faf2a" +
            "94a35a77ac6ff91bd3b85a16d10022042ddbc8e6e0a65b2b50c0611547d98b69d758e2e90a4bdce1544fe060e3abf54012103" +
            "1bac95bde03950d087b289cca8d6504a42bd3389123dcba021e3e1c17b1d3188\"}],\"time\":1573045879,\"tx_index\":1" +
            "195657042,\"vin_sz\":1,\"hash\":\"154d477ea8fdfb401a97894ce6d511fc905bdf126d4b8256ba04dd3877f08" +
            "96a\",\"vout_sz\":2,\"relayed_by\":\"127.0.0.1\",\"out\":[{\"spent\":false,\"tx_index\":119565704" +
            "2,\"type\":0,\"addr\":\"1At9jiwzVsRJAtN9hkqpgHsaCTJZSfgWAm\",\"value\":27577,\"n\":0,\"script\":\"76a" +
            "9146c65a3994edb887e0962266b1e543f2bb6238a9488ac\"},{\"spent\":false,\"tx_index\":1195657042,\"ty" +
            "pe\":0,\"addr\":\"1F9HAVJWKS86z4VmoAJpRfRJR2wd4b2NAV\",\"value\":33340,\"n\":1,\"script\":\"76a91" +
            "49b2294d348b5ab6081014f0bef57eb77b7f4282b88ac\"}]}}"

    private val bchTransaction = "{\"coin\":\"bch\",\"entity\":\"xpub\",\"transaction\":{\"lo" +
            "ck_time\":0,\"ver\":1,\"size\":406,\"inputs\":[{\"address\":\"1PTPmqXXaQBe1K4PTTHngbSCHuj1N1L1sz" +
            "\",\"value\":546,\"sequence\":4294967295,\"prev_out\":{\"spent\":false,\"tx_index\":0,\"type\":0,\"a" +
            "ddr\":\"1PTPmqXXaQBe1K4PTTHngbSCHuj1N1L1sz\",\"value\":546,\"n\":1,\"script\":\"76a914f650979c734170" +
            "5c21f961c59cffa59214ed6d0a88ac\"},\"script\":\"473044022053ee0f14460f5f250ff248e6a69326c5f063cc035ff" +
            "8bc707bd66212de8cdc7a02203455291619180865bc1d0838daadd072c65a61fed9ee35f8220b54e987b931dc412103c3ab8" +
            "96f252e7929f7b975df0ecd9198b04d7a3cc4fa27073e22797c02800036\"},{\"address\":\"1di8urMQChm4JWp8ht5DLB" +
            "EqnhAEMdvUD\",\"value\":332521,\"sequence\":4294967295,\"prev_out\":{\"spent\":false,\"tx_index\":0" +
            ",\"type\":0,\"addr\":\"1di8urMQChm4JWp8ht5DLBEqnhAEMdvUD\",\"value\":332521,\"n\":2,\"script\":\"76a" +
            "1406f150459c0bb0ab8b1c9f54f119bc02c769619988ac\"},\"script\":\"47304402205809e521636fea27894fc91c2f7a" +
            "e8300584d6491815d48d571ea0f0b251622a02206f3c75a574d21ee846db7d35814fb254b6c3bc77011a9377a655eb390483b" +
            "4c5412103c04d705fa8aafb2e8a5f15396e91ace762f2e259e14a2fc8daf81fd2c3f1c2e7\"}],\"time\":1573137677,\"tx" +
            "_index\":0,\"vin_sz\":2,\"hash\":\"3e8a7d71f8e18569c1266c403342fff2d010f788c7ba47f46969191621451f" +
            "b7\",\"vout_sz\":3,\"relayed_by\":\"\",\"out\":[{\"spent\":false,\"tx_index\":0,\"type\":0,\"ad" +
            "dr\":\"1He3iJEfNyo5GaU1ntQkXHybryRZ6BZYbD\",\"value\":546,\"n\":0,\"script\":\"76a914b683aac0f87e27a" +
            "77b70ad5eaae6f8055e63e3dd88ac\"},{\"spent\":false,\"tx_index\":0,\"type\":0,\"addr\":\"1BKsAULno4DuWK" +
            "2MZvxdVx2oGdiM6faCAm\",\"value\":34445,\"n\":1,\"script\":\"76a91471429e8ea47c8d179f80ad55716657bad7ab" +
            "822388ac\"},{\"spent\":false,\"tx_index\":0,\"type\":0,\"addr\":\"1di8urMQChm4JWp8ht5DLBEqnhAEMdv" +
            "UD\",\"value\":296948,\"n\":2,\"script\":\"76a91406f150459c0bb0ab8b1c9f54f119bc02c769619988ac\"}]}}"
}