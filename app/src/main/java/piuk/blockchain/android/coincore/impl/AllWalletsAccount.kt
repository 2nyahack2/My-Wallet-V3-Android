package piuk.blockchain.android.coincore.impl

import com.blockchain.wallet.DefaultLabels
import info.blockchain.balance.ExchangeRates
import info.blockchain.balance.FiatValue
import info.blockchain.balance.Money
import io.reactivex.Single
import piuk.blockchain.android.coincore.AccountGroup
import piuk.blockchain.android.coincore.ActivitySummaryList
import piuk.blockchain.android.coincore.AssetAction
import piuk.blockchain.android.coincore.AvailableActions
import piuk.blockchain.android.coincore.BlockchainAccount
import piuk.blockchain.android.coincore.SingleAccountList

class AllWalletsAccount(
    override val accounts: SingleAccountList,
    labels: DefaultLabels
) : AccountGroup {

    override val label: String = labels.getAllWalletLabel()

    override val accountBalance: Single<Money>
        get() = Single.error(NotImplementedError("No unified balance for All Wallets meta account"))

    override val pendingBalance: Single<Money>
        get() = Single.error(NotImplementedError("No unified pending balance for All Wallets meta account"))

    override val activity: Single<ActivitySummaryList>
        get() = allActivities()

    override val actions: Single<AvailableActions>
        get() = Single.just(setOf(AssetAction.ViewActivity))

    override val isFunded: Boolean
        get() = true

    override val hasTransactions: Boolean
        get() = true

    override fun fiatBalance(fiatCurrency: String, exchangeRates: ExchangeRates): Single<Money> =
        allAccounts().flattenAsObservable { it }
            .flatMapSingle { it.fiatBalance(fiatCurrency, exchangeRates) }
            .reduce { a, v -> a + v }
            .toSingle(FiatValue.zero(fiatCurrency))

    override fun includes(account: BlockchainAccount): Boolean = true

    private fun allAccounts(): Single<List<BlockchainAccount>> =
        Single.just(accounts)

    private fun allActivities(): Single<ActivitySummaryList> =
        allAccounts().flattenAsObservable { it }
            .flatMapSingle { account ->
                account.activity
                    .onErrorResumeNext { Single.just(emptyList()) }
            }
            .reduce { a, l -> a + l }
            .toSingle(emptyList())
            .map { it.sorted() }
}
