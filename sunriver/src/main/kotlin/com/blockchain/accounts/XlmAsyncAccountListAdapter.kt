package com.blockchain.accounts

import com.blockchain.sunriver.XlmDataManager
import info.blockchain.balance.AccountReference
import info.blockchain.balance.AccountReferenceList
import io.reactivex.Maybe
import io.reactivex.Single

internal class XlmAsyncAccountListAdapter(
    private val xlmDataManager: XlmDataManager
) : AccountList {

    override fun defaultAccount(): Single<AccountReference> =
        xlmDataManager.defaultAccountReference()

    override fun accounts(): Single<AccountReferenceList> =
        xlmDataManager.maybeDefaultAccount().map { listOf<AccountReference>(it) }
            .switchIfEmpty(Maybe.just(emptyList()))
            .toSingle()
}
