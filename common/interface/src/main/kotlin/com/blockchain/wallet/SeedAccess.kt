package com.blockchain.wallet

import io.reactivex.Maybe

interface SeedAccessWithoutPrompt {

    /**
     * The HD Seeds and master keys which come from the mnemonic.
     * If a second password is required and the wallet is not previously decoded, then it will be empty.
     */
    val seed: Maybe<Seed>

    /**
     * The seed given the pre-validated password.
     */
    fun seed(validatedSecondPassword: String?): Maybe<Seed>
}

interface SeedAccess : SeedAccessWithoutPrompt {

    /**
     * The HD Seeds and master keys which come from the mnemonic.
     * If a second password is required and not supplied, then it will be empty.
     * If the wallet has been decoded before, there may not be a second prompt, depending on caching lower down.
     */
    val seedPromptIfRequired: Maybe<Seed>
}

class Seed(
    val hdSeed: ByteArray,
    val masterKey: ByteArray
)
