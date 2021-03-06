package info.blockchain.wallet.payload.data

@Deprecated("Only used in tests")
fun LegacyAddress.archive() {
    tag = LegacyAddress.ARCHIVED_ADDRESS
}

@Deprecated("Only used in tests")
fun LegacyAddress.unarchive() {
    tag = LegacyAddress.NORMAL_ADDRESS
}

val LegacyAddress.isArchived: Boolean
    get() = tag == LegacyAddress.ARCHIVED_ADDRESS
