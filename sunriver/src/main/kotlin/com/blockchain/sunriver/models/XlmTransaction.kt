package com.blockchain.sunriver.models

import com.blockchain.sunriver.HorizonKeyPair
import com.blockchain.sunriver.Memo
import info.blockchain.balance.CryptoValue

data class XlmTransaction(
    val timeStamp: String,
    val value: CryptoValue,
    val fee: CryptoValue,
    val hash: String,
    val memo: Memo,
    val to: HorizonKeyPair.Public,
    val from: HorizonKeyPair.Public
) {
    val accountDelta: CryptoValue
        get() =
            if (value.isPositive) {
                value
            } else {
                (value - fee) as CryptoValue
            }
}
