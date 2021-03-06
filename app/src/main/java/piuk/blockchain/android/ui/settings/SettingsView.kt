package piuk.blockchain.android.ui.settings

import androidx.annotation.StringRes
import com.blockchain.nabu.datamanagers.PaymentMethod
import com.blockchain.nabu.models.data.Bank
import com.blockchain.nabu.models.data.LinkBankTransfer
import com.blockchain.nabu.models.responses.nabu.KycTiers
import piuk.blockchain.androidcoreui.ui.base.View

interface SettingsView : View {

    fun setUpUi()

    fun showFingerprintDialog(pincode: String)

    fun showDisableFingerprintDialog()

    fun updateFingerprintPreferenceStatus()

    fun showNoFingerprintsAddedDialog()

    fun showProgress()

    fun hideProgress()

    fun showError(@StringRes message: Int)

    fun setGuidSummary(summary: String)

    fun setKycState(kycTiers: KycTiers)

    fun setEmailSummary(email: String, isVerified: Boolean)
    fun setEmailUnknown()

    fun setSmsSummary(summary: String, isVerified: Boolean)
    fun setSmsUnknown()

    fun setFiatSummary(summary: String)

    fun showEmailDialog(currentEmail: String, emailVerified: Boolean)

    fun showDialogTwoFA(authType: Int, smsVerified: Boolean)

    fun setEmailNotificationsVisibility(visible: Boolean)

    fun setEmailNotificationPref(enabled: Boolean)

    fun setPushNotificationPref(enabled: Boolean)

    fun setFingerprintVisibility(visible: Boolean)

    fun setTwoFaPreference(enabled: Boolean)

    fun setTorBlocked(blocked: Boolean)

    fun setPitLinkingState(isLinked: Boolean)

    fun updateCards(cards: List<PaymentMethod.Card>)

    fun updateLinkableBanks(linkableBanks: Set<LinkableBank>, linkedBanksCount: Int)

    fun updateLinkedBanks(banks: Set<Bank>)

    fun cardsEnabled(enabled: Boolean)

    fun banksEnabled(enabled: Boolean)

    fun setScreenshotsEnabled(enabled: Boolean)

    fun showDialogEmailVerification()

    fun showDialogVerifySms()

    fun showDialogMobile(authType: Int, isSmsVerified: Boolean, smsNumber: String)

    fun showDialogSmsVerified()

    fun goToPinEntryPage()

    fun launchThePitLandingActivity()

    fun launchThePit()

    fun setLauncherShortcutVisibility(visible: Boolean)

    fun showWarningDialog(@StringRes message: Int)

    fun launchKycFlow()

    fun linkBankWithPartner(linkBankTransfer: LinkBankTransfer)
}
