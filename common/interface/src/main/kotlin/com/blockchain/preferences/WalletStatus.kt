package com.blockchain.preferences

import info.blockchain.balance.CryptoCurrency

interface WalletStatus {
    var lastBackupTime: Long // Seconds since epoch
    val isWalletBackedUp: Boolean

    val isWalletFunded: Boolean
    fun setWalletFunded()

    var lastSwapTime: Long
    val hasSwapped: Boolean

    val hasMadeBitPayTransaction: Boolean
    fun setBitPaySuccess()

    fun setFeeTypeForAsset(cryptoCurrency: CryptoCurrency, type: Int)
    fun getFeeTypeForAsset(cryptoCurrency: CryptoCurrency): Int?

    val hasSeenSwapPromo: Boolean
    fun setSeenSwapPromo()

    val resendSmsRetries: Int
    fun setResendSmsRetries(retries: Int)

    val isNewUser: Boolean
    fun setNewUser()

    val hasSeenTradingSwapPromo: Boolean
    fun setSeenTradingSwapPromo()
}