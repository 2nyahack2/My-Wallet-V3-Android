package com.blockchain.accounts

import info.blockchain.balance.AccountReference
import info.blockchain.balance.AccountReferenceList
import io.reactivex.Single

@Deprecated("Only used by lockbox")
interface AccountList {
    fun defaultAccount(): Single<AccountReference>
    fun accounts(): Single<AccountReferenceList>
}
