package com.blockchain.nabu.datamanagers.repositories.serialization

import com.blockchain.nabu.models.responses.interest.AssetLimitsResponse
import com.blockchain.nabu.models.responses.interest.InterestLimitsFullResponse
import com.blockchain.nabu.models.responses.interest.InterestLimitsResponse
import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonReader

class InterestLimitsMapAdapter {
    @FromJson
    fun fromJson(reader: JsonReader): InterestLimitsFullResponse {
        val map = mutableMapOf<String, InterestLimitsResponse>()

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.peek()) {
                JsonReader.Token.NAME ->
                    when (reader.selectName(KEYS)) {
                        0 -> {
                            var currency = ""
                            var lockUpDuration: Int = -1
                            var maxWithdrawalAmount = ""
                            var minDepositAmount = ""

                            reader.beginObject()
                            while (reader.hasNext()) {
                                val k = reader.nextName()
                                reader.beginObject()
                                while (reader.hasNext()) {
                                    when (reader.selectName(SUB_KEYS)) {
                                        0 -> currency = reader.nextString()
                                        1 -> lockUpDuration = reader.nextInt()
                                        2 -> maxWithdrawalAmount =
                                            reader.nextString()
                                        3 -> minDepositAmount = reader.nextString()
                                    }
                                }
                                reader.endObject()
                                map[k] = InterestLimitsResponse(currency, lockUpDuration,
                                    maxWithdrawalAmount,
                                    minDepositAmount)
                            }
                            reader.endObject()
                        }
                        else -> reader.skipValue()
                    }
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        val limitsObject = AssetLimitsResponse(map)

        return InterestLimitsFullResponse(limitsObject)
    }

    companion object {
        private val KEYS = JsonReader.Options.of("limits")
        private val SUB_KEYS =
            JsonReader.Options.of("currency", "lockUpDuration", "maxWithdrawalAmount", "minDepositAmount")
    }
}