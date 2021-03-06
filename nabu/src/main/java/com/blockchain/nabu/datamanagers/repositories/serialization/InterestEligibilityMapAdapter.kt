package com.blockchain.nabu.datamanagers.repositories.serialization

import com.blockchain.nabu.models.responses.interest.DisabledReason
import com.blockchain.nabu.models.responses.interest.InterestEligibilityFullResponse
import com.blockchain.nabu.models.responses.interest.InterestEligibilityResponse
import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonReader
import info.blockchain.balance.CryptoCurrency
import timber.log.Timber

class InterestEligibilityMapAdapter {
    @FromJson
    fun fromJson(reader: JsonReader): InterestEligibilityFullResponse {
        val map = mutableMapOf<String, InterestEligibilityResponse>()

        reader.beginObject()
        try {
            while (reader.hasNext()) {
                val k = reader.nextName()
                if (reader.peek() != JsonReader.Token.BEGIN_OBJECT) {
                    processObject(reader, map)
                } else {
                    processMap(reader, map, k)
                }
            }
            reader.endObject()
        } catch (e: JsonDataException) {
            Timber.e("Error parsing interest eligibility ${e.message}")
        } finally {
            // if mapping failed, populate values as ineligible
            if (map.isEmpty()) {
                CryptoCurrency.activeCurrencies().map {
                    map[it.networkTicker] = InterestEligibilityResponse(false, DisabledReason.OTHER)
                }
            }

            return InterestEligibilityFullResponse(map)
        }
    }

    private fun processMap(
        reader: JsonReader,
        map: MutableMap<String, InterestEligibilityResponse>,
        k: String
    ) {
        reader.beginObject()
        val result = readObject(reader)
        reader.endObject()
        map[k] = InterestEligibilityResponse(result.first, result.second)
    }

    private fun processObject(
        reader: JsonReader,
        map: MutableMap<String, InterestEligibilityResponse>
    ) {
        val result = readObject(reader)
        CryptoCurrency.activeCurrencies().map {
            map[it.networkTicker] = InterestEligibilityResponse(result.first, result.second)
        }
    }

    private fun readObject(reader: JsonReader): Pair<Boolean, DisabledReason> {
        var eligible = false
        var reason: DisabledReason = DisabledReason.NONE

        while (reader.hasNext()) {
            when (reader.selectName(KEYS)) {
                0 -> eligible = reader.nextBoolean()
                1 -> reason = processDisabledReason(reader)
                else -> reader.skipValue()
            }
        }
        return Pair(eligible, reason)
    }

    private fun processDisabledReason(
        reader: JsonReader
    ): DisabledReason {
        return if (reader.peek() == JsonReader.Token.STRING) {
            mapStringToReason(reader.nextString())
        } else {
            mapStringToReason(reader.nextNull<String>())
        }
    }

    private fun mapStringToReason(reason: String?): DisabledReason =
        when (reason) {
            REGION -> DisabledReason.REGION
            INVALID_USER,
            KYC_TIER -> DisabledReason.KYC_TIER
            null -> DisabledReason.NONE
            else -> DisabledReason.OTHER
        }

    companion object {
        private val KEYS = JsonReader.Options.of("eligible", "ineligibilityReason")
        private const val REGION = "UNSUPPORTED_REGION"
        private const val KYC_TIER = "TIER_TOO_LOW"
        private const val INVALID_USER = "INVALID_USER"
    }
}