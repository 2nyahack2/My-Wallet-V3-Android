package piuk.blockchain.android.data.biometrics

interface BiometricAuth {
    val isFingerprintUnlockEnabled: Boolean

    val isFingerprintAvailable: Boolean

    val isHardwareDetected: Boolean

    val areFingerprintsEnrolled: Boolean

    fun setFingerprintUnlockEnabled(enabled: Boolean)
}