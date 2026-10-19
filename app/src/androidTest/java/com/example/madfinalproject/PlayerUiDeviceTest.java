package com.example.madfinalproject;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;

import android.content.Intent;
import android.graphics.Bitmap;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.FileOutputStream;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Run at narrow display widths to catch drawer clipping and account action regressions. */
@RunWith(AndroidJUnit4.class)
public class PlayerUiDeviceTest {
  @Test public void drawerQrAndConnectionsAreFullyVisible() throws Exception {
    try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
      scenario.onActivity(activity -> {
        try {
          java.lang.reflect.Method drawer = MainActivity.class.getDeclaredMethod("drawer");
          drawer.setAccessible(true); drawer.invoke(activity);
        } catch (Exception error) { throw new AssertionError(error); }
      });
      // Espresso can see the dialog before SideSheetBehavior's opening animation draws its first frame.
      Thread.sleep(600);
      onView(withText("Connections")).inRoot(isDialog()).check(matches(isCompletelyDisplayed()));
      onView(withContentDescription("Share your cricket profile QR code")).inRoot(isDialog()).check(matches(isCompletelyDisplayed()));
      screenshot("drawer");
    }
  }

  @Test public void accountOffersUsernameSearchAndWhatsAppInvitation() throws Exception {
    android.content.Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    Intent intent = new Intent(context, PlayerAccountActivity.class).putExtra("pick", true).putExtra("teamName", "Boundary Test XI");
    try (ActivityScenario<PlayerAccountActivity> scenario = ActivityScenario.launch(intent)) {
      onView(withHint("@username, email or player code")).check(matches(isDisplayed()));
      onView(withText("Invite player on WhatsApp")).check(matches(isDisplayed()));
      screenshot("player-account");
    }
  }

  private void screenshot(String name) throws Exception {
    android.content.Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    File folder = context.getExternalFilesDir("ui-checks");
    Bitmap bitmap = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
    if (bitmap == null) throw new AssertionError("Screenshot unavailable");
    try (FileOutputStream out = new FileOutputStream(new File(folder, name + ".png"))) {
      bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
    } finally { bitmap.recycle(); }
  }
}
