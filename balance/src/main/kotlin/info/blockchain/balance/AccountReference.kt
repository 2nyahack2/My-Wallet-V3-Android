package info.blockchain.balance

@Deprecated("Switch to coincore")
sealed class AccountReference(
    val cryptoCurrency: CryptoCurrency,
    val label: String
) {
    abstract val receiveAddress: String

    data class BitcoinLike(
        private val _cryptoCurrency: CryptoCurrency,
        private val _label: String,
        val xpub: String
    ) : AccountReference(_cryptoCurrency, _label) {

        override val receiveAddress: String
            get() = xpub
    }

    data class Ethereum(
        private val _label: String,
        val address: String
    ) : AccountReference(CryptoCurrency.ETHER, _label) {
        override val receiveAddress: String
            get() = address
    }

    data class Xlm(
        private val _label: String,
        val accountId: String
    ) : AccountReference(CryptoCurrency.XLM, _label) {
        override val receiveAddress: String
            get() = accountId
    }

    data class Pax(
        private val _label: String,
        val ethAddress: String,
        val apiCode: String
    ) : AccountReference(CryptoCurrency.PAX, _label) {
        override val receiveAddress: String
            get() = ethAddress
    }

    data class Usdt(
        private val _label: String,
        val ethAddress: String,
        val apiCode: String
    ) : AccountReference(CryptoCurrency.USDT, _label) {
        override val receiveAddress: String
            get() = ethAddress
    }
}

@Deprecated("Switch to coincore")
typealias AccountReferenceList = List<AccountReference>