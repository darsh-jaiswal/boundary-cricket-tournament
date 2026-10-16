package com.example.madfinalproject;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import java.io.*;

public class ScannerActivity extends AppCompatActivity {
  private EditText review;
  private TextRecognizer recognizer;
  private ImageView preview;
  private TextView status;

  @Override
  public void onCreate(Bundle b) {
    super.onCreate(b);
    recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
    LinearLayout box = new LinearLayout(this);
    box.setOrientation(1);
    box.setPadding(24, 40, 24, 20);
    box.setBackgroundColor(0xfff3f6ef);
    setContentView(box);
    UiInsets.apply(box);
    TextView title = new TextView(this);
    title.setText("Scorebook scanner");
    title.setTextSize(28);
    box.addView(title);
    status = new TextView(this);
    status.setText("Choose a clear image. Check handwriting and player names before saving.");
    box.addView(status);
    Button choose = new Button(this);
    choose.setText("Choose scorebook image");
    box.addView(choose);
    choose.setOnClickListener(
        v -> {
          Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
          i.setType("image/*");
          i.addCategory(Intent.CATEGORY_OPENABLE);
          startActivityForResult(i, 21);
        });
    preview = new ImageView(this);
    preview.setAdjustViewBounds(true);
    box.addView(preview, new LinearLayout.LayoutParams(-1, 240));
    review = new EditText(this);
    review.setGravity(android.view.Gravity.TOP);
    review.setHint("Recognized scorebook text appears here. Edit the result before saving.");
    box.addView(review, new LinearLayout.LayoutParams(-1, 0, 1));
    Button save = new Button(this);
    save.setText("Save text & import player statistics");
    box.addView(save);
    save.setOnClickListener(
        v -> {
          try {
            if (review.getText().toString().trim().isEmpty())
              throw new IllegalArgumentException("Choose an image or enter scorebook text first.");
            File folder = new File(getFilesDir(), "scorebooks");
            if (!folder.exists() && !folder.mkdirs())
              throw new IOException("Cannot create scorebook folder");
            File f = new File(folder, "scorebook-" + System.currentTimeMillis() + ".txt");
            try (Writer writer =
                new OutputStreamWriter(
                    new FileOutputStream(f), java.nio.charset.StandardCharsets.UTF_8)) {
              writer.write(review.getText().toString());
            }
            importStats();
          } catch (Exception e) {
            status.setText(e.getMessage());
          }
        });
  }

  @Override
  protected void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request, result, data);
    if (request == 21 && result == RESULT_OK && data != null && data.getData() != null) {
      try {
        preview.setImageURI(data.getData());
        status.setText("Reading scorebook on this device…");
        InputImage image = InputImage.fromFilePath(this, data.getData());
        recognizer
            .process(image)
            .addOnSuccessListener(
                t -> {
                  review.setText(t.getText());
                  status.setText(
                      t.getText().isEmpty()
                          ? "No readable text found. Try a sharper, evenly lit image."
                          : "Review the extracted text, especially handwritten numbers.");
                })
            .addOnFailureListener(e -> status.setText("Could not read image: " + e.getMessage()));
      } catch (Exception e) {
        status.setText(e.getMessage());
      }
    }
  }

  private void importStats() {
    LinearLayout box = new LinearLayout(this);
    box.setOrientation(1);
    box.setPadding(30, 10, 30, 10);
    EditText match = new EditText(this);
    match.setHint("Past match / scorebook name");
    box.addView(match);
    EditText team = new EditText(this);
    team.setHint("Team name (same as registered team)");
    box.addView(team);
    TextView hint = new TextView(this);
    hint.setText(
        "Review rows: player,runs,balls,wickets,conceded,bowled\n"
            + "One player per line. Batting-only rows use 0,0,0 for bowling.");
    box.addView(hint);
    EditText csv = new EditText(this);
    csv.setSingleLine(false);
    StringBuilder guessed = new StringBuilder();
    for (String line : review.getText().toString().split("\\n")) {
      java.util.regex.Matcher m =
          java.util.regex.Pattern.compile(
                  "^([A-Za-z][A-Za-z .'-]+?)\\s+(\\d+)\\s+(\\d+)(?:\\s+.*)?$")
              .matcher(line.trim());
      if (m.matches())
        guessed
            .append(m.group(1).trim())
            .append(',')
            .append(m.group(2))
            .append(',')
            .append(m.group(3))
            .append(",0,0,0\n");
    }
    csv.setText(guessed.toString());
    csv.setHint("Rohan,42,31,0,0,0");
    box.addView(csv);
    ScrollView scroll = new ScrollView(this);
    scroll.addView(box);
    AlertDialog d =
        new AlertDialog.Builder(this)
            .setTitle("Review imported scorecard")
            .setView(scroll)
            .setNegativeButton("Keep text only", null)
            .setPositiveButton("Import statistics", null)
            .create();
    d.setOnShowListener(
        v ->
            d.getButton(-1)
                .setOnClickListener(
                    vv -> {
                      try {
                        if (match.getText().toString().trim().isEmpty()
                            || team.getText().toString().trim().isEmpty())
                          throw new IllegalArgumentException("Enter match and team names.");
                        com.example.madfinalproject.domain.Cricket.Scorebook record =
                            new com.example.madfinalproject.domain.Cricket.Scorebook(
                                match.getText().toString().trim(),
                                team.getText().toString().trim());
                        for (String line : csv.getText().toString().split("\\n"))
                          if (!line.trim().isEmpty())
                            record.players.add(
                                new com.example.madfinalproject.domain.Cricket.ImportedPlayer(
                                    line));
                        if (record.players.isEmpty())
                          throw new IllegalArgumentException(
                              "Add at least one reviewed player row.");
                        try (com.example.madfinalproject.data.Repository db =
                            new com.example.madfinalproject.data.Repository(this)) {
                          db.save(record.id, "scorebook", record);
                        }
                        status.setText(
                            "Past scorecard imported. Player statistics now appear in team"
                                + " careers.");
                        d.dismiss();
                      } catch (Exception error) {
                        Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
                      }
                    }));
    d.show();
  }

  @Override
  protected void onDestroy() {
    recognizer.close();
    super.onDestroy();
  }
}
