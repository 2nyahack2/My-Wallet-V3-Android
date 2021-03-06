package info.blockchain.wallet.metadata

import info.blockchain.wallet.metadata.data.RemoteMetadataNodes
import org.bitcoinj.core.ECKey
import org.bitcoinj.crypto.DeterministicKey
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

class MetadataNodeFactory(
    guid: String,
    sharedKey: String,
    walletPassword: String,
    private val metadataDerivation: MetadataDerivation
) {
    private var sharedMetadataNode: DeterministicKey? = null
    var metadataNode: DeterministicKey? = null

    val secondPwNode = deriveSecondPasswordNode(guid, sharedKey, walletPassword)

    fun remoteMetadataHdNodes(masterKey: DeterministicKey): RemoteMetadataNodes { // Derive nodes
        // Save nodes hex on 2nd pw metadata
        return RemoteMetadataNodes().apply {
            mdid = metadataDerivation.deriveSharedMetadataNode(masterKey)
            metadata = metadataDerivation.deriveMetadataNode(masterKey)
        }
    }

    fun initNodes(remoteMetadataNodes: RemoteMetadataNodes): Boolean { // If not all nodes available fail.
        if (!remoteMetadataNodes.isAllNodesAvailable) {
            return false
        }
        sharedMetadataNode = metadataDerivation.deserializeMetadataNode(remoteMetadataNodes.mdid)
        metadataNode = metadataDerivation.deserializeMetadataNode(remoteMetadataNodes.metadata)

        return true
    }

    private fun deriveSecondPasswordNode(guid: String, sharedkey: String, password: String): Metadata {
        val md = MessageDigest.getInstance("SHA-256")
        val input = guid + sharedkey + password
        md.update(input.toByteArray(StandardCharsets.UTF_8))
        val entropy = md.digest()
        val bi = BigInteger(1, entropy)
        val key = ECKey.fromPrivate(bi)
        val address = metadataDerivation.deriveAddress(key)
        return Metadata(address, key, key.privKeyBytes, true, -1)
    }
}