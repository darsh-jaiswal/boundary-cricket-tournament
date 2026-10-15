package com.example.madfinalproject;

import static org.junit.Assert.*;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.k2fsa.sherpa.onnx.*;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class VoiceModelDeviceTest {
  @Test
  public void speakerModelRunsOnDevice() {
    SpeakerEmbeddingExtractor engine =
        new SpeakerEmbeddingExtractor(
            InstrumentationRegistry.getInstrumentation().getTargetContext().getAssets(),
            new SpeakerEmbeddingExtractorConfig("speaker.onnx", 2, false, "cpu"));
    OnlineStream stream = engine.createStream();
    try {
      float[] samples = new float[16000 * 3];
      for (int i = 0; i < samples.length; i++)
        samples[i] =
            (float)
                (.12 * Math.sin(2 * Math.PI * 220 * i / 16000)
                    + .05 * Math.sin(2 * Math.PI * 440 * i / 16000));
      stream.acceptWaveform(samples, 16000);
      stream.inputFinished();
      assertTrue(engine.isReady(stream));
      float[] vector = engine.compute(stream);
      assertEquals(engine.dim(), vector.length);
      for (float n : vector) assertTrue(Float.isFinite(n));
    } finally {
      stream.release();
      engine.release();
    }
  }

  @Test
  public void voiceScreenLoadsBothOfflineModels() throws Exception {
    try (ActivityScenario<VoiceActivity> scenario = ActivityScenario.launch(VoiceActivity.class)) {
      long deadline = System.currentTimeMillis() + 45000;
      boolean[] loaded = {false};
      while (System.currentTimeMillis() < deadline && !loaded[0]) {
        scenario.onActivity(
            activity -> {
              try {
                java.lang.reflect.Field model = VoiceActivity.class.getDeclaredField("speechModel");
                model.setAccessible(true);
                loaded[0] = model.get(activity) != null;
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            });
        if (!loaded[0]) Thread.sleep(250);
      }
      assertTrue("Offline command model did not load", loaded[0]);
    }
  }
}
