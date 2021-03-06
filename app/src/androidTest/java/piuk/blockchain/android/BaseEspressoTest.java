package piuk.blockchain.android;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import androidx.annotation.CallSuper;
import androidx.test.InstrumentationRegistry;

import android.view.MotionEvent;

import com.blockchain.logging.CrashLogger;

import org.junit.After;
import org.junit.Before;

import org.mockito.Mock;
import piuk.blockchain.androidcore.utils.DeviceIdGenerator;
import piuk.blockchain.androidcore.utils.PersistentPrefs;
import piuk.blockchain.androidcore.utils.PrefsUtil;
import piuk.blockchain.androidcore.utils.UUIDGenerator;

@SuppressWarnings("WeakerAccess")
public class BaseEspressoTest {

    @Mock
    protected DeviceIdGenerator idGenerator;
    @Mock
    protected UUIDGenerator uuidGenerator;

    @Mock
    protected CrashLogger crashLogger;

    private final SharedPreferences store =
        PreferenceManager.getDefaultSharedPreferences(InstrumentationRegistry.getTargetContext());

    private SystemAnimations systemAnimations;
    protected PrefsUtil prefs;

    @CallSuper
    @Before
    public void setup() {
        systemAnimations = new SystemAnimations(InstrumentationRegistry.getTargetContext());

        prefs = new PrefsUtil(store, store, idGenerator, uuidGenerator, crashLogger);
        clearState();
        ignoreTapJacking(true);
        disableAnimations();
    }

    @CallSuper
    @After
    public void tearDown() {
        enableAnimations();
    }

    /**
     * Clears application state completely for use between tests. Use alongside <code>new
     * ActivityTestRule<>(Activity.class, false, false)</code> and launch activity manually on setup
     * to avoid Espresso starting your activity automatically, if that's what you need.
     */
    protected void clearState() {
        prefs.clear();
    }

    /**
     * Sets SharedPreferences value which means that {@link piuk.blockchain.androidcoreui.utils.OverlayDetection#detectObscuredWindow(Context,
     * MotionEvent)} won't trigger a warning dialog.
     *
     * @param ignore Set to true to ignore all touch events
     */
    protected void ignoreTapJacking(boolean ignore) {
        prefs.setValue(PersistentPrefs.KEY_OVERLAY_TRUSTED, ignore);
    }

    /**
     * Disables all system animations for less flaky tests.
     */
    private void disableAnimations() {
        systemAnimations.disableAll();
    }

    /**
     * Re-enables all system animations. Intended for use once all tests complete.
     */
    private void enableAnimations() {
        systemAnimations.enableAll();
    }

}
