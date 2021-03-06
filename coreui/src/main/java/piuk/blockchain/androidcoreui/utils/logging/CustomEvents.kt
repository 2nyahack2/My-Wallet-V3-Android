package piuk.blockchain.androidcoreui.utils.logging

fun pairingEvent(pairingMethod: PairingMethod) =
    LoggingEvent("Wallet Pairing", mapOf("Pairing method" to pairingMethod.name))

@Suppress("UNUSED_PARAMETER")
enum class PairingMethod(name: String) {
    MANUAL("Manual"),
    QR_CODE("Qr code"),
    REVERSE("Reverse")
}

@Suppress("UNUSED_PARAMETER")
enum class AddressType(name: String) {
    PRIVATE_KEY("Private key")
}

fun appLaunchEvent(playServicesFound: Boolean) =
    LoggingEvent("App Launched",
        mapOf("Play Services found" to playServicesFound))

fun secondPasswordEvent(secondPasswordEnabled: Boolean) =
    LoggingEvent("Second password event",
        mapOf("Second password enabled" to secondPasswordEnabled))

fun launcherShortcutEvent(type: String) =
    LoggingEvent("Launcher Shortcut", mapOf("Launcher Shortcut used" to type))

fun walletUpgradeEvent(successful: Boolean) =
    LoggingEvent("Wallet Upgraded", mapOf("Successful" to successful))