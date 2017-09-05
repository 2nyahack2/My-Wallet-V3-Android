package piuk.blockchain.android.ui.account

import info.blockchain.wallet.ethereum.EthereumAccount
import info.blockchain.wallet.payload.data.Account
import info.blockchain.wallet.payload.data.LegacyAddress
import piuk.blockchain.android.util.annotations.Mockable

@Mockable
class ItemAccount {

    enum class TYPE {
        ALL_ACCOUNTS_AND_LEGACY, ALL_LEGACY, SINGLE_ACCOUNT, ETHEREUM
    }

    var label: String? = null
    var displayBalance: String? = null
    var tag: String? = null
    var absoluteBalance: Long? = null

    //TODO Get rid of this Any
    var accountObject: Any? = null

    //Address/Xpub to fetch balance/tx list
    var address: String? = null
    var type: TYPE = TYPE.SINGLE_ACCOUNT

    constructor() {
        // Empty constructor for serialization
    }

    //TODO This is disgusting
    fun getAddressString(): String {

        if (accountObject is Account) {
            //xpub
            return (accountObject as Account).xpub
        } else if (accountObject is LegacyAddress){
            //legacy address
            return (accountObject as LegacyAddress).address
        } else {
            //legacy address
            return (accountObject as EthereumAccount).address
        }
    }

    @JvmOverloads
    constructor(label: String?,
                displayBalance: String?,
                tag: String?,
                absoluteBalance: Long?,
                accountObject: Any? = null,
                address: String?,
                type: TYPE = TYPE.SINGLE_ACCOUNT) {
        this.label = label
        this.displayBalance = displayBalance
        this.tag = tag
        this.absoluteBalance = absoluteBalance
        this.address = address
        this.accountObject = accountObject
        this.type = type
    }
}
