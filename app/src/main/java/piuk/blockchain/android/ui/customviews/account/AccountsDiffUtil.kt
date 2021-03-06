package piuk.blockchain.android.ui.customviews.account

import androidx.recyclerview.widget.DiffUtil
import piuk.blockchain.android.coincore.BlockchainAccount
import piuk.blockchain.android.coincore.CryptoAccount
import piuk.blockchain.android.coincore.FiatAccount

internal class AccountsDiffUtil(
    private val oldAccounts: List<SelectableAccountItem>,
    private val newAccounts: List<SelectableAccountItem>
) : DiffUtil.Callback() {

    override fun getOldListSize(): Int = oldAccounts.size

    override fun getNewListSize(): Int = newAccounts.size

    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
        oldAccounts[oldItemPosition].account.isTheSameWith(newAccounts[newItemPosition].account)

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean = false
}

internal fun BlockchainAccount.isTheSameWith(other: BlockchainAccount): Boolean =
    this::class == other::class &&
            this.hasTheSameAsset(other) &&
            this.label == other.label

private fun BlockchainAccount.hasTheSameAsset(other: BlockchainAccount): Boolean {
    val thisCryptoAsset = (this as? CryptoAccount)?.asset
    val otherCryptoAsset = (other as? CryptoAccount)?.asset
    if (thisCryptoAsset != null && thisCryptoAsset == otherCryptoAsset)
        return true

    val thisFiatAsset = (this as? FiatAccount)?.fiatCurrency
    val otherFiatAsset = (other as? FiatAccount)?.fiatCurrency
    if (thisFiatAsset != null && thisFiatAsset == otherFiatAsset)
        return true

    return false
}