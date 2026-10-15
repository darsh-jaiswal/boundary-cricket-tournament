package com.example.madfinalproject;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.*;

import android.Manifest;
import android.graphics.*;
import android.media.MediaMetadataRetriever;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import java.io.File;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class GroundToolsDeviceTest {
  @Test
  public void bundledOCRReadsScorebookText() throws Exception {
    Bitmap image = Bitmap.createBitmap(1000, 240, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(image);
    canvas.drawColor(Color.WHITE);
    Paint paint = new Paint(3);
    paint.setColor(Color.BLACK);
    paint.setTextSize(80);
    canvas.drawText("ROHAN 42 31", 40, 130, paint);
    try (com.google.mlkit.vision.text.TextRecognizer reader =
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)) {
      String value =
          Tasks.await(reader.process(InputImage.fromBitmap(image, 0)), 30, TimeUnit.SECONDS)
              .getText();
      assertTrue(value.toUpperCase().contains("ROHAN"));
      assertTrue(value.contains("42"));
    }
    image.recycle();
  }

  @Test
  public void rollingCameraProducesReplay() throws Exception {
    android.content.Context c = InstrumentationRegistry.getInstrumentation().getTargetContext();
    InstrumentationRegistry.getInstrumentation()
        .getUiAutomation()
        .grantRuntimePermission(c.getPackageName(), Manifest.permission.CAMERA);
    try (ActivityScenario<StumpCamActivity> scenario =
        ActivityScenario.launch(StumpCamActivity.class)) {
      onView(withText("Start rolling camera")).perform(click());
      Thread.sleep(10500);
      onView(withText("Resume camera")).perform(click());
      onView(withText("Check run-out / no-ball")).perform(click());
      File[] found = null;
      for (int i = 0; i < 60; i++) {
        found =
            c.getCacheDir().listFiles(f -> f.getName().startsWith("review-") && f.length() > 500);
        if (found != null && found.length > 0) break;
        Thread.sleep(250);
      }
      assertNotNull(found);
      assertTrue("No exported replay clip", found.length > 0);
      MediaMetadataRetriever metadata = new MediaMetadataRetriever();
      try {
        metadata.setDataSource(found[0].getAbsolutePath());
        assertNotNull(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
        assertTrue(
            Long.parseLong(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION))
                > 3000);
      } finally {
        metadata.release();
      }
    }
  }
}
