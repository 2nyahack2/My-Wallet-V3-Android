package piuk.blockchain.android.ui.transfer.receive

import android.os.Bundle
import android.view.View
import piuk.blockchain.android.R
import piuk.blockchain.android.coincore.AssetAction
import piuk.blockchain.android.coincore.BlockchainAccount
import piuk.blockchain.android.coincore.CryptoAccount
import piuk.blockchain.android.ui.customviews.account.DefaultCellDecorator
import piuk.blockchain.android.ui.transfer.AccountSelectorFragment
import piuk.blockchain.android.ui.transfer.receive.activity.ReceiveActivity

class TransferReceiveFragment : AccountSelectorFragment() {

    override val fragmentAction: AssetAction
        get() = AssetAction.Receive

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setEmptyStateDetails(R.string.common_empty_title,
            R.string.common_empty_details, R.string.common_empty_cta) {
            refreshItems()
        }

        initialiseAccountSelectorWithHeader(
            statusDecorator = {
                DefaultCellDecorator()
            },
            onAccountSelected = ::doOnAccountSelected,
            title = R.string.transfer_receive_crypto_title,
            label = R.string.transfer_receive_crypto_label,
            icon = R.drawable.ic_receive_blue_circle
        )
    }

    private fun doOnAccountSelected(account: BlockchainAccount) {
        require(account is CryptoAccount)
        ReceiveActivity.start(requireContext(), account)
    }

    companion object {
        fun newInstance() = TransferReceiveFragment()
    }
}
