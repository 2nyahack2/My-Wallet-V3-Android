package com.blockchain.koin

import com.blockchain.koin.modules.moshiModule
import com.blockchain.network.modules.apiModule
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.test.AutoCloseKoinTest

class MoshiModuleTest : AutoCloseKoinTest() {

    @Test
    fun `the moshi module injects at least one of the buy sell adapters`() {
        startKoin {
            modules(listOf(
                apiModule,
                moshiModule,
                nabuModule
            ))
        }
    }
}
