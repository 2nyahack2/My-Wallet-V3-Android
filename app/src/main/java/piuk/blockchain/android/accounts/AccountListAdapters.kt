package piuk.blockchain.android.accounts

import com.blockchain.accounts.AccountList
import com.blockchain.wallet.DefaultLabels
import com.blockchain.wallet.toAccountReference
import info.blockchain.balance.AccountReference
import info.blockchain.balance.AccountReferenceList
import info.blockchain.balance.CryptoCurrency
import io.reactivex.Single
import piuk.blockchain.androidcore.data.bitcoincash.BchDataManager
import piuk.blockchain.androidcore.data.ethereum.EthDataManager
import piuk.blockchain.androidcore.data.payload.PayloadDataManager

internal class BtcAccountListAdapter(
    private val payloadDataManager: PayloadDataManager
) : AccountList {

    override fun defaultAccount(): Single<AccountReference> =
        Single.just(payloadDataManager.defaultAccount.toAccountReference())

    override fun accounts(): Single<AccountReferenceList> =
        Single.just(payloadDataManager.accounts
            .filter { !it.isArchived }
            .map { it.toAccountReference() })
}

internal class BchAccountListAdapter(private val bchPayloadDataManager: BchDataManager) :
    AccountList {

    override fun defaultAccount(): Single<AccountReference> =
        Single.just(
            with(bchPayloadDataManager) {
                getAccountMetadataList()[getDefaultAccountPosition()].toAccountReference()
            }
        )

    override fun accounts(): Single<AccountReferenceList> =
        Single.just(bchPayloadDataManager.getAccountMetadataList()
            .filter { !it.isArchived }
            .map { it.toAccountReference() })
}

internal class PaxAccountListAdapter(
    private val ethDataManager: EthDataManager,
    private val labels: DefaultLabels
) : AccountList {

    override fun defaultAccount(): Single<AccountReference> =
        Single.just(defaultAccountReference())

    private fun defaultAccountReference() =
        AccountReference.Pax(labels.getDefaultNonCustodialWalletLabel(CryptoCurrency.PAX),
            ethDataManager.getEthWallet()?.account?.address
                ?: throw Exception("No ether wallet found"), "")

    override fun accounts(): Single<AccountReferenceList> =
        Single.just(listOf(defaultAccountReference()))
}

internal class UsdtAccountListAdapter(
    private val ethDataManager: EthDataManager,
    private val labels: DefaultLabels
) : AccountList {

    override fun defaultAccount(): Single<AccountReference> =
        Single.just(defaultAccountReference())

    private fun defaultAccountReference() =
        AccountReference.Usdt(labels.getDefaultNonCustodialWalletLabel(CryptoCurrency.USDT),
            ethDataManager.getEthWallet()?.account?.address
                ?: throw Exception("No usdt wallet found"), "")

    override fun accounts(): Single<AccountReferenceList> =
        Single.just(listOf(defaultAccountReference()))
}

internal class EthAccountListAdapter(private val ethDataManager: EthDataManager) : AccountList {

    override fun defaultAccount(): Single<AccountReference> =
        Single.just(defaultAccountReference())

    private fun defaultAccountReference() =
        (ethDataManager.getEthWallet() ?: throw Exception("No ether wallet found"))
            .account.toAccountReference()

    override fun accounts(): Single<AccountReferenceList> =
        Single.just(listOf(defaultAccountReference()))
}
