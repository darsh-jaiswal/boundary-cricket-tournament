package com.example.madfinalproject;

import android.Manifest;
import android.app.AlertDialog;
import android.content.*;
import android.content.pm.PackageManager;
import android.media.*;
import android.os.*;
import android.speech.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.k2fsa.sherpa.onnx.*;
import java.io.*;
import java.util.*;

/** Enrolls three independent utterances and compares learned speaker embeddings locally. */
public class VoiceActivity extends AppCompatActivity implements RecognitionListener {
  private final java.util.concurrent.ExecutorService nativeQueue =
      java.util.concurrent.Executors.newSingleThreadExecutor();
  private TextView status;
  private Button enroll, command;
  private SpeakerEmbeddingExtractor extractor;
  private AudioRecord mic;
  private SpeechRecognizer recognizer;
  private ParcelFileDescriptor input;
  private org.vosk.Model speechModel;
  private volatile boolean alive = true, busy;
  private String profile;
  private final ArrayList<float[]> samples = new ArrayList<>();

  @Override
  public void onCreate(Bundle saved) {
    super.onCreate(saved);
    profile = getIntent().getStringExtra("profile");
    if (profile == null) profile = "official";
    LinearLayout box = new LinearLayout(this);
    box.setPadding(36, 70, 36, 36);
    box.setOrientation(1);
    box.setBackgroundColor(0xfff3f6ef);
    setContentView(box);
    TextView h = new TextView(this);
    h.setText("Official voice scoring");
    h.setTextSize(28);
    box.addView(h);
    status = new TextView(this);
    status.setText("Loading speaker model…");
    box.addView(status);
    enroll = new Button(this);
    enroll.setText("Enroll official voice · 3 phrases");
    box.addView(enroll);
    command = new Button(this);
    command.setText("Speak scoring command");
    box.addView(command);
    CheckBox wake = new CheckBox(this);
    wake.setText("Require ‘Scorer’ or ‘Umpire’ wake word");
    wake.setChecked(getSharedPreferences("voice", 0).getBoolean("wake", false));
    wake.setOnCheckedChangeListener(
        (v, on) -> getSharedPreferences("voice", 0).edit().putBoolean("wake", on).apply());
    box.addView(wake);
    TextView note = new TextView(this);
    note.setText(
        "Voice matching runs on this phone. No biometric system can promise perfect rejection in"
            + " crowd noise. Keep confirmation enabled and use touch when conditions are poor.\n\n"
            + "Command transcription runs offline on the same verified recording.");
    box.addView(note);
    enroll.setOnClickListener(v -> capture(true));
    command.setOnClickListener(v -> capture(false));
    enroll.setEnabled(false);
    command.setEnabled(false);
    nativeQueue.execute(
        () -> {
          try {
            SpeakerEmbeddingExtractorConfig config =
                new SpeakerEmbeddingExtractorConfig("speaker.onnx", 2, false, "cpu");
            extractor = new SpeakerEmbeddingExtractor(getAssets(), config);
            File speechDir = new File(getFilesDir(), "vosk-model-small-en-in-0.4");
            if (!new File(speechDir, ".complete").exists()) {
              try (java.util.zip.ZipInputStream zip =
                  new java.util.zip.ZipInputStream(getAssets().open("speech.zip"))) {
                java.util.zip.ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                  File dest = new File(getFilesDir(), entry.getName());
                  if (!dest.getCanonicalPath()
                      .startsWith(getFilesDir().getCanonicalPath() + File.separator))
                    throw new IOException("Invalid speech model archive");
                  if (entry.isDirectory()) {
                    dest.mkdirs();
                  } else {
                    dest.getParentFile().mkdirs();
                    try (OutputStream out = new FileOutputStream(dest)) {
                      byte[] bytes = new byte[16384];
                      int n;
                      while ((n = zip.read(bytes)) > 0) out.write(bytes, 0, n);
                    }
                  }
                }
              }
            }
            speechModel = new org.vosk.Model(speechDir.getAbsolutePath());
            new File(speechDir, ".complete").createNewFile();
            runOnUiThread(
                () -> {
                  if (!alive) return;
                  status.setText(
                      hasProfile()
                          ? "Official voice enrolled. Ready to listen."
                          : "Enroll the designated official in a quiet place before scoring.");
                  enroll.setEnabled(true);
                  command.setEnabled(true);
                });
          } catch (Throwable e) {
            runOnUiThread(() -> status.setText("Speaker model could not load: " + e.getMessage()));
          }
        });
  }

  private boolean hasProfile() {
    return getSharedPreferences("voice", 0).contains(profile);
  }

  private void capture(boolean enrolling) {
    if (busy) return;
    if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
        != PackageManager.PERMISSION_GRANTED) {
      requestPermissions(new String[] {Manifest.permission.RECORD_AUDIO}, 62);
      return;
    }
    if (!enrolling && !hasProfile()) {
      status.setText("Enroll the official before accepting commands.");
      return;
    }
    busy = true;
    enroll.setEnabled(false);
    command.setEnabled(false);
    status.setText(
        enrolling
            ? "Phrase "
                + (samples.size() + 1)
                + " of 3: say ‘Scorer, four runs by the batter, wide ball, undo last ball’ for five"
                + " seconds."
            : "Listening for five seconds. Say your full command now.");
    nativeQueue.execute(
        () -> {
          try {
            int rate = 16000, count = rate * 5;
            short[] pcm = new short[count];
            int min =
                AudioRecord.getMinBufferSize(
                    rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            mic =
                new AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    rate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    Math.max(min, rate));
            if (mic.getState() != AudioRecord.STATE_INITIALIZED)
              throw new IOException("Microphone is unavailable");
            mic.startRecording();
            int position = 0;
            while (alive && position < count) {
              int n = mic.read(pcm, position, Math.min(2048, count - position));
              if (n < 0) throw new IOException("Microphone capture failed");
              position += n;
            }
            mic.stop();
            mic.release();
            mic = null;
            if (!alive) return;
            float[] waveform = new float[count];
            double energy = 0;
            for (int i = 0; i < count; i++) {
              waveform[i] = pcm[i] / 32768f;
              energy += waveform[i] * waveform[i];
            }
            if (Math.sqrt(energy / count) < .003)
              throw new IOException("Speech was too quiet. Move closer and try again.");
            OnlineStream stream = extractor.createStream();
            float[] embedding;
            try {
              stream.acceptWaveform(waveform, rate);
              stream.inputFinished();
              if (!extractor.isReady(stream))
                throw new IOException("Not enough speech for voice verification");
              embedding = extractor.compute(stream);
            } finally {
              stream.release();
            }
            if (enrolling) {
              samples.add(embedding);
              if (samples.size() == 3) {
                float[] avg = new float[embedding.length];
                for (float[] v : samples) for (int i = 0; i < avg.length; i++) avg[i] += v[i] / 3;
                StringBuilder value = new StringBuilder();
                for (float n : avg) value.append(n).append(',');
                getSharedPreferences("voice", 0)
                    .edit()
                    .putString(profile, value.toString())
                    .apply();
                samples.clear();
              }
              runOnUiThread(
                  () ->
                      finishCapture(
                          hasProfile()
                              ? "Official enrolled. Voice profile saved only on this phone."
                              : "Sample saved. Tap Enroll again for the next phrase."));
            } else {
              String[] saved = getSharedPreferences("voice", 0).getString(profile, "").split(",");
              double dot = 0, a = 0, b = 0;
              for (int i = 0; i < embedding.length; i++) {
                float v = Float.parseFloat(saved[i]);
                dot += v * embedding[i];
                a += v * v;
                b += embedding[i] * embedding[i];
              }
              double similarity = dot / Math.sqrt(a * b);
              if (similarity < .65) {
                runOnUiThread(
                    () -> {
                      status.setTextColor(0xffab3f32);
                      finishCapture(
                          "Background voice blocked · voice match "
                              + Math.round(similarity * 100)
                              + "%. No command applied.");
                    });
              } else
                runOnUiThread(
                    () -> {
                      status.setTextColor(0xff147653);
                      status.setText(
                          "Official recognized · "
                              + Math.round(similarity * 100)
                              + "%. Transcribing verified audio…");
                      transcribe(pcm);
                    });
            }
          } catch (Throwable e) {
            runOnUiThread(() -> finishCapture("Could not verify voice: " + e.getMessage()));
          } finally {
            if (mic != null) {
              mic.release();
              mic = null;
            }
          }
        });
  }

  private void finishCapture(String message) {
    if (!alive) return;
    status.setText(message);
    busy = false;
    enroll.setEnabled(true);
    command.setEnabled(true);
  }

  private void transcribe(short[] pcm) {
    nativeQueue.execute(
        () -> {
          try (org.vosk.Recognizer decoder = new org.vosk.Recognizer(speechModel, 16000)) {
            StringBuilder text = new StringBuilder();
            for (int pos = 0; pos < pcm.length; pos += 4000) {
              short[] block = Arrays.copyOfRange(pcm, pos, Math.min(pcm.length, pos + 4000));
              if (decoder.acceptWaveForm(block, block.length)) {
                String part = new org.json.JSONObject(decoder.getResult()).optString("text");
                text.append(part).append(' ');
              }
            }
            text.append(new org.json.JSONObject(decoder.getFinalResult()).optString("text"));
            String line = text.toString().trim();
            runOnUiThread(
                () -> {
                  if (!alive) return;
                  if (line.isEmpty()) {
                    finishCapture("No command recognized. Try a clear full phrase.");
                    return;
                  }
                  Bundle b = new Bundle();
                  ArrayList<String> lines = new ArrayList<>();
                  lines.add(line);
                  b.putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, lines);
                  onResults(b);
                });
          } catch (Exception e) {
            runOnUiThread(() -> finishCapture("Offline transcription failed: " + e.getMessage()));
          }
        });
  }

  @Override
  public void onResults(Bundle b) {
    ArrayList<String> lines = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
    if (lines == null || lines.isEmpty()) {
      finishCapture("No command recognized.");
      return;
    }
    String line = lines.get(0);
    if (getSharedPreferences("voice", 0).getBoolean("wake", false)
        && !line.toLowerCase(Locale.ROOT).matches(".*\\b(scorer|umpire)\\b.*")) {
      finishCapture("Wake word missing. No command applied.");
      return;
    }
    new AlertDialog.Builder(this)
        .setTitle("Recognized official command")
        .setMessage(line)
        .setOnCancelListener(d -> finishCapture("Command ignored."))
        .setNegativeButton("Ignore", (d, w) -> finishCapture("Command ignored."))
        .setPositiveButton(
            "Apply",
            (d, w) -> {
              setResult(RESULT_OK, new Intent().putExtra("command", line));
              finish();
            })
        .show();
  }

  @Override
  public void onError(int error) {
    finishCapture(
        "Speech service could not transcribe verified audio ("
            + error
            + "). Try again or use touch scoring.");
  }

  @Override
  public void onReadyForSpeech(Bundle b) {}

  @Override
  public void onBeginningOfSpeech() {}

  @Override
  public void onRmsChanged(float f) {}

  @Override
  public void onBufferReceived(byte[] b) {}

  @Override
  public void onEndOfSpeech() {}

  @Override
  public void onPartialResults(Bundle b) {}

  @Override
  public void onEvent(int t, Bundle b) {}

  @Override
  protected void onDestroy() {
    alive = false;
    try {
      AudioRecord recorder = mic;
      if (recorder != null && recorder.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING)
        recorder.stop();
    } catch (IllegalStateException ignored) {
    }
    if (recognizer != null) recognizer.destroy();
    if (input != null)
      try {
        input.close();
      } catch (IOException ignored) {
      }
    nativeQueue.execute(
        () -> {
          if (extractor != null) {
            extractor.release();
            extractor = null;
          }
          if (speechModel != null) {
            speechModel.close();
            speechModel = null;
          }
        });
    nativeQueue.shutdown();
    super.onDestroy();
  }
}
