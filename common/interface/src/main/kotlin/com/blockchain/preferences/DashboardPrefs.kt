package com.blockchain.preferences

interface DashboardPrefs {
    var swapIntroCompleted: Boolean
    var isOnboardingComplete: Boolean
    var isCustodialIntroSeen: Boolean
    var remainingSendsWithoutBackup: Int

    val isTourComplete: Boolean
    val tourStage: String

    fun setTourComplete()
    fun setTourStage(stageName: String)
    fun resetTour()
}