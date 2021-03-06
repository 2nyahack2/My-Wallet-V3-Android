package piuk.blockchain.android.ui.home

import com.blockchain.nabu.datamanagers.NabuDataManager
import info.blockchain.wallet.payload.PayloadManagerWiper
import piuk.blockchain.android.ui.launcher.LauncherActivity
import piuk.blockchain.android.util.AppUtil
import piuk.blockchain.androidcore.data.access.AccessState
import piuk.blockchain.androidcore.data.bitcoincash.BchDataManager
import piuk.blockchain.androidcore.data.ethereum.EthDataManager
import piuk.blockchain.androidcore.data.metadata.MetadataManager
import piuk.blockchain.androidcore.data.walletoptions.WalletOptionsState

class CredentialsWiper(
    private val payloadManagerWiper: PayloadManagerWiper,
    private val ethDataManager: EthDataManager,
    private val accessState: AccessState,
    private val appUtil: AppUtil,
    private val bchDataManager: BchDataManager,
    private val metadataManager: MetadataManager,
    private val nabuDataManager: NabuDataManager,
    private val walletOptionsState: WalletOptionsState
) {
    fun wipe() {
        payloadManagerWiper.wipe()
        accessState.logout()
        accessState.unpairWallet()
        appUtil.restartApp(LauncherActivity::class.java)
        accessState.clearPin()
        ethDataManager.clearAccountDetails()
        bchDataManager.clearAccountDetails()
        nabuDataManager.clearAccessToken()
        metadataManager.reset()
        walletOptionsState.wipe()
    }
}