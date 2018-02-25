package org.amv.access.sdk.sample;

import android.content.Context;
import android.support.test.InstrumentationRegistry;
import android.support.test.espresso.PerformException;
import android.support.test.espresso.UiController;
import android.support.test.espresso.ViewAction;
import android.support.test.espresso.assertion.ViewAssertions;
import android.support.test.espresso.intent.rule.IntentsTestRule;
import android.support.test.espresso.util.HumanReadables;
import android.support.test.espresso.util.TreeIterables;
import android.support.test.runner.AndroidJUnit4;
import android.view.View;
import android.widget.ListView;

import org.amv.access.sdk.spi.certificate.AccessCertificatePair;
import org.amv.access.sdk.spi.certificate.DeviceCertificate;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.FixMethodOrder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static android.support.test.espresso.Espresso.onData;
import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.assertion.ViewAssertions.matches;
import static android.support.test.espresso.intent.Intents.intended;
import static android.support.test.espresso.intent.matcher.IntentMatchers.hasComponent;
import static android.support.test.espresso.matcher.ViewMatchers.isDisplayed;
import static android.support.test.espresso.matcher.ViewMatchers.isRoot;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import static android.support.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.anything;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Instrumentation test, which will execute on an Android device.
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 * <p>
 * In order to avoid hanging tests due to animations, please disable the following
 * settings on your device under Settings > Developer options:
 * - Window animation scale
 * - Transition animation scale
 * - Animator duration scale
 * @see <a href="https://developer.android.com/training/testing/espresso/setup.html">
 * Espresso setup instructions</a>
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING) // run the cert delete last
@RunWith(AndroidJUnit4.class)
public class AppActivitiesSmokeTest {
    @Rule
    public IntentsTestRule<CertificatesActivity> mainActivityActivityTestRule =
            new IntentsTestRule<>(CertificatesActivity.class);

    @Test
    public void useAppContext() throws Exception {
        // Context of the app under test.
        Context appContext = InstrumentationRegistry.getTargetContext();
        assertEquals("org.amv.access.sdk.sample", appContext.getPackageName());
    }

    @Test
    public void titleHasSerialNumber() throws Exception {
        waitForAccessCertificatesDownload();
        DeviceCertificate cert = mainActivityActivityTestRule.getActivity().controller
                .getDeviceCertificate()
                .blockingFirst();

        String serial = cert.getDeviceSerial();
        onView(withId(R.id.title_button)).check(matches(withText(serial)));
    }

    @Test
    public void atLeastOneCertificateIsVisible() throws Exception {
        // wait for the list to appear
        waitForAccessCertificatesDownload();
        CertificatesActivity.CertificatesAdapter adapter = mainActivityActivityTestRule.getActivity().adapter;
        if (adapter.getItems().isEmpty()) {
            fail("no certificates");
            return;
        }

        // check that first cert is in the list view with the same serial as the cert in datasource
        AccessCertificatePair cert = (AccessCertificatePair) adapter.getItem(0);
        onData(anything())
                .inAdapterView(withId(R.id.certificates_list_view))
                .atPosition(0)
                .onChildView(withId(R.id.name_text_view))
                .check(matches(withText(cert.getDeviceAccessCertificate().getGainerSerial())));

        onView(withId(R.id.certificates_list_view)).check(ViewAssertions.matches(withListSize(adapter.getItems().size())));

    }

    @Test
    public void broadcastViewHasCertificateSerialAsTitle() throws Exception {
        // test that broadcast view is opened on list item click and that views title is the same as the certificate's
        atLeastOneCertificateIsVisible();
        CertificatesActivity.CertificatesAdapter adapter = mainActivityActivityTestRule.getActivity().adapter;
        AccessCertificatePair cert = (AccessCertificatePair) adapter.getItem(0);
        onData(anything())
                .inAdapterView(withId(R.id.certificates_list_view))
                .atPosition(0).perform(click());
        intended(hasComponent(BroadcastActivity.class.getName()));

        String serial = cert.getDeviceAccessCertificate().getGainerSerial();
        onView(withId(R.id.title_button)).check(matches(withText(serial)));
    }

    @Test
    public void zCertificateIsDeleted() throws Exception {
        // test that a cert is visible and after delete it is not visible any more
        atLeastOneCertificateIsVisible();
        CertificatesActivity.CertificatesAdapter adapter = mainActivityActivityTestRule.getActivity().adapter;

        AccessCertificatePair cert = (AccessCertificatePair) adapter.getItem(0);
        String serialToDelete = cert.getDeviceAccessCertificate().getGainerSerial();
        onView(withText(serialToDelete)).check(matches(isDisplayed()));
        int countBeforeDelete = adapter.getCount();

        onData(anything())
                .inAdapterView(withId(R.id.certificates_list_view))
                .atPosition(0).onChildView(withId(R.id.revoke_button)).perform(click());

        onView(withText(mainActivityActivityTestRule.getActivity().getString(R.string.ok))).check(matches(isDisplayed())).perform(click());

        // click on error dialog
        onView(withText(mainActivityActivityTestRule.getActivity().getString(R.string.ok))).check(matches(isDisplayed())).perform(click());

        waitForAccessCertificatesDownload();

        // as certificate revocation is not supported, the list entry should still be displayed
        assertTrue(adapter.getCount() == countBeforeDelete);
        onView(withText(serialToDelete)).check(matches(isDisplayed()));
    }

    static void waitForAccessCertificatesDownload() {
        onView(isRoot()).perform(waitId(R.id.certificates_list_view, TimeUnit.SECONDS.toMillis(15)));
    }

    static Matcher<View> withListSize(final int size) {
        return new TypeSafeMatcher<View>() {
            @Override
            public boolean matchesSafely(final View view) {
                return ((ListView) view).getCount() == size;
            }

            @Override
            public void describeTo(final Description description) {
                description.appendText("ListView should have " + size + " items");
            }
        };
    }


    /**
     * Perform action of waiting for a specific view id.
     */
    public static ViewAction waitId(final int viewId, final long millis) {
        return new ViewAction() {
            @Override
            public Matcher<View> getConstraints() {
                return isRoot();
            }

            @Override
            public String getDescription() {
                return "wait for a specific view with id <" + viewId + "> during " + millis + " millis.";
            }

            @Override
            public void perform(final UiController uiController, final View view) {
                uiController.loopMainThreadUntilIdle();
                final long startTime = System.currentTimeMillis();
                final long endTime = startTime + millis;
                final Matcher<View> viewMatcher = withId(viewId);

                do {
                    for (View child : TreeIterables.breadthFirstViewTraversal(view)) {
                        // found view with required ID
                        if (viewMatcher.matches(child) && child.getVisibility() == View.VISIBLE) {
                            return;
                        }
                    }

                    uiController.loopMainThreadForAtLeast(50);
                }
                while (System.currentTimeMillis() < endTime);

                // timeout happens
                throw new PerformException.Builder()
                        .withActionDescription(this.getDescription())
                        .withViewDescription(HumanReadables.describe(view))
                        .withCause(new TimeoutException())
                        .build();
            }
        };
    }
}
