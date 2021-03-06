package info.blockchain.wallet.metadata

import info.blockchain.wallet.crypto.AESUtil
import info.blockchain.wallet.metadata.data.MetadataBody
import info.blockchain.wallet.util.FormatsUtil
import info.blockchain.wallet.util.MetadataUtil
import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.Maybe
import io.reactivex.Single
import io.reactivex.functions.Function
import io.reactivex.rxkotlin.zipWith
import org.json.JSONException
import org.spongycastle.util.encoders.Base64
import org.spongycastle.util.encoders.Hex
import retrofit2.HttpException
import java.util.concurrent.TimeUnit

class MetadataInteractor(private val metadataService: MetadataService) {

    fun fetchMagic(address: String): Single<ByteArray> =
        metadataService.getMetadata(address).map {
            val encryptedPayloadBytes = Base64.decode(it.payload.toByteArray(charset("utf-8")))
            if (it.prevMagicHash != null) {
                val prevMagicBytes = Hex.decode(it.prevMagicHash)
                MetadataUtil.magic(encryptedPayloadBytes, prevMagicBytes)
            } else {
                MetadataUtil.magic(encryptedPayloadBytes, null)
            }
        }

    fun putMetadata(payloadJson: String, metadata: Metadata): Completable {
        if (!FormatsUtil.isValidJson(payloadJson))
            return Completable.error(JSONException("Payload is not a valid json object."))

        val encryptedPayloadBytes: ByteArray = if (metadata.isEncrypted) {
            // base64 to buffer
            Base64.decode(AESUtil.encryptWithKey(metadata.encryptionKey, payloadJson))
        } else {
            payloadJson.toByteArray(charset("utf-8"))
        }

        return fetchMagic(metadata.address)
            .onErrorReturn { ByteArray(0) }
            .flatMapCompletable { m ->
                val magic = if (m.isEmpty()) null else m
                val message = MetadataUtil.message(encryptedPayloadBytes, magic)
                val sig = metadata.node.signMessage(String(Base64.encode(message)))
                val body = MetadataBody().apply {
                    version = METADATA_VERSION
                    payload = String(Base64.encode(encryptedPayloadBytes))
                    signature = sig
                    prevMagicHash = magic?.let {
                        Hex.toHexString(it)
                    }
                    typeId = metadata.type
                }
                metadataService.putMetadata(metadata.address, body)
            }.retryWhen { errors ->
                errors.zipWith(Flowable.range(0, FETCH_MAGIC_HASH_ATTEMPT_LIMIT))
                    .flatMap { (error, attempt) ->
                        if (error is HttpException && error.code() == 404 && attempt < FETCH_MAGIC_HASH_ATTEMPT_LIMIT) {
                            Flowable.timer(1, TimeUnit.SECONDS)
                        } else {
                            Flowable.error(error)
                        }
                    }
            }
    }

    fun loadRemoteMetadata(metadata: Metadata): Maybe<String> {
        return metadataService.getMetadata(metadata.address).toMaybe().map {
            if (metadata.isEncrypted) {
                AESUtil.decryptWithKey(metadata.encryptionKey, it.payload)
            } else {
                String(Base64.decode(it.payload))
            }
        }.onErrorResumeNext(Function {
            if (it is HttpException && it.code() == 404) // haven't been created
                Maybe.empty<String>()
            else Maybe.error<String>(it)
        })
    }

    companion object {
        const val METADATA_VERSION = 1
        const val FETCH_MAGIC_HASH_ATTEMPT_LIMIT = 1
    }
}