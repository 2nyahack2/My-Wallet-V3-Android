package com.blockchain.nabu.models.responses.nabu

internal data class SupportedDocumentsResponse(
    val countryCode: String,
    val documentTypes: List<SupportedDocuments>
)

enum class SupportedDocuments {
    PASSPORT,
    DRIVING_LICENCE,
    NATIONAL_IDENTITY_CARD,
    RESIDENCE_PERMIT
}