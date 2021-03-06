package piuk.blockchain.android.withdraw.mvi

import com.blockchain.nabu.datamanagers.CustodialWalletManager
import com.blockchain.nabu.datamanagers.repositories.AssetBalancesRepository
import info.blockchain.balance.FiatValue

class WithdrawInteractor(
    private val assetBalancesRepository: AssetBalancesRepository,
    private val custodialWalletManager: CustodialWalletManager
) {

    fun fetchBalanceForCurrency(currency: String) =
        assetBalancesRepository.getActionableBalanceForAsset(currency)
            .defaultIfEmpty(FiatValue.zero(currency))

    fun fetchLinkedBanks(currency: String) =
        custodialWalletManager.getLinkedBeneficiaries().map { banks ->
            banks.filter { it.currency == currency }
        }

    fun createWithdrawOrder(
        amount: FiatValue,
        bankId: String
    ) = custodialWalletManager.createWithdrawOrder(
        amount, bankId
    )

    fun fetchWithdrawFees(currency: String) =
        custodialWalletManager.fetchWithdrawFee(currency)
}