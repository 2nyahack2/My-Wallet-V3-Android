package com.blockchain.nabu

import com.blockchain.nabu.models.responses.tokenresponse.NabuSessionTokenResponse

fun getEmptySessionToken(): NabuSessionTokenResponse =
    NabuSessionTokenResponse(
        "ID",
        "USER_ID",
        "TOKEN",
        true,
        "EXPIRES_AT",
        "INSERTED_AT",
        "UPDATED_AT"
    )