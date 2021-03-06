package piuk.blockchain.android.ui.kyc.address

import com.blockchain.nabu.NabuToken
import com.blockchain.nabu.EthEligibility
import com.blockchain.nabu.datamanagers.NabuDataManager
import io.reactivex.Single

class EligibilityForFreeEthAdapter(
    private val nabuToken: NabuToken,
    private val nabuDataManager: NabuDataManager
) : EthEligibility {

    override fun isEligible(): Single<Boolean> {
        return nabuToken.fetchNabuToken().flatMap {
            nabuDataManager.getUser(it).map { nabuUser ->
                val userTier = nabuUser.tiers?.current ?: 0
                val isPowerPaxTagged = nabuUser.tags?.containsKey("POWER_PAX") ?: false
                return@map userTier == 2 && !isPowerPaxTagged
            }
        }
    }
}