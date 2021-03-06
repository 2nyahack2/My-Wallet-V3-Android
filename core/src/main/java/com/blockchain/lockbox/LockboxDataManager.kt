package com.blockchain.lockbox

import com.blockchain.accounts.AccountList
import com.blockchain.lockbox.models.Device
import com.blockchain.lockbox.models.LockboxMetadata
import com.blockchain.metadata.MetadataRepository
import com.blockchain.remoteconfig.FeatureFlag
import info.blockchain.balance.AccountReference
import info.blockchain.balance.AccountReferenceList
import info.blockchain.balance.CryptoCurrency
import io.reactivex.Maybe
import io.reactivex.Single

class LockboxDataManager(
    private val metadataRepository: MetadataRepository,
    private val featureFlag: FeatureFlag
) : AccountList {

    fun isLockboxAvailable(): Single<Boolean> = featureFlag.enabled

    fun hasLockbox(): Single<Boolean> = fetchLockbox()
        .toSingle()
        .map { it.hasLockbox() }
        .cache()
        .onErrorReturn { false }

    private fun fetchLockbox(): Maybe<LockboxMetadata> =
        metadataRepository.loadMetadata(LockboxMetadata.MetaDataType, LockboxMetadata::class.java)

    override fun accounts(): Single<AccountReferenceList> =
        fetchLockbox()
            .map {
                it.devices.flatMap { device -> device.accounts() }
            }
            .defaultIfEmpty(emptyList())
            .toSingle()

    override fun defaultAccount(): Single<AccountReference> {
        TODO("not implemented - not used")
    }
}

private fun Device.accounts() =
    btcAccounts() + bchAccounts() + ethAccounts() + xlmAccounts()

private fun Device.btcAccounts() =
    btc?.accounts?.map { account ->
        AccountReference.BitcoinLike(CryptoCurrency.BTC, account.label, account.xpub)
    } ?: emptyList()

private fun Device.bchAccounts() =
    bch?.accounts?.map { account ->
        AccountReference.BitcoinLike(CryptoCurrency.BCH, account.label, account.xpub)
    } ?: emptyList()

private fun Device.ethAccounts() =
    eth?.accounts?.map { account ->
        AccountReference.Ethereum(account.label, account.addr)
    } ?: emptyList()

private fun Device.xlmAccounts() =
    xlm?.accounts?.map { account ->
        AccountReference.Xlm(account.label, account.publicKey)
    } ?: emptyList()
