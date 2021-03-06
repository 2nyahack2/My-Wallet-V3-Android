package com.blockchain.notifications.analytics

sealed class SettingsAnalyticsEvents(override val event: String, override val params: Map<String, String> = mapOf()) :
    AnalyticsEvent {
    object EmailClicked : SettingsAnalyticsEvents("settings_email_clicked")
    object PhoneClicked : SettingsAnalyticsEvents("settings_phone_clicked")
    object SwapLimitChecked : SettingsAnalyticsEvents("settings_swap_limit_clicked")
    object SwipeToReceiveSwitch : SettingsAnalyticsEvents("settings_swipe_to_receive_switch")
    object CloudBackupSwitch : SettingsAnalyticsEvents("settings_cloud_backup_switch")
    object WalletIdCopyClicked : SettingsAnalyticsEvents("settings_wallet_id_copy_click")
    object WalletIdCopyCopied : SettingsAnalyticsEvents("settings_wallet_id_copied")
    object EmailNotificationClicked : SettingsAnalyticsEvents("settings_email_notif_switch")
    object ChangePassClicked : SettingsAnalyticsEvents("settings_password_click")
    object TwoFactorAuthClicked : SettingsAnalyticsEvents("settings_two_fa_click")
    object ChangePinClicked : SettingsAnalyticsEvents("settings_change_pin_click")
    object BiometryAuthSwitch : SettingsAnalyticsEvents("settings_biometry_auth_switch")
    object PinChanged : SettingsAnalyticsEvents("settings_pin_selected")
    object PasswordChanged : SettingsAnalyticsEvents("settings_password_selected")
    object CurrencyChanged : SettingsAnalyticsEvents("settings_currency_selected")
}