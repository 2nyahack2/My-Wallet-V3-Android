package info.blockchain.wallet.prices

/**
 * A simple class of timescale constants for the [PriceEndpoints] methods.
 */
enum class TimeInterval(val intervalSeconds: Int) {
    FIFTEEN_MINUTES(900),
    ONE_HOUR(3600),
    TWO_HOURS(7200),
    ONE_DAY(86400),
    FIVE_DAYS(432000)
}

enum class TimeAgo(val epoch: Long) {
    ONE_DAY((System.currentTimeMillis() / 1000) - TimeInterval.ONE_DAY.intervalSeconds)
}