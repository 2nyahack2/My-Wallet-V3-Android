package piuk.blockchain.android.ui.launcher;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import info.blockchain.wallet.api.data.Settings;
import info.blockchain.wallet.payload.data.Wallet;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.Single;
import piuk.blockchain.android.BlockchainTestApplication;
import piuk.blockchain.android.R;
import piuk.blockchain.androidcore.data.access.AccessState;

import com.blockchain.logging.CrashLogger;
import com.blockchain.notifications.NotificationTokenManager;
import com.blockchain.notifications.analytics.Analytics;
import com.blockchain.preferences.CurrencyPrefs;
import com.blockchain.remoteconfig.FeatureFlag;
import com.blockchain.nabu.datamanagers.CustodialWalletManager;

import piuk.blockchain.androidcore.data.api.EnvironmentConfig;
import piuk.blockchain.androidcore.data.metadata.MetadataManager;
import piuk.blockchain.androidcore.data.payload.PayloadDataManager;
import piuk.blockchain.androidcore.data.settings.SettingsDataManager;
import piuk.blockchain.androidcoreui.ui.customviews.ToastCustom;
import piuk.blockchain.android.util.AppUtil;
import piuk.blockchain.androidcore.utils.PersistentPrefs;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Created by adambennett on 09/08/2016.
 */
@Config(sdk = 23, application = BlockchainTestApplication.class)
@RunWith(RobolectricTestRunner.class)
public class LauncherPresenterTest {

    private LauncherPresenter subject;

    @Mock
    private LauncherView launcherActivity;
    @Mock
    private PersistentPrefs prefsUtil;
    @Mock
    private AppUtil appUtil;
    @Mock
    private PayloadDataManager payloadDataManager;
    @Mock
    private DeepLinkPersistence deepLinkPersistence;
    @Mock
    private SettingsDataManager settingsDataManager;
    @Mock
    private AccessState accessState;
    @Mock
    private Intent intent;
    @Mock
    private Bundle extras;
    @Mock
    private Wallet wallet;
    @Mock
    private NotificationTokenManager notificationTokenManager;
    @Mock
    private EnvironmentConfig environmentConfig;
    @Mock
    private FeatureFlag featureFlag;
    @Mock
    private CustodialWalletManager custodialWalletManager;
    @Mock
    private CurrencyPrefs currencyPrefs;
    @Mock
    private MetadataManager metadataManager;
    @Mock
    private Analytics analytics;
    @Mock
    private CrashLogger crashLogger;
    @Mock
    private Prerequisites prerequisites;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        subject = new LauncherPresenter(
                appUtil,
                payloadDataManager,
                prefsUtil,
                deepLinkPersistence,
                accessState,
                settingsDataManager,
                notificationTokenManager,
                environmentConfig,
                currencyPrefs,
                analytics,
                prerequisites,
                crashLogger
        );
        subject.initView(launcherActivity);
        Mockito.when(featureFlag.getEnabled()).thenReturn(Single.just(false));
        Mockito.when(custodialWalletManager.isCurrencySupportedForSimpleBuy(any()))
                .thenReturn(Single.just(true));
        Mockito.when(currencyPrefs.getSelectedFiatCurrency()).thenReturn("USD");
        Mockito.when(currencyPrefs.getDefaultFiatCurrency()).thenReturn("USD");
        Mockito.when(settingsDataManager.updateFiatUnit(any())).thenReturn(Observable.just(mock(Settings.class)));
    }

    /**
     * Everything is good. Expected output is {@link LauncherActivity#onStartMainActivity(Uri)}
     */
    @Test
    public void onViewReadyVerifiedEmailVerified() {
        // Arrange
        when(launcherActivity.getPageIntent()).thenReturn(intent);
        when(intent.getExtras()).thenReturn(extras);
        when(extras.containsKey(AppUtil.INTENT_EXTRA_VERIFIED)).thenReturn(true);
        when(extras.getBoolean(AppUtil.INTENT_EXTRA_VERIFIED)).thenReturn(true);
        when(prefsUtil.getValue(anyString(), anyString())).thenReturn("1234567890");
        when(prefsUtil.getPinId()).thenReturn("1234567890");
        when(prefsUtil.isLoggedOut()).thenReturn(false);
        when(appUtil.isSane()).thenReturn(true);
        when(payloadDataManager.getWallet()).thenReturn(wallet);
        when(prerequisites.initMetadataAndRelatedPrerequisites()).thenReturn(Completable.complete());
        Settings mockSettings = mock(Settings.class);
        when(prerequisites.initSettings(any(), any())).thenReturn(Single.just(mockSettings));
        when(wallet.isUpgraded()).thenReturn(true);
        when(accessState.isLoggedIn()).thenReturn(true);
        String guid = "GUID";
        String sharedKey = "SHARED_KEY";
        when(wallet.getGuid()).thenReturn(guid);
        when(wallet.getSharedKey()).thenReturn(sharedKey);
        when(mockSettings.isEmailVerified()).thenReturn(true);
        when(mockSettings.getCurrency()).thenReturn("USD");
        // Act
        subject.onViewReady();
        // Assert
        verify(launcherActivity).onStartMainActivity(null, false);
    }

    /**
     * Everything is good, email not verified and getting {@link Settings} object failed. Should
     * re-request PIN code.
     */
    @Test
    public void onViewReadyNonVerifiedEmailSettingsFailure() {
        // Arrange
        when(launcherActivity.getPageIntent()).thenReturn(intent);
        when(intent.getExtras()).thenReturn(extras);
        when(extras.containsKey(AppUtil.INTENT_EXTRA_VERIFIED)).thenReturn(true);
        when(extras.getBoolean(AppUtil.INTENT_EXTRA_VERIFIED)).thenReturn(true);
        when(prefsUtil.getValue(anyString(), anyString())).thenReturn("1234567890");
        when(prefsUtil.getPinId()).thenReturn("1234567890");
        when(prefsUtil.isLoggedOut()).thenReturn(false);
        when(appUtil.isSane()).thenReturn(true);
        when(payloadDataManager.getWallet()).thenReturn(wallet);
        when(wallet.isUpgraded()).thenReturn(true);
        when(accessState.isLoggedIn()).thenReturn(true);
        String guid = "GUID";
        String sharedKey = "SHARED_KEY";
        when(prerequisites.initMetadataAndRelatedPrerequisites()).thenReturn(Completable.complete());
        Settings mockSettings = mock(Settings.class);
        when(prerequisites.initSettings(any(), any())).thenReturn(Single.error(new Throwable()));
        when(wallet.getGuid()).thenReturn(guid);
        when(wallet.getSharedKey()).thenReturn(sharedKey);

        // Act
        subject.onViewReady();
        // Assert
        verify(launcherActivity).showToast(R.string.unexpected_error, ToastCustom.TYPE_ERROR);
        verify(launcherActivity).onRequestPin();
    }

    /**
     * Bitcoin URI is found, expected to step into Bitcoin branch and call {@link
     * LauncherActivity#onStartMainActivity(Uri)}
     */
    @Test
    public void onViewReadyBitcoinUri() {
        // Arrange
        when(launcherActivity.getPageIntent()).thenReturn(intent);
        when(intent.getAction()).thenReturn(Intent.ACTION_VIEW);
        when(intent.getScheme()).thenReturn("bitcoin");
        when(intent.getData()).thenReturn(Uri.parse("bitcoin uri"));
        when(intent.getExtras()).thenReturn(extras);
        when(extras.containsKey(AppUtil.INTENT_EXTRA_VERIFIED)).thenReturn(true);
        when(extras.getBoolean(AppUtil.INTENT_EXTRA_VERIFIED)).thenReturn(true);
        when(prefsUtil.getValue(anyString(), anyString())).thenReturn("1234567890");
        when(prefsUtil.getPinId()).thenReturn("1234567890");
        when(prefsUtil.isLoggedOut()).thenReturn(false);
        when(appUtil.isSane()).thenReturn(true);
        when(payloadDataManager.getWallet()).thenReturn(wallet);
        when(wallet.isUpgraded()).thenReturn(true);
        when(accessState.isLoggedIn()).thenReturn(true);
        String guid = "GUID";
        String sharedKey = "SHARED_KEY";
        when(prerequisites.initMetadataAndRelatedPrerequisites()).thenReturn(Completable.complete());
        Settings mockSettings = mock(Settings.class);
        when(prerequisites.initSettings(any(), any())).thenReturn(Single.just(mockSettings));
        when(wallet.getGuid()).thenReturn(guid);
        when(wallet.getSharedKey()).thenReturn(sharedKey);
        when(mockSettings.isEmailVerified()).thenReturn(true);
        when(mockSettings.getCurrency()).thenReturn("USD");
        // Act
        subject.onViewReady();
        // Assert
        verify(prefsUtil).setValue(PersistentPrefs.KEY_SCHEME_URL, "bitcoin uri");
        verify(launcherActivity).onStartMainActivity(null, false);
    }

    /**
     * Everything is fine, but PIN not validated. Expected output is {@link
     * LauncherActivity#onRequestPin()}
     */
    @Test
    public void onViewReadyNotVerified() {
        // Arrange
        when(launcherActivity.getPageIntent()).thenReturn(intent);
        when(intent.getExtras()).thenReturn(extras);
        when(extras.containsKey(AppUtil.INTENT_EXTRA_VERIFIED)).thenReturn(false);
        when(prefsUtil.getValue(anyString(), anyString())).thenReturn("1234567890");
        when(prefsUtil.getPinId()).thenReturn("1234567890");
        when(prefsUtil.isLoggedOut()).thenReturn(false);
        when(appUtil.isSane()).thenReturn(true);
        when(payloadDataManager.getWallet()).thenReturn(wallet);
        when(wallet.isUpgraded()).thenReturn(true);
        when(accessState.isLoggedIn()).thenReturn(false);
        // Act
        subject.onViewReady();
        // Assert
        verify(launcherActivity).onRequestPin();
    }

    /**
     * Everything is fine, but PIN not validated. However, {@link AccessState} returns logged in.
     * Expected output is {@link LauncherActivity#onStartMainActivity(Uri)}
     */
    @Test
    public void onViewReadyPinNotValidatedButLoggedIn() {
        // Arrange
        when(launcherActivity.getPageIntent()).thenReturn(intent);
        when(intent.getExtras()).thenReturn(extras);
        when(extras.containsKey(AppUtil.INTENT_EXTRA_VERIFIED)).thenReturn(false);
        when(prefsUtil.getValue(anyString(), anyString())).thenReturn("1234567890");
        when(prefsUtil.getPinId()).thenReturn("1234567890");
        when(prefsUtil.isLoggedOut()).thenReturn(false);
        when(appUtil.isSane()).thenReturn(true);
        when(payloadDataManager.getWallet()).thenReturn(wallet);
        when(wallet.isUpgraded()).thenReturn(true);
        when(accessState.isLoggedIn()).thenReturn(true);
        String guid = "GUID";
        String sharedKey = "SHARED_KEY";
        when(prerequisites.initMetadataAndRelatedPrerequisites()).thenReturn(Completable.complete());
        Settings mockSettings = mock(Settings.class);
        when(prerequisites.initSettings(any(), any())).thenReturn(Single.just(mockSettings));
        when(wallet.getGuid()).thenReturn(guid);
        when(wallet.getSharedKey()).thenReturn(sharedKey);
        when(mockSettings.isEmailVerified()).thenReturn(true);
        when(mockSettings.getCurrency()).thenReturn("USD");
        // Act
        subject.onViewReady();
        // Assert
        verify(accessState).setLoggedIn(true);
        verify(launcherActivity).onStartMainActivity(null, false);
    }

    /**
     * GUID not found, expected output is {@link LauncherActivity#onNoGuid()}
     */
    @Test
    public void onViewReadyNoGuid() {
        // Arrange
        when(launcherActivity.getPageIntent()).thenReturn(intent);
        when(intent.getExtras()).thenReturn(extras);
        when(extras.containsKey(AppUtil.INTENT_EXTRA_VERIFIED)).thenReturn(false);
        when(prefsUtil.getValue(anyString(), anyString())).thenReturn("");
        // Act
        subject.onViewReady();
        // Assert
        verify(launcherActivity).onNoGuid();
    }

    /**
     * Pin not found, expected output is {@link LauncherActivity#onRequestPin()}
     */
    @Test
    public void onViewReadyNoPin() {
        // Arrange
        when(launcherActivity.getPageIntent()).thenReturn(intent);
        when(intent.getExtras()).thenReturn(extras);
        when(extras.containsKey(AppUtil.INTENT_EXTRA_VERIFIED)).thenReturn(false);
        when(prefsUtil.getValue(eq(PersistentPrefs.KEY_WALLET_GUID), anyString())).thenReturn("1234567890");
        when(prefsUtil.getPinId()).thenReturn("");
        // Act
        subject.onViewReady();
        // Assert
        verify(launcherActivity).onRequestPin();
    }

    /**
     * AppUtil returns not sane. Expected output is {@link LauncherActivity#onCorruptPayload()}
     */
    @Test
    public void onViewReadyNotSane() {
        // Arrange
        when(launcherActivity.getPageIntent()).thenReturn(intent);
        when(intent.getExtras()).thenReturn(extras);
        when(extras.containsKey(AppUtil.INTENT_EXTRA_VERIFIED)).thenReturn(false);
        when(prefsUtil.getValue(anyString(), anyString())).thenReturn("1234567890");
        when(prefsUtil.getPinId()).thenReturn("1234567890");
        when(appUtil.isSane()).thenReturn(false);
        // Act
        subject.onViewReady();
        // Assert
        verify(launcherActivity).onCorruptPayload();
    }

    /**
     * Everything is fine, but not upgraded. Expected output is {@link
     * LauncherActivity#onRequestUpgrade()}
     */
    @Test
    public void onViewReadyNotUpgraded() {
        // Arrange
        when(launcherActivity.getPageIntent()).thenReturn(intent);
        when(intent.getExtras()).thenReturn(extras);
        when(extras.containsKey(AppUtil.INTENT_EXTRA_VERIFIED)).thenReturn(true);
        when(extras.getBoolean(AppUtil.INTENT_EXTRA_VERIFIED)).thenReturn(true);
        when(prefsUtil.getValue(anyString(), anyString())).thenReturn("1234567890");
        when(prefsUtil.getPinId()).thenReturn("1234567890");
        when(prefsUtil.isLoggedOut()).thenReturn(false);
        when(appUtil.isSane()).thenReturn(true);
        when(payloadDataManager.getWallet()).thenReturn(wallet);
        when(wallet.isUpgraded()).thenReturn(false);
        // Act
        subject.onViewReady();
        // Assert
        verify(launcherActivity).onRequestUpgrade();
    }

    /**
     * GUID exists, Shared Key exists but user logged out. Expected output is {@link
     * LauncherActivity#onReEnterPassword()}
     */
    @Test
    public void onViewReadyUserLoggedOut() {
        // Arrange
        when(launcherActivity.getPageIntent()).thenReturn(intent);
        when(intent.getExtras()).thenReturn(extras);
        when(extras.containsKey(AppUtil.INTENT_EXTRA_VERIFIED)).thenReturn(false);
        when(prefsUtil.getValue(anyString(), anyString())).thenReturn("1234567890");
        when(prefsUtil.isLoggedOut()).thenReturn(true);
        // Act
        subject.onViewReady();
        // Assert
        verify(launcherActivity).onReEnterPassword();
    }

    @Test
    public void clearCredentialsAndRestart() {
        // Arrange

        // Act
        subject.clearCredentialsAndRestart();
        // Assert
        verify(appUtil).clearCredentialsAndRestart(any());
    }

}