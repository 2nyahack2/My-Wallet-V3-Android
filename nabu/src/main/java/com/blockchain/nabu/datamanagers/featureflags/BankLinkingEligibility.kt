package com.blockchain.nabu.datamanagers.featureflags

import android.os.Build
import com.blockchain.remoteconfig.FeatureFlag
import com.blockchain.nabu.models.data.BankPartner
import io.reactivex.Single

interface BankLinkingEnabledProvider {
    fun supportedBankPartners(): Single<List<BankPartner>>
    fun bankLinkingEnabled(): Single<Boolean>
}

class BankLinkingEnabledProviderImpl(
    private val achFF: FeatureFlag,
    private val globalLinkingFF: FeatureFlag
) : BankLinkingEnabledProvider {
    override fun supportedBankPartners(): Single<List<BankPartner>> =
        achFF.enabled.map {
            if (it && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                listOf(BankPartner.YODLEE)
            } else emptyList()
        }

    override fun bankLinkingEnabled(): Single<Boolean> = globalLinkingFF.enabled
}