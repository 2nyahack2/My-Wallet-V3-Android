package com.blockchain.nabu.datamanagers.repositories

import com.blockchain.rx.ParameteredTimedCacheRequest
import com.blockchain.nabu.datamanagers.CustodialWalletManager
import com.blockchain.nabu.datamanagers.custodialwalletimpl.PaymentMethodType
import io.reactivex.Single
import timber.log.Timber
import java.math.BigInteger

class WithdrawLocksRepository(custodialWalletManager: CustodialWalletManager) {
    private val cache = ParameteredTimedCacheRequest<PaymentMethodType, BigInteger>(
        cacheLifetimeSeconds = 100L,
        refreshFn = {
            custodialWalletManager.fetchWithdrawLocksTime(it)
                .doOnSuccess { Timber.d("Withdrawal lock: $it") }
        }
    )

    fun getWithdrawLockTypeForPaymentMethod(paymentMethodType: PaymentMethodType): Single<BigInteger> =
        cache.getCachedSingle(paymentMethodType)
            .onErrorReturn { BigInteger.ZERO }
}