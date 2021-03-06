package com.blockchain.nabu.datamanagers

import com.blockchain.nabu.models.responses.banktransfer.LinkBankAttributes
import om.blockchain.swap.nabu.BuildConfig

interface ExtraAttributesProvider {
    fun getBankLinkingAttributes(): LinkBankAttributes?
    fun getBankUpdateOverride(): String?
}

class ExtraAttributesProviderImpl : ExtraAttributesProvider {
    companion object {
        private const val userOverride = "sbMem5fb5284b1f71e3" // "sbMem5fb5284b1f71e1" //"sbMem5fb5284b1f71e2"
    }

    override fun getBankLinkingAttributes(): LinkBankAttributes? =
        if (BuildConfig.DEBUG) {
            LinkBankAttributes(userOverride)
        } else {
            null
        }

    override fun getBankUpdateOverride(): String? =
        if (BuildConfig.DEBUG) {
            userOverride
        } else {
            null
        }
}