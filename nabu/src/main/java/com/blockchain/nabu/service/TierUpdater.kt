package com.blockchain.nabu.service

import io.reactivex.Completable

interface TierUpdater {

    /**
     * Set the tier the user wants to apply for.
     */
    fun setUserTier(tier: Int): Completable
}
