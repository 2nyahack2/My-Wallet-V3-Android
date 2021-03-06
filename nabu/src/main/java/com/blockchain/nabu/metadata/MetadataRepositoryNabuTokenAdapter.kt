package com.blockchain.nabu.metadata

import com.blockchain.exceptions.MetadataNotFoundException
import com.blockchain.metadata.MetadataRepository
import com.blockchain.nabu.CreateNabuToken
import com.blockchain.nabu.NabuToken
import com.blockchain.rx.maybeCache
import com.blockchain.nabu.models.responses.tokenresponse.NabuOfflineTokenResponse
import com.blockchain.nabu.models.responses.tokenresponse.mapFromMetadata
import com.blockchain.nabu.models.responses.tokenresponse.mapToMetadata
import io.reactivex.Maybe
import io.reactivex.Single
import piuk.blockchain.androidcore.data.metadata.MetadataManager

class MetadataRepositoryNabuTokenAdapter(
    private val metadataRepository: MetadataRepository,
    private val createNabuToken: CreateNabuToken,
    private val metadataManager: MetadataManager
) : NabuToken {

    private fun createMetaData(currency: String?, action: String?) = Maybe.defer {
        createNabuToken.createNabuOfflineToken(currency, action)
            .map {
                it.mapToMetadata()
            }
            .flatMapMaybe {
                metadataRepository.saveMetadata(
                    it,
                    NabuCredentialsMetadata::class.java,
                    NabuCredentialsMetadata.USER_CREDENTIALS_METADATA_NODE
                ).andThen(Maybe.just(it))
            }
    }

    private val defer = Maybe.defer {
        metadataRepository.loadMetadata(
            NabuCredentialsMetadata.USER_CREDENTIALS_METADATA_NODE,
            NabuCredentialsMetadata::class.java
        )
    }.maybeCache()
        .filter { it.isValid() }

    override fun fetchNabuToken(currency: String?, action: String?): Single<NabuOfflineTokenResponse> =
        defer
            .switchIfEmpty(createMetaData(currency, action))
            .map { metadata ->
                if (!metadata.isValid()) throw MetadataNotFoundException("Nabu Token is empty")
                metadata.mapFromMetadata()
            }
            .toSingle()
}
