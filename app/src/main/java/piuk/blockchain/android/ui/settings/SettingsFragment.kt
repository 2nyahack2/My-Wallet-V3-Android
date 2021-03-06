package piuk.blockchain.android.ui.settings

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.ShortcutManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.text.Editable
import android.text.Html
import android.text.SpannableString
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity.RESULT_CANCELED
import androidx.appcompat.app.AppCompatActivity.RESULT_FIRST_USER
import androidx.appcompat.app.AppCompatActivity.RESULT_OK
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.content.ContextCompat
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.blockchain.koin.scopedInject
import com.blockchain.nabu.datamanagers.PaymentMethod
import com.blockchain.nabu.datamanagers.custodialwalletimpl.PaymentMethodType
import com.blockchain.nabu.models.data.Bank
import com.blockchain.nabu.models.data.LinkBankTransfer
import com.blockchain.nabu.models.responses.nabu.KycTiers
import com.blockchain.notifications.analytics.Analytics
import com.blockchain.notifications.analytics.AnalyticsEvent
import com.blockchain.notifications.analytics.AnalyticsEvents
import com.blockchain.notifications.analytics.SettingsAnalyticsEvents
import com.blockchain.preferences.CurrencyPrefs
import com.blockchain.ui.urllinks.URL_PRIVACY_POLICY
import com.blockchain.ui.urllinks.URL_TOS_POLICY
import com.google.android.play.core.review.ReviewInfo
import com.google.android.play.core.review.ReviewManager
import com.google.android.play.core.review.ReviewManagerFactory
import com.mukesh.countrypicker.fragments.CountryPicker
import info.blockchain.wallet.api.data.Settings
import info.blockchain.wallet.util.FormatsUtil
import info.blockchain.wallet.util.PasswordUtil
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import org.koin.android.ext.android.inject
import piuk.blockchain.android.BuildConfig
import piuk.blockchain.android.R
import piuk.blockchain.android.R.string.success
import piuk.blockchain.android.campaign.CampaignType
import piuk.blockchain.android.cards.CardDetailsActivity
import piuk.blockchain.android.cards.RemoveCardBottomSheet
import piuk.blockchain.android.data.biometrics.BiometricAuthError
import piuk.blockchain.android.data.biometrics.BiometricAuthLockout
import piuk.blockchain.android.data.biometrics.BiometricAuthLockoutPermanent
import piuk.blockchain.android.data.biometrics.BiometricAuthOther
import piuk.blockchain.android.data.biometrics.BiometricKeysInvalidated
import piuk.blockchain.android.data.biometrics.BiometricsCallback
import piuk.blockchain.android.data.biometrics.BiometricsController
import piuk.blockchain.android.data.biometrics.BiometricsNoSuitableMethods
import piuk.blockchain.android.simplebuy.RemoveLinkedBankBottomSheet
import piuk.blockchain.android.simplebuy.RemovePaymentMethodBottomSheetHost
import piuk.blockchain.android.simplebuy.SimpleBuyAnalytics
import piuk.blockchain.android.simplebuy.linkBankEventWithCurrency
import piuk.blockchain.android.ui.auth.KEY_VALIDATING_PIN_FOR_RESULT
import piuk.blockchain.android.ui.auth.PinEntryActivity
import piuk.blockchain.android.ui.auth.REQUEST_CODE_VALIDATE_PIN
import piuk.blockchain.android.ui.base.mvi.MviFragment.Companion.BOTTOM_SHEET
import piuk.blockchain.android.ui.customviews.PasswordStrengthView
import piuk.blockchain.android.ui.customviews.dialogs.MaterialProgressDialog
import piuk.blockchain.android.ui.dashboard.sheets.LinkBankAccountDetailsBottomSheet
import piuk.blockchain.android.ui.dashboard.sheets.LinkBankMethodChooserBottomSheet
import piuk.blockchain.android.ui.kyc.navhost.KycNavHostActivity
import piuk.blockchain.android.ui.linkbank.LinkBankActivity
import piuk.blockchain.android.ui.settings.preferences.BankPreference
import piuk.blockchain.android.ui.settings.preferences.CardPreference
import piuk.blockchain.android.ui.settings.preferences.KycStatusPreference
import piuk.blockchain.android.ui.settings.preferences.ThePitStatusPreference
import piuk.blockchain.android.ui.thepit.PitLaunchBottomDialog
import piuk.blockchain.android.ui.thepit.PitPermissionsActivity
import piuk.blockchain.android.util.AfterTextChangedWatcher
import piuk.blockchain.android.util.AndroidUtils
import piuk.blockchain.android.util.RootUtil
import piuk.blockchain.android.util.ViewUtils
import piuk.blockchain.androidcore.data.events.ActionEvent
import piuk.blockchain.androidcore.data.rxjava.RxBus
import piuk.blockchain.androidcore.utils.PersistentPrefs
import piuk.blockchain.androidcore.utils.helperfunctions.unsafeLazy
import piuk.blockchain.androidcoreui.ui.customviews.ToastCustom
import piuk.blockchain.androidcoreui.utils.logging.Logging
import timber.log.Timber
import java.util.Locale
import kotlin.math.roundToInt

class SettingsFragment : PreferenceFragmentCompat(), SettingsView, RemovePaymentMethodBottomSheetHost, BankLinkingHost,
    ReviewHost {

    // Profile
    private val kycStatusPref by lazy {
        findPreference<KycStatusPreference>("identity_verification")
    }
    private val guidPref by lazy {
        findPreference<Preference>("guid")
    }
    private val emailPref by lazy {
        findPreference<Preference>("email")
    }
    private val smsPref by lazy {
        findPreference<Preference>("mobile")
    }
    private val thePit by lazy {
        findPreference<ThePitStatusPreference>("the_pit")
    }
    private val banksPref by lazy {
        findPreference<PreferenceCategory>("banks")
    }
    private val cardsPref by lazy {
        findPreference<PreferenceCategory>("cards")
    }

    // Preferences
    private val fiatPref by lazy {
        findPreference<Preference>("fiat")
    }
    private val emailNotificationPref by lazy {
        findPreference<Preference>("email_notifications") as SwitchPreferenceCompat
    }
    private val pushNotificationPref by lazy {
        findPreference<Preference>("push_notifications") as SwitchPreferenceCompat
    }

    // Security
    private val fingerprintPref by lazy {
        findPreference<Preference>("fingerprint") as SwitchPreferenceCompat
    }
    private val twoStepVerificationPref by lazy {
        findPreference<SwitchPreferenceCompat>("2fa")
    }
    private val torPref by lazy {
        findPreference<SwitchPreferenceCompat>("tor")
    }
    private val launcherShortcutPrefs by lazy {
        findPreference<SwitchPreferenceCompat>("receive_shortcuts_enabled")
    }
    private val swipeToReceivePrefs by lazy {
        findPreference<SwitchPreferenceCompat>(PersistentPrefs.KEY_SWIPE_TO_RECEIVE_ENABLED)
    }
    private val screenshotPref by lazy {
        findPreference<SwitchPreferenceCompat>("screenshots_enabled")
    }
    private val cloudBackupPref by lazy {
        findPreference<SwitchPreferenceCompat>("cloud_backup")
    }

    private val settingsPresenter: SettingsPresenter by scopedInject()
    private val biometricsController: BiometricsController by scopedInject()
    private val currencyPrefs: CurrencyPrefs by inject()
    private val analytics: Analytics by inject()
    private val rxBus: RxBus by inject()

    private var pwStrength = 0
    private var progressDialog: MaterialProgressDialog? = null

    private val reviewManager: ReviewManager by lazy {
        ReviewManagerFactory.create(activity)
    }

    private var reviewInfo: ReviewInfo? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsPresenter.initView(this)
        settingsPresenter.onViewReady()

        analytics.logEvent(AnalyticsEvents.Settings)
        Logging.logContentView(javaClass.simpleName)

        initReviews()
    }

    private fun initReviews() {
        reviewManager.requestReviewFlow().addOnCompleteListener { request ->
            if (request.isSuccessful) {
                analytics.logEvent(ReviewAnalytics.REQUEST_REVIEW_SUCCESS)
                reviewInfo = request.result
            } else {
                analytics.logEvent(ReviewAnalytics.REQUEST_REVIEW_FAILURE)
            }
        }
    }

    override fun setUpUi() {
        // Profile
        kycStatusPref.onClick {
            settingsPresenter.onKycStatusClicked()
            analytics.logEvent(SettingsAnalyticsEvents.SwapLimitChecked)
        }
        kycStatusPref?.isVisible = false

        guidPref.onClick {
            showDialogGuid()
            analytics.logEvent(SettingsAnalyticsEvents.WalletIdCopyClicked)
        }
        emailPref.onClick {
            onUpdateEmailClicked()
            analytics.logEvent(SettingsAnalyticsEvents.EmailClicked)
        }
        smsPref.onClick {
            settingsPresenter.onVerifySmsRequested()
            analytics.logEvent(SettingsAnalyticsEvents.PhoneClicked)
        }

        thePit.onClick { settingsPresenter.onThePitClicked() }
        thePit?.isVisible = true

        // Preferences
        fiatPref.onClick { showDialogFiatUnits() }
        emailNotificationPref.onClick {
            showDialogEmailNotifications()
            analytics.logEvent(SettingsAnalyticsEvents.EmailNotificationClicked)
        }
        pushNotificationPref.onClick { showDialogPushNotifications() }

        // Security
        fingerprintPref.onClick {
            onFingerprintClicked()
            analytics.logEvent(SettingsAnalyticsEvents.BiometryAuthSwitch)
        }
        findPreference<Preference>("pin").onClick {
            showDialogChangePin()
            analytics.logEvent(SettingsAnalyticsEvents.ChangePinClicked)
        }
        twoStepVerificationPref.onClick {
            settingsPresenter.onTwoStepVerificationRequested()
            analytics.logEvent(SettingsAnalyticsEvents.TwoFactorAuthClicked)
        }

        findPreference<Preference>("change_pw").onClick {
            showDialogChangePasswordWarning()
            analytics.logEvent(SettingsAnalyticsEvents.ChangePassClicked)
        }

        torPref?.setOnPreferenceChangeListener { _, newValue ->
            settingsPresenter.updateTor(newValue as Boolean)
            true
        }

        screenshotPref?.setOnPreferenceChangeListener { _, newValue ->
            settingsPresenter.updatePreferences(
                PersistentPrefs.KEY_SCREENSHOTS_ENABLED,
                newValue as Boolean
            )
            true
        }

        launcherShortcutPrefs?.setOnPreferenceChangeListener { _, newValue ->
            if (!(newValue as Boolean) && AndroidUtils.is25orHigher()) {
                settingsActivity.getSystemService(
                    ShortcutManager::class.java
                )!!.removeAllDynamicShortcuts()
            }
            true
        }

        swipeToReceivePrefs?.setOnPreferenceChangeListener { _, newValue ->
            if (!(newValue as Boolean)) {
                settingsPresenter.clearOfflineAddressCache()
            } else {
                AlertDialog.Builder(settingsActivity, R.style.AlertDialogStyle)
                    .setTitle(R.string.swipe_receive_hint)
                    .setMessage(R.string.swipe_receive_address_info)
                    .setPositiveButton(android.R.string.ok) { _, _ -> }
                    .setCancelable(false)
                    .show()
            }
            analytics.logEvent(SettingsAnalyticsEvents.SwipeToReceiveSwitch)
            true
        }

        cloudBackupPref?.setOnPreferenceChangeListener { _, newValue ->
            settingsPresenter.updateCloudData(newValue as Boolean)
            analytics.logEvent(SettingsAnalyticsEvents.CloudBackupSwitch)
            true
        }

        // App
        findPreference<Preference>("about")?.apply {
            summary = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) ${BuildConfig.COMMIT_HASH}"
            onClick { onAboutClicked() }
        }

        findPreference<Preference>("tos").onClick { onTosClicked() }
        findPreference<Preference>("privacy").onClick { onPrivacyClicked() }

        val disableRootWarningPref = findPreference<Preference>(PersistentPrefs.KEY_ROOT_WARNING_DISABLED)
        if (disableRootWarningPref != null && !RootUtil().isDeviceRooted) {
            val appCategory = findPreference<Preference>("app") as PreferenceCategory
            appCategory.removePreference(disableRootWarningPref)
        }

        // Check if referred from Security Centre dialog
        val intent = activity?.intent
        when {
            intent == null -> {
            }
            intent.hasExtra(EXTRA_SHOW_TWO_FA_DIALOG) ->
                settingsPresenter.onTwoStepVerificationRequested()
            intent.hasExtra(EXTRA_SHOW_ADD_EMAIL_DIALOG) ->
                settingsPresenter.onEmailShowRequested()
        }
    }

    override fun showProgress() {
        progressDialog = MaterialProgressDialog(requireContext()).apply {
            setCancelable(false)
            setMessage(R.string.please_wait)
            show()
        }
    }

    override fun showEmailDialog(currentEmail: String, emailVerified: Boolean) {
        showUpdateEmailDialog(settingsActivity, settingsPresenter, currentEmail, emailVerified)
    }

    override fun hideProgress() {
        progressDialog?.dismiss()
        progressDialog = null
    }

    override fun showError(@StringRes message: Int) {
        ToastCustom.makeText(
            activity,
            getString(message),
            ToastCustom.LENGTH_SHORT,
            ToastCustom.TYPE_ERROR
        )
    }

    override fun showReviewDialog() {
        reviewInfo?.let {
            reviewManager.launchReviewFlow(activity, reviewInfo).addOnFailureListener {
                analytics.logEvent(ReviewAnalytics.LAUNCH_REVIEW_FAILURE)
                goToPlayStore()
            }.addOnCompleteListener {
                analytics.logEvent(ReviewAnalytics.LAUNCH_REVIEW_SUCCESS)
            }
        } ?: goToPlayStore()
    }

    private fun goToPlayStore() {
        val flags = Intent.FLAG_ACTIVITY_NO_HISTORY or
            Intent.FLAG_ACTIVITY_MULTIPLE_TASK or Intent.FLAG_ACTIVITY_NEW_DOCUMENT
        try {
            val appPackageName = requireActivity().packageName
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("market://details?id=$appPackageName")
            ).let {
                it.addFlags(flags)
                startActivity(it)
            }
        } catch (e: ActivityNotFoundException) {
            Timber.e(e, "Google Play Store not found")
        }
    }

    override fun showWarningDialog(@StringRes message: Int) {
        activity?.let {
            AlertDialog.Builder(it, R.style.AlertDialogStyle)
                .setTitle(R.string.app_name)
                .setMessage(message)
                .setCancelable(true)
                .setPositiveButton(android.R.string.ok, null)
                .setOnDismissListener { showDialogVerifySms() }
                .create()
                .show()
        }
    }

    private val compositeDisposable = CompositeDisposable()

    private val event by unsafeLazy {
        rxBus.register(ActionEvent::class.java)
    }

    override fun onResume() {
        super.onResume()

        compositeDisposable += event.subscribe {
            settingsPresenter.onViewReady()
        }
    }

    override fun onPause() {
        rxBus.unregister(ActionEvent::class.java, event)
        compositeDisposable.clear()
        super.onPause()
    }

    override fun onCreatePreferences(bundle: Bundle?, s: String?) {
        preferenceScreen?.removeAll()
        addPreferencesFromResource(R.xml.settings)
    }

    override fun setKycState(kycTiers: KycTiers) {
        kycStatusPref?.setValue(kycTiers)
        kycStatusPref?.isVisible = kycTiers.isInitialised()
    }

    override fun setGuidSummary(summary: String) {
        guidPref?.summary = summary
    }

    override fun setEmailSummary(email: String, isVerified: Boolean) {
        emailPref?.summary = when {
            email.isEmpty() -> getString(R.string.not_specified)
            isVerified -> "$email  (${getString(R.string.verified)})"
            else -> "$email  (${getString(R.string.unverified)})"
        }
    }

    override fun setEmailUnknown() {
        emailPref?.summary = getString(R.string.not_specified)
    }

    override fun setSmsSummary(smsNumber: String, isVerified: Boolean) {
        smsPref?.summary = when {
            smsNumber.isEmpty() -> getString(R.string.not_specified)
            isVerified -> "$smsNumber (${getString(R.string.verified)})"
            else -> "$smsNumber (${getString(R.string.unverified)})"
        }
    }

    override fun setSmsUnknown() {
        emailPref?.summary = getString(R.string.not_specified)
    }

    override fun setFiatSummary(summary: String) {
        fiatPref?.summary = summary
    }

    override fun setEmailNotificationsVisibility(visible: Boolean) {
        emailNotificationPref.isVisible = visible
    }

    override fun setEmailNotificationPref(enabled: Boolean) {
        emailNotificationPref.isChecked = enabled
    }

    override fun setPushNotificationPref(enabled: Boolean) {
        pushNotificationPref.isChecked = enabled
    }

    override fun setFingerprintVisibility(visible: Boolean) {
        fingerprintPref.isVisible = visible
    }

    override fun setTwoFaPreference(enabled: Boolean) {
        twoStepVerificationPref?.isChecked = enabled
    }

    override fun setTorBlocked(blocked: Boolean) {
        torPref?.isChecked = blocked
    }

    override fun setPitLinkingState(isLinked: Boolean) {
        thePit?.setValue(isLinked)
    }

    override fun updateLinkableBanks(linkableBanks: Set<LinkableBank>, linkedBanksCount: Int) {
        if (linkableBanks.isEmpty()) {
            banksPref?.isVisible = false
        } else {
            linkableBanks.forEach { linkableBank ->
                banksPref?.findPreference<BankPreference>(LINK_BANK_KEY.plus(linkableBank.hashCode()))?.let {
                    it.order = it.order + linkedBanksCount + linkableBanks.indexOf(linkableBank)
                } ?: banksPref?.addPreference(
                    BankPreference(context = requireContext(), fiatCurrency = linkableBank.currency).apply {
                        onClick {
                            linkBank(linkableBank)
                        }
                        key = LINK_BANK_KEY.plus(linkableBank.hashCode())
                    }
                )
            }
        }
    }

    override fun updateLinkedBanks(banks: Set<Bank>) {
        val existingBanks = prefsExistingBanks()

        val newBanks = banks.filterNot { existingBanks.contains(it.id) }

        newBanks.forEach { bank ->
            banksPref?.addPreference(
                BankPreference(context = requireContext(), bank = bank, fiatCurrency = bank.currency).apply {
                    onClick {
                        removeBank(bank)
                    }
                    key = bank.id
                }
            )
        }
    }

    private fun removeBank(bank: Bank) {
        RemoveLinkedBankBottomSheet.newInstance(bank).show(childFragmentManager, BOTTOM_SHEET)
    }

    override fun linkBankWithWireTransfer(currency: String) {
        LinkBankAccountDetailsBottomSheet.newInstance(currency).show(childFragmentManager, BOTTOM_SHEET)
        analytics.logEvent(linkBankEventWithCurrency(SimpleBuyAnalytics.LINK_BANK_CLICKED, currency))
    }

    private fun linkBank(linkableBank: LinkableBank) {
        require(linkableBank.linkMethods.isNotEmpty())
        if (linkableBank.linkMethods.size > 1) {
            showDialogForLinkBankMethodChooser(linkableBank)
        } else {
            when (linkableBank.linkMethods[0]) {
                PaymentMethodType.FUNDS -> linkBankWithWireTransfer(linkableBank.currency)
                PaymentMethodType.BANK_TRANSFER -> linkBankWithBankTransfer(linkableBank.currency)
                else -> throw IllegalStateException("Not valid linkable bank type")
            }
        }
    }

    override fun linkBankWithBankTransfer(currency: String) {
        settingsPresenter.linkBank(currency)
    }

    private fun showDialogForLinkBankMethodChooser(linkableBank: LinkableBank) {
        LinkBankMethodChooserBottomSheet.newInstance(linkableBank).show(childFragmentManager, BOTTOM_SHEET)
    }

    override fun updateCards(cards: List<PaymentMethod.Card>) {

        val existingCards = prefsExistingCards()

        val newCards = cards.filterNot { existingCards.contains(it.cardId) }

        newCards.forEach { card ->
            cardsPref?.addPreference(
                CardPreference(context = requireContext(), card = card).apply {
                    onClick {
                        RemoveCardBottomSheet.newInstance(card).show(childFragmentManager, BOTTOM_SHEET)
                    }
                    key = card.cardId
                }
            )
        }

        cardsPref?.findPreference<CardPreference>(ADD_CARD_KEY)?.let {
            it.order = it.order + newCards.size + 1
        } ?: cardsPref?.addPreference(
            CardPreference(context = requireContext(), card = PaymentMethod.Undefined).apply {
                onClick {
                    addNewCard()
                }
                key = ADD_CARD_KEY
            }
        )
    }

    private fun prefsExistingCards(): List<String> {
        val existingCards = mutableListOf<String>()

        for (i in (0 until (cardsPref?.preferenceCount ?: 0))) {
            existingCards.add(cardsPref?.getPreference(i)?.key.takeIf { it != ADD_CARD_KEY } ?: continue)
        }
        return existingCards
    }

    private fun addNewCard() {
        val intent = Intent(activity, CardDetailsActivity::class.java)
        startActivityForResult(intent, CardDetailsActivity.ADD_CARD_REQUEST_CODE)
        analytics.logEvent(SimpleBuyAnalytics.SETTINGS_ADD_CARD)
    }

    override fun setScreenshotsEnabled(enabled: Boolean) {
        screenshotPref?.isChecked = enabled
    }

    private fun prefsExistingBanks(): List<String> {
        val existingBanks = mutableListOf<String>()

        for (i in (0 until (banksPref?.preferenceCount ?: 0))) {
            existingBanks.add(
                banksPref?.getPreference(i)?.key.takeIf {
                    it?.contains(LINK_BANK_KEY)?.not() ?: false
                } ?: continue
            )
        }
        return existingBanks
    }

    override fun setLauncherShortcutVisibility(visible: Boolean) {
        launcherShortcutPrefs?.isVisible = visible
    }

    private fun onFingerprintClicked() {
        settingsPresenter.onFingerprintClicked()
    }

    override fun showDisableFingerprintDialog() {
        AlertDialog.Builder(settingsActivity, R.style.AlertDialogStyle)
            .setTitle(R.string.app_name)
            .setMessage(R.string.biometric_disable_message)
            .setCancelable(true)
            .setPositiveButton(R.string.common_yes) { _, _ ->
                settingsPresenter.setFingerprintUnlockEnabled(
                    false
                )
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                updateFingerprintPreferenceStatus()
            }
            .show()
    }

    override fun showNoFingerprintsAddedDialog() {
        updateFingerprintPreferenceStatus()
        AlertDialog.Builder(settingsActivity, R.style.AlertDialogStyle)
            .setTitle(R.string.app_name)
            .setMessage(R.string.fingerprint_no_fingerprints_added)
            .setCancelable(true)
            .setPositiveButton(R.string.common_yes) { _, _ ->
                startActivityForResult(
                    Intent(
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                            android.provider.Settings.ACTION_SECURITY_SETTINGS
                        } else {
                            android.provider.Settings.ACTION_BIOMETRIC_ENROLL
                        }
                    ),
                    REQUEST_CODE_BIOMETRIC_ENROLLMENT
                )
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun updateFingerprintPreferenceStatus() {
        fingerprintPref.isChecked = settingsPresenter.isFingerprintUnlockEnabled
    }

    override fun showFingerprintDialog(pincode: String) {
        biometricsController.init(this, BiometricsController.BiometricsType.TYPE_REGISTER, object : BiometricsCallback {
            override fun onAuthSuccess(data: String) {
                settingsPresenter.setFingerprintUnlockEnabled(true)
                fingerprintPref.isChecked = settingsPresenter.isFingerprintUnlockEnabled
            }

            override fun onAuthFailed(error: BiometricAuthError) {
                handleAuthFailed(error)
            }

            override fun onAuthCancelled() {
                settingsPresenter.setFingerprintUnlockEnabled(false)
                fingerprintPref.isChecked = settingsPresenter.isFingerprintUnlockEnabled
            }
        })

        biometricsController.authenticateForRegistration()
    }

    private fun handleAuthFailed(error: BiometricAuthError) {
        when (error) {
            is BiometricKeysInvalidated -> BiometricsController.showActionableInvalidatedKeysDialog(requireContext(),
                positiveActionCallback = {
                    settingsPresenter.onFingerprintClicked()
                },
                negativeActionCallback = {
                    startActivityForResult(
                        Intent(
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                                android.provider.Settings.ACTION_SECURITY_SETTINGS
                            } else {
                                android.provider.Settings.ACTION_BIOMETRIC_ENROLL
                            }
                        ),
                        REQUEST_CODE_BIOMETRIC_ENROLLMENT
                    )
                })
            is BiometricsNoSuitableMethods -> showNoFingerprintsAddedDialog()
            is BiometricAuthLockout -> BiometricsController.showAuthLockoutDialog(requireContext())
            is BiometricAuthLockoutPermanent -> BiometricsController.showPermanentAuthLockoutDialog(requireContext())
            is BiometricAuthOther -> BiometricsController.showBiometricsGenericError(requireContext(), error.error)
            else -> {
                // do nothing
            }
        }
        settingsPresenter.setFingerprintUnlockEnabled(false)
        fingerprintPref.isChecked = settingsPresenter.isFingerprintUnlockEnabled
    }

    override fun showDialogSmsVerified() {
        AlertDialog.Builder(settingsActivity, R.style.AlertDialogStyle)
            .setTitle(success)
            .setMessage(R.string.sms_verified)
            .setPositiveButton(R.string.dialog_continue) { _, _ -> settingsPresenter.onTwoStepVerificationRequested() }
            .show()
    }

    override fun goToPinEntryPage() {
        val intent = Intent(activity, PinEntryActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        analytics.logEvent(SettingsAnalyticsEvents.PinChanged)
    }

    override fun launchThePitLandingActivity() {
        PitPermissionsActivity.start(requireActivity(), "")
    }

    override fun launchThePit() {
        PitLaunchBottomDialog.launch(requireActivity())
    }

    private fun onUpdateEmailClicked() {
        settingsPresenter.onEmailShowRequested()
    }

    private fun onAboutClicked() {
        AboutDialog().show(childFragmentManager, "ABOUT_DIALOG")
    }

    private fun onTosClicked() {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(URL_TOS_POLICY)))
    }

    private fun onPrivacyClicked() {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(URL_PRIVACY_POLICY)))
    }

    override fun showDialogEmailVerification() {
        // Slight delay to prevent UI blinking issues
        val handler = Handler()
        handler.postDelayed({
            if (activity != null) {
                AlertDialog.Builder(settingsActivity, R.style.AlertDialogStyle)
                    .setTitle(R.string.verify)
                    .setMessage(R.string.verify_email_notice)
                    .setCancelable(true)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }
        }, 300)
    }

    override fun showDialogMobile(authType: Int, isSmsVerified: Boolean, smsNumber: String) {
        if (authType != Settings.AUTH_TYPE_OFF) {
            AlertDialog.Builder(settingsActivity, R.style.AlertDialogStyle)
                .setTitle(R.string.warning)
                .setMessage(R.string.disable_2fa_first)
                .setPositiveButton(android.R.string.ok, null)
                .create()
                .show()
        } else {
            val inflater = settingsActivity.layoutInflater
            val smsPickerView = inflater.inflate(R.layout.include_sms_update, null)
            val mobileNumber = smsPickerView.findViewById<EditText>(R.id.etSms)
            val countryTextView = smsPickerView.findViewById<TextView>(R.id.tvCountry)
            val mobileNumberTextView = smsPickerView.findViewById<TextView>(R.id.tvSms)

            val picker = CountryPicker.newInstance(getString(R.string.select_country))
            val country = picker.getUserCountryInfo(requireActivity())
            if (country.dialCode == "+93") {
                setCountryFlag(countryTextView, "+1", R.drawable.flag_us)
            } else {
                setCountryFlag(countryTextView, country.dialCode, country.flag)
            }

            countryTextView.setOnClickListener {
                picker.show(requireFragmentManager(), "COUNTRY_PICKER")
                picker.setListener { _, _, dialCode, flagDrawableResID ->
                    setCountryFlag(countryTextView, dialCode, flagDrawableResID)
                    picker.dismiss()
                }
            }

            if (smsNumber.isNotEmpty()) {
                mobileNumberTextView.text = smsNumber
                mobileNumberTextView.visibility = View.VISIBLE
            }

            val alertDialogSmsBuilder = AlertDialog.Builder(settingsActivity, R.style.AlertDialogStyle)
                .setTitle(R.string.mobile)
                .setMessage(getString(R.string.mobile_description))
                .setView(smsPickerView)
                .setCancelable(false)
                .setPositiveButton(R.string.update, null)
                .setNegativeButton(android.R.string.cancel, null)

            if (isSmsVerified && smsNumber.isNotEmpty()) {
                alertDialogSmsBuilder.setNeutralButton(R.string.verify) { dialogInterface, i ->
                    settingsPresenter.updateSms(
                        smsNumber
                    )
                }
            }

            val dialog = alertDialogSmsBuilder.create()
            dialog.setOnShowListener {
                val positive = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
                positive.setOnClickListener {
                    val sms = countryTextView.text.toString() + mobileNumber.text.toString()

                    if (!FormatsUtil.isValidMobileNumber(sms)) {
                        showCustomToast(R.string.invalid_mobile)
                    } else {
                        settingsPresenter.updateSms(sms)
                        dialog.dismiss()
                    }
                }
            }

            dialog.show()
        }
    }

    private fun showDialogGuid() {
        AlertDialog.Builder(settingsActivity, R.style.AlertDialogStyle)
            .setTitle(R.string.app_name)
            .setMessage(R.string.guid_to_clipboard)
            .setCancelable(false)
            .setPositiveButton(R.string.common_yes) { _, _ ->
                val clipboard =
                    settingsActivity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("guid", guidPref!!.summary)
                clipboard.setPrimaryClip(clip)
                showCustomToast(R.string.copied_to_clipboard)
                analytics.logEvent(SettingsAnalyticsEvents.WalletIdCopyCopied)
            }
            .setNegativeButton(R.string.common_no, null)
            .show()
    }

    private fun showDialogFiatUnits() {
        val currencies = settingsPresenter.currencyLabels
        val strCurrency = currencyPrefs.selectedFiatCurrency
        var selected = 0
        for (i in currencies.indices) {
            if (currencies[i].endsWith(strCurrency)) {
                selected = i
                break
            }
        }

        AlertDialog.Builder(settingsActivity, R.style.AlertDialogStyle)
            .setTitle(R.string.select_currency)
            .setSingleChoiceItems(currencies, selected) { dialog, which ->
                val fiatUnit = currencies[which].substring(currencies[which].length - 3)
                settingsPresenter.updateFiatUnit(fiatUnit)
                dialog.dismiss()
            }
            .show()
    }

    private val settingsActivity: Activity
        get() = activity ?: throw IllegalStateException("Activity not found")

    override fun showDialogVerifySms() {
        val editText = AppCompatEditText(settingsActivity)
        editText.setSingleLine(true)

        val dialog = AlertDialog.Builder(settingsActivity, R.style.AlertDialogStyle)
            .setTitle(R.string.verify_mobile)
            .setMessage(R.string.verify_sms_summary)
            .setView(ViewUtils.getAlertDialogPaddedView(requireContext(), editText))
            .setCancelable(false)
            .setPositiveButton(R.string.verify, null)
            .setNegativeButton(android.R.string.cancel, null)
            .setNeutralButton(R.string.resend) { _, _ ->
                settingsPresenter.resendSms()
            }
            .create()

        dialog.setOnShowListener {
            val positive = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
            positive.setOnClickListener {
                val codeS = editText.text.toString()
                if (codeS.isNotEmpty()) {
                    settingsPresenter.verifySms(codeS)
                    dialog.dismiss()
                    ViewUtils.hideKeyboard(settingsActivity)
                }
            }
        }
        dialog.show()
    }

    private fun showDialogChangePin() {
        val intent = Intent(activity, PinEntryActivity::class.java)
        intent.putExtra(KEY_VALIDATING_PIN_FOR_RESULT, true)
        startActivityForResult(intent, REQUEST_CODE_VALIDATE_PIN)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK) {
            when (requestCode) {
                REQUEST_CODE_VALIDATE_PIN -> {
                    settingsPresenter.pinCodeValidatedForChange()
                }
                CardDetailsActivity.ADD_CARD_REQUEST_CODE -> {
                    updateCards(
                        listOf(
                            (data?.extras?.getSerializable(CardDetailsActivity.CARD_KEY) as? PaymentMethod.Card)
                                ?: return
                        )
                    )
                }
                LinkBankActivity.LINK_BANK_REQUEST_CODE -> {
                    settingsPresenter.updateBanks()
                }
                REQUEST_CODE_BIOMETRIC_ENROLLMENT -> {
                    settingsPresenter.onFingerprintClicked()
                }
            }
        } else if (resultCode == RESULT_FIRST_USER || resultCode == RESULT_CANCELED) {
            if (requestCode == REQUEST_CODE_BIOMETRIC_ENROLLMENT) {
                settingsPresenter.onFingerprintClicked()
            }
        }
    }

    private fun showDialogEmailNotifications() {
        val dialog = AlertDialog.Builder(settingsActivity, R.style.AlertDialogStyle)
            .setTitle(R.string.email_notifications)
            .setMessage(R.string.email_notifications_summary)
            .setPositiveButton(R.string.enable) { _, _ ->
                settingsPresenter.updateEmailNotification(
                    true
                )
            }
            .setNegativeButton(R.string.disable) { _, _ ->
                settingsPresenter.updateEmailNotification(
                    false
                )
            }
            .create()

        dialog.setOnCancelListener {
            emailNotificationPref.isChecked = !emailNotificationPref.isChecked
        }
        dialog.show()
    }

    private fun showDialogPushNotifications() {
        val dialog = AlertDialog.Builder(settingsActivity, R.style.AlertDialogStyle)
            .setTitle(R.string.push_notifications)
            .setMessage(R.string.push_notifications_summary)
            .setPositiveButton(R.string.enable) { _, _ -> settingsPresenter.enablePushNotifications() }
            .setNegativeButton(R.string.disable) { _, _ -> settingsPresenter.disablePushNotifications() }
            .create()

        dialog.setOnCancelListener {
            pushNotificationPref.isChecked = !pushNotificationPref.isChecked
        }
        dialog.show()
    }

    private fun showDialogChangePasswordWarning() {
        AlertDialog.Builder(settingsActivity, R.style.AlertDialogStyle)
            .setTitle(R.string.warning)
            .setMessage(R.string.change_password_summary)
            .setPositiveButton(R.string.dialog_continue) { _, _ -> showDialogChangePassword() }
            .show()
    }

    private fun showDialogChangePassword() {
        val inflater = settingsActivity.layoutInflater
        val pwLayout = inflater.inflate(R.layout.modal_change_password2, null) as LinearLayout

        val currentPassword = pwLayout.findViewById<AppCompatEditText>(R.id.current_password)
        val newPassword = pwLayout.findViewById<AppCompatEditText>(R.id.new_password)
        val newPasswordConfirmation = pwLayout.findViewById<AppCompatEditText>(R.id.confirm_password)
        val passwordStrength = pwLayout.findViewById<PasswordStrengthView>(R.id.password_strength)

        newPassword.addTextChangedListener(object : AfterTextChangedWatcher() {
            override fun afterTextChanged(editable: Editable) {
                newPassword.postDelayed({
                    if (activity != null && !settingsActivity.isFinishing) {
                        passwordStrength.visibility = View.VISIBLE
                        setPasswordStrength(editable.toString(), passwordStrength)
                    }
                }, 200)
            }
        })

        val alertDialog = AlertDialog.Builder(settingsActivity, R.style.AlertDialogStyle)
            .setTitle(R.string.change_password)
            .setCancelable(false)
            .setView(pwLayout)
            .setPositiveButton(R.string.update, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        alertDialog.setOnShowListener {
            val buttonPositive = alertDialog.getButton(AlertDialog.BUTTON_POSITIVE)
            buttonPositive.setOnClickListener {

                val currentPw = currentPassword.text.toString()
                val newPw = newPassword.text.toString()
                val newConfirmedPw = newPasswordConfirmation.text.toString()
                val walletPassword = settingsPresenter.tempPassword

                if (currentPw != newPw) {
                    if (currentPw == walletPassword) {
                        if (newPw == newConfirmedPw) {
                            if (newConfirmedPw.length < 4 || newConfirmedPw.length > 255) {
                                ToastCustom.makeText(
                                    activity,
                                    getString(R.string.invalid_password),
                                    ToastCustom.LENGTH_SHORT,
                                    ToastCustom.TYPE_ERROR
                                )
                            } else if (pwStrength < 50) {
                                AlertDialog.Builder(settingsActivity, R.style.AlertDialogStyle)
                                    .setTitle(R.string.app_name)
                                    .setMessage(R.string.weak_password)
                                    .setCancelable(false)
                                    .setPositiveButton(R.string.common_yes) { _, _ ->
                                        newPasswordConfirmation.setText("")
                                        newPasswordConfirmation.requestFocus()
                                        newPassword.setText("")
                                        newPassword.requestFocus()
                                    }
                                    .setNegativeButton(R.string.polite_no) { _, _ ->
                                        alertDialog.dismiss()
                                        settingsPresenter.updatePassword(
                                            newConfirmedPw,
                                            walletPassword
                                        )
                                    }
                                    .show()
                            } else {
                                alertDialog.dismiss()
                                settingsPresenter.updatePassword(newConfirmedPw, walletPassword)
                            }
                        } else {
                            newPasswordConfirmation.setText("")
                            newPasswordConfirmation.requestFocus()
                            showCustomToast(R.string.password_mismatch_error)
                        }
                    } else {
                        currentPassword.setText("")
                        currentPassword.requestFocus()
                        showCustomToast(R.string.invalid_password)
                    }
                } else {
                    newPassword.setText("")
                    newPasswordConfirmation.setText("")
                    newPassword.requestFocus()
                    showCustomToast(R.string.change_password_new_matches_current)
                }
            }
        }
        alertDialog.show()
    }

    private fun showCustomToast(@StringRes stringId: Int) {
        ToastCustom.makeText(
            activity,
            getString(stringId),
            ToastCustom.LENGTH_LONG,
            ToastCustom.TYPE_ERROR
        )
    }

    override fun showDialogTwoFA(authType: Int, smsVerified: Boolean) {
        if (authType == Settings.AUTH_TYPE_GOOGLE_AUTHENTICATOR ||
            authType == Settings.AUTH_TYPE_YUBI_KEY
        ) {
            twoStepVerificationPref?.isChecked = true
            AlertDialog.Builder(settingsActivity, R.style.AlertDialogStyle)
                .setTitle(R.string.warning)
                .setCancelable(false)
                .setMessage(R.string.disable_online_only)
                .setPositiveButton(android.R.string.ok, null)
                .create()
                .show()
        } else if (!smsVerified) {
            twoStepVerificationPref?.isChecked = false
            settingsPresenter.onVerifySmsRequested()
        } else {
            val message = Html.fromHtml(getString(R.string.two_fa_description, URL_LOGIN))
            val spannable = SpannableString(message)
            Linkify.addLinks(spannable, Linkify.WEB_URLS)

            val alertDialogBuilder = AlertDialog.Builder(settingsActivity, R.style.AlertDialogStyle)
                .setTitle(R.string.two_fa)
                .setCancelable(false)
                .setMessage(spannable)
                .setNeutralButton(android.R.string.cancel) { _, _ ->
                    twoStepVerificationPref?.isChecked =
                        authType != Settings.AUTH_TYPE_OFF
                }

            if (authType != Settings.AUTH_TYPE_OFF) {
                alertDialogBuilder.setNegativeButton(R.string.disable) { _, _ ->
                    settingsPresenter.updateTwoFa(
                        Settings.AUTH_TYPE_OFF
                    )
                }
            } else {
                alertDialogBuilder.setPositiveButton(R.string.enable) { _, _ ->
                    settingsPresenter.updateTwoFa(
                        Settings.AUTH_TYPE_SMS
                    )
                }
            }
            val alertDialog = alertDialogBuilder.create()
            alertDialog.show()
            (alertDialog.findViewById<View>(android.R.id.message) as TextView).movementMethod =
                LinkMovementMethod.getInstance()
        }
    }

    private fun setPasswordStrength(
        pw: String,
        passwordStrengthView: PasswordStrengthView
    ) {
        if (activity != null && !settingsActivity.isFinishing) {
            pwStrength = PasswordUtil.getStrength(pw).roundToInt()

            // red
            var pwStrengthLevel = 0

            when {
                pwStrength >= 75 -> // green
                    pwStrengthLevel = 3
                pwStrength >= 50 -> // green
                    pwStrengthLevel = 2
                pwStrength >= 25 -> // orange
                    pwStrengthLevel = 1
            }

            passwordStrengthView.setStrengthProgress(pwStrength)
            passwordStrengthView.updateLevelUI(pwStrengthLevel)
        }
    }

    override fun launchKycFlow() {
        KycNavHostActivity.start(requireContext(), CampaignType.Swap, true)
        requireActivity().finish()
    }

    private fun setCountryFlag(tvCountry: TextView, dialCode: String, flagResourceId: Int) {
        tvCountry.text = dialCode
        val drawable = ContextCompat.getDrawable(settingsActivity, flagResourceId)
        drawable!!.alpha = 30
        tvCountry.background = drawable
    }

    override fun onDestroy() {
        super.onDestroy()
        hideProgress()
        settingsPresenter.onViewDestroyed()
    }

    companion object {
        const val URL_LOGIN = "<a href=\"https://login.blockchain.com/\">login.blockchain.com</a>"

        internal const val EXTRA_SHOW_ADD_EMAIL_DIALOG = "show_add_email_dialog"
        internal const val EXTRA_SHOW_TWO_FA_DIALOG = "show_two_fa_dialog"
        private const val ADD_CARD_KEY = "ADD_CARD_KEY"
        private const val LINK_BANK_KEY = "ADD_BANK_KEY"
        private const val REQUEST_CODE_BIOMETRIC_ENROLLMENT = 666
    }

    override fun onCardRemoved(cardId: String) {
        cardsPref?.findPreference<CardPreference>(cardId)?.let {
            cardsPref?.removePreference(it)
        }
    }

    override fun onLinkedBankRemoved(bankId: String) {
        banksPref?.findPreference<BankPreference>(bankId)?.let {
            banksPref?.removePreference(it)
        }
    }

    override fun onSheetClosed() {}

    override fun cardsEnabled(enabled: Boolean) {
        cardsPref?.isVisible = enabled
    }

    override fun banksEnabled(enabled: Boolean) {
        banksPref?.isVisible = enabled
    }

    override fun linkBankWithPartner(linkBankTransfer: LinkBankTransfer) {
        startActivityForResult(
            LinkBankActivity.newInstance(linkBankTransfer, requireContext()), LinkBankActivity.LINK_BANK_REQUEST_CODE
        )
    }
}

fun Preference?.onClick(onClick: () -> Unit) {
    this?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
        onClick()
        true
    }
}

interface ReviewHost {
    fun showReviewDialog()
}

enum class ReviewAnalytics : AnalyticsEvent {
    REQUEST_REVIEW_SUCCESS,
    REQUEST_REVIEW_FAILURE,
    LAUNCH_REVIEW_SUCCESS,
    LAUNCH_REVIEW_FAILURE;

    override val event: String
        get() = name.toLowerCase(Locale.ENGLISH)
    override val params: Map<String, String>
        get() = emptyMap()
}

interface BankLinkingHost {
    fun linkBankWithWireTransfer(currency: String)
    fun linkBankWithBankTransfer(currency: String)
}