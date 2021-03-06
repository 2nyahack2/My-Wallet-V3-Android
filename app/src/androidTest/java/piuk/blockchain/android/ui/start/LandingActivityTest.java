package piuk.blockchain.android.ui.start;

import androidx.test.espresso.ViewInteraction;
import androidx.test.filters.LargeTest;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import piuk.blockchain.android.BaseEspressoTest;
import piuk.blockchain.android.R;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static java.lang.Thread.sleep;
import static junit.framework.Assert.assertNotNull;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class LandingActivityTest extends BaseEspressoTest {

    private static final ViewInteraction BUTTON_LOGIN = onView(withId(R.id.btn_login));
    private static final ViewInteraction BUTTON_CREATE = onView(withId(R.id.btn_create));
    private static final ViewInteraction BUTTON_RECOVER = onView(withId(R.id.btn_recover));

    @Before
    public void setUp() throws Exception {
        prefs.setValue("disable_root_warning", true);
    }

    @Rule
    public ActivityTestRule<LandingActivity> activityRule =
            new ActivityTestRule<>(LandingActivity.class);

    @Test
    public void isLaunched() throws Exception {
        assertNotNull(activityRule.getActivity());
    }

    @Test
    public void launchLoginPage() throws InterruptedException {
        BUTTON_LOGIN.perform(click());
        sleep(500);
        // Check pairing fragment launched
        onView(withText(R.string.log_in)).check(matches(isDisplayed()));
    }

    @Test
    public void launchCreateWalletPage() throws InterruptedException {
        BUTTON_CREATE.perform(click());
        sleep(500);
        // Check create wallet fragment launched
        onView(withText(R.string.create_a_wallet)).check(matches(isDisplayed()));
    }

    @Test
    public void launchRecoverFundsPage() throws InterruptedException {
        BUTTON_RECOVER.perform(click());
        sleep(500);
        // Verify warning dialog showing
        onView(withText(R.string.recover_funds_warning_message_1))
                .inRoot(isDialog())
                .check(matches(isDisplayed()));
        // Click "Continue"
        onView(withId(android.R.id.button1)).perform(click());
        sleep(500);
        // Check recover funds activity launched
        onView(withText(R.string.recover_funds_instructions)).check(matches(isDisplayed()));
    }

}