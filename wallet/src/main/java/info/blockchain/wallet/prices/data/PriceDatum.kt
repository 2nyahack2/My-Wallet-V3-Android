package info.blockchain.wallet.prices.data

data class PriceDatum(
    val timestamp: Long = 0,
    val price: Double? = 0.0,
    val volume24h: Double? = 0.0
)