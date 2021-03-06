package com.blockchain.sunriver.datamanager

import com.blockchain.logging.CrashLogger
import com.blockchain.metadata.MetadataRepository
import com.blockchain.rx.maybeCache
import com.blockchain.sunriver.derivation.deriveXlmAccountKeyPair
import com.blockchain.wallet.DefaultLabels
import com.blockchain.wallet.Seed
import com.blockchain.wallet.SeedAccess
import info.blockchain.balance.CryptoCurrency
import io.reactivex.Maybe

internal class XlmMetaDataInitializer(
    private val defaultLabels: DefaultLabels,
    private val repository: MetadataRepository,
    private val seedAccess: SeedAccess,
    private val crashLogger: CrashLogger
) {
    /**
     * Will not prompt for second password.
     */
    internal val initWalletMaybe: Maybe<XlmMetaData> = Maybe.defer {
        Maybe.concat(
            load,
            createAndSave()
        ).firstElement()
    }

    /**
     * Might prompt for second password if required to generate the meta data.
     */
    internal val initWalletMaybePrompt: Maybe<XlmMetaData> = Maybe.defer {
        Maybe.concat(
            load,
            createAndSavePrompt()
        ).firstElement()
    }

    private val load: Maybe<XlmMetaData> = Maybe.defer {
        repository.loadMetadata(XlmMetaData.MetaDataType, XlmMetaData::class.java)
            .ignoreBadMetadata()
            .compareForLog()
    }.maybeCache()

    private fun createAndSave(): Maybe<XlmMetaData> = newXlmMetaData().saveSideEffect()

    private fun createAndSavePrompt(): Maybe<XlmMetaData> = newXlmMetaDataPrompt().saveSideEffect()

    private fun newXlmMetaData(): Maybe<XlmMetaData> = seedAccess.seed.deriveMetadata()

    private fun newXlmMetaDataPrompt(): Maybe<XlmMetaData> =
        seedAccess.seedPromptIfRequired.deriveMetadata()

    private fun Maybe<XlmMetaData>.compareForLog(): Maybe<XlmMetaData> =
        flatMap { loaded ->
            Maybe.concat(
                newXlmMetaData()
                    .doOnSuccess { expected ->
                        inspectLoadedData(loaded, expected)
                    }
                    .map { loaded },
                this
            ).firstElement()
        }

    /**
     * Logs any discrepancies between the expected first account, and the loaded first account.
     * If it cannot test for discrepancies (e.g., no seed available at the time) it does not log anything.
     */
    private fun inspectLoadedData(loaded: XlmMetaData, expected: XlmMetaData) {
        val expectedAccount = expected.accounts?.get(0)
        val loadedAccount = loaded.accounts?.get(0)
        if (expectedAccount?.publicKey != loadedAccount?.publicKey) {
            Throwable("Xlm metadata expected did not match that loaded").let {
                crashLogger.logException(it)
                if (crashLogger.isDebugBuild) {
                    // we want to know about this on a debug build
                    throw it
                }
            }
        }
    }

    private fun Maybe<XlmMetaData>.saveSideEffect(): Maybe<XlmMetaData> =
        flatMap { newData ->
            repository.saveMetadata(
                newData,
                XlmMetaData::class.java,
                XlmMetaData.MetaDataType
            ).andThen(Maybe.just(newData))
        }

    private fun Maybe<Seed>.deriveMetadata(): Maybe<XlmMetaData> =
        map { seed ->
            val derived = deriveXlmAccountKeyPair(seed.hdSeed, 0)
            XlmMetaData(
                defaultAccountIndex = 0,
                accounts = listOf(
                    XlmAccount(
                        publicKey = derived.accountId,
                        label = defaultLabels.getDefaultNonCustodialWalletLabel(CryptoCurrency.XLM),
                        archived = false
                    )
                ),
                transactionNotes = emptyMap()
            )
        }
}

private fun Maybe<XlmMetaData>.ignoreBadMetadata(): Maybe<XlmMetaData> =
    filter { !(it.accounts?.isEmpty() ?: true) }
