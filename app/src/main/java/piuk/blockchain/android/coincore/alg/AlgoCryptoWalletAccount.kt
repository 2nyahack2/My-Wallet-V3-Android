package piuk.blockchain.android.coincore.alg

import info.blockchain.balance.CryptoCurrency
import info.blockchain.balance.CryptoValue
import info.blockchain.balance.Money
import io.reactivex.Single
import org.apache.commons.lang3.NotImplementedException
import piuk.blockchain.android.coincore.ActivitySummaryList
import piuk.blockchain.android.coincore.AssetAction
import piuk.blockchain.android.coincore.AvailableActions
import piuk.blockchain.android.coincore.ReceiveAddress
import piuk.blockchain.android.coincore.TxEngine
import piuk.blockchain.android.coincore.impl.CryptoNonCustodialAccount
import piuk.blockchain.androidcore.data.exchangerate.ExchangeRateDataManager
import piuk.blockchain.androidcore.data.payload.PayloadDataManager

internal class AlgoCryptoWalletAccount(
    payloadManager: PayloadDataManager,
    override val label: String,
    override val isDefault: Boolean = true,
    override val exchangeRates: ExchangeRateDataManager
) : CryptoNonCustodialAccount(payloadManager, CryptoCurrency.ALGO) {

    override val accountBalance: Single<Money>
        get() = Single.just(CryptoValue.zero(asset))

    override val actionableBalance: Single<Money>
        get() = accountBalance

    override val receiveAddress: Single<ReceiveAddress>
        get() = Single.error(NotImplementedException("Need implementation of ALGO receive"))

    override val activity: Single<ActivitySummaryList>
        get() = Single.just(emptyList())

    override val isFunded: Boolean
        get() = false

    override val actions: Single<AvailableActions>
        get() = Single.just(setOf(AssetAction.ViewActivity))

    override fun createTxEngine(): TxEngine {
        TODO("Not yet implemented")
    }
}