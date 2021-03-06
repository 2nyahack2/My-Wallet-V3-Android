package piuk.blockchain.androidcore.data.auth.metadata

import com.blockchain.android.testutils.rxInit
import com.blockchain.metadata.MetadataRepository
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.verifyNoMoreInteractions
import com.nhaarman.mockito_kotlin.whenever
import io.reactivex.Completable
import io.reactivex.Maybe
import org.junit.Rule
import org.junit.Test
import piuk.blockchain.androidcore.data.payload.PayloadDataManager

class WalletCredentialsMetadataUpdaterTest {

    @get:Rule
    val rxSchedulers = rxInit {
        mainTrampoline()
        ioTrampoline()
        computationTrampoline()
    }

    private val metadataRepository: MetadataRepository = mock()
    private val payloadDataManager: PayloadDataManager = mock()

    private val subject = WalletCredentialsMetadataUpdater(
        metadataRepository,
        payloadDataManager
    )

    @Test
    fun `no metadata stored`() {
        whenever(payloadDataManager.guid).thenReturn(GUID_1)
        whenever(payloadDataManager.tempPassword).thenReturn(PASSWORD_1)
        whenever(payloadDataManager.sharedKey).thenReturn(KEY_1)

        whenever(metadataRepository.loadMetadata(
            WalletCredentialsMetadata.WALLET_CREDENTIALS_METADATA_NODE, WalletCredentialsMetadata::class.java)
        ).thenReturn(
            Maybe.empty<WalletCredentialsMetadata>()
        )

        // This is nasty. I'd like a much better way of testing for chained subscriptions TODO
        var subFlag = false
        val updateResult = Completable.complete()
            .doOnSubscribe { subFlag = true }

        whenever(metadataRepository.saveMetadata(
            WalletCredentialsMetadata(GUID_1, PASSWORD_1, KEY_1),
            WalletCredentialsMetadata::class.java,
            WalletCredentialsMetadata.WALLET_CREDENTIALS_METADATA_NODE
        )).thenReturn(updateResult)

        subject.checkAndUpdate()
            .test()

        verify(payloadDataManager).guid
        verify(payloadDataManager).tempPassword
        verify(payloadDataManager).sharedKey

        verify(metadataRepository).loadMetadata(
            WalletCredentialsMetadata.WALLET_CREDENTIALS_METADATA_NODE,
            WalletCredentialsMetadata::class.java
        )
        verify(metadataRepository).saveMetadata(
            WalletCredentialsMetadata(GUID_1, PASSWORD_1, KEY_1),
            WalletCredentialsMetadata::class.java,
            WalletCredentialsMetadata.WALLET_CREDENTIALS_METADATA_NODE
        )

        assert(subFlag)

        verifyNoMoreInteractions(metadataRepository)
        verifyNoMoreInteractions(payloadDataManager)
    }

    @Test
    fun `metadata does not match stored`() {
        whenever(payloadDataManager.guid).thenReturn(GUID_1)
        whenever(payloadDataManager.tempPassword).thenReturn(PASSWORD_1)
        whenever(payloadDataManager.sharedKey).thenReturn(KEY_1)

        whenever(metadataRepository.loadMetadata(
            WalletCredentialsMetadata.WALLET_CREDENTIALS_METADATA_NODE, WalletCredentialsMetadata::class.java)
        ).thenReturn(
            Maybe.just(
                WalletCredentialsMetadata(
                    GUID_2,
                    PASSWORD_1,
                    KEY_1
                )
            )
        )

        subject.checkAndUpdate().test()

        verify(payloadDataManager).guid
        verify(payloadDataManager).tempPassword
        verify(payloadDataManager).sharedKey

        verify(metadataRepository).loadMetadata(
            WalletCredentialsMetadata.WALLET_CREDENTIALS_METADATA_NODE,
            WalletCredentialsMetadata::class.java
        )
        verify(metadataRepository).saveMetadata(
            WalletCredentialsMetadata(GUID_1, PASSWORD_1, KEY_1),
            WalletCredentialsMetadata::class.java,
            WalletCredentialsMetadata.WALLET_CREDENTIALS_METADATA_NODE
        )
        verifyNoMoreInteractions(metadataRepository)
        verifyNoMoreInteractions(payloadDataManager)
    }

    @Test
    fun `metadata matches stored`() {
        whenever(payloadDataManager.guid).thenReturn(GUID_1)
        whenever(payloadDataManager.tempPassword).thenReturn(PASSWORD_1)
        whenever(payloadDataManager.sharedKey).thenReturn(KEY_1)

        whenever(metadataRepository.loadMetadata(
            WalletCredentialsMetadata.WALLET_CREDENTIALS_METADATA_NODE, WalletCredentialsMetadata::class.java)
        ).thenReturn(
            Maybe.just(
                WalletCredentialsMetadata(
                    GUID_1,
                    PASSWORD_1,
                    KEY_1
                )
            )
        )

        subject.checkAndUpdate().test()

        verify(payloadDataManager).guid
        verify(payloadDataManager).tempPassword
        verify(payloadDataManager).sharedKey

        verify(metadataRepository).loadMetadata(
            WalletCredentialsMetadata.WALLET_CREDENTIALS_METADATA_NODE,
            WalletCredentialsMetadata::class.java
        )

        verifyNoMoreInteractions(metadataRepository)
        verifyNoMoreInteractions(payloadDataManager)
    }

    companion object {
        private const val GUID_1 = "12334442341q3-134234-1234"
        private const val GUID_2 = "1u3irwqp3r1q3-134234-1234"
        private const val PASSWORD_1 = "change me"
        private const val KEY_1 = "980886878687978"
    }
}