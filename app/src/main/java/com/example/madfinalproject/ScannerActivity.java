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
    private EditText review;private TextRecognizer recognizer;private ImageView preview;private TextView status;
    @Override public void onCreate(Bundle b){super.onCreate(b);recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);LinearLayout box=new LinearLayout(this);box.setOrientation(1);box.setPadding(24,40,24,20);box.setBackgroundColor(0xfff3f6ef);setContentView(box);TextView title=new TextView(this);title.setText("Scorebook scanner");title.setTextSize(28);box.addView(title);status=new TextView(this);status.setText("Choose a clear image. Check handwriting and player names before saving.");box.addView(status);Button choose=new Button(this);choose.setText("Choose scorebook image");box.addView(choose);choose.setOnClickListener(v->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("image/*");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,21);});preview=new ImageView(this);preview.setAdjustViewBounds(true);box.addView(preview,new LinearLayout.LayoutParams(-1,240));review=new EditText(this);review.setGravity(android.view.Gravity.TOP);review.setHint("Recognized scorebook text appears here. Edit the result before saving.");box.addView(review,new LinearLayout.LayoutParams(-1,0,1));Button save=new Button(this);save.setText("Save reviewed scorebook");box.addView(save);save.setOnClickListener(v->{try{if(review.getText().toString().trim().isEmpty())throw new IllegalArgumentException("Choose an image or enter scorebook text first.");File folder=new File(getFilesDir(),"scorebooks");if(!folder.exists()&&!folder.mkdirs())throw new IOException("Cannot create scorebook folder");File f=new File(folder,"scorebook-"+System.currentTimeMillis()+".txt");try(Writer writer=new OutputStreamWriter(new FileOutputStream(f),java.nio.charset.StandardCharsets.UTF_8)){writer.write(review.getText().toString());}new AlertDialog.Builder(this).setTitle("Scorebook saved").setMessage("Reviewed text is saved on this device. Create the past match from the reviewed delivery records to include it in career statistics.").setPositiveButton("OK",null).show();}catch(Exception e){status.setText(e.getMessage());}});}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==21&&result==RESULT_OK&&data!=null&&data.getData()!=null){try{preview.setImageURI(data.getData());status.setText("Reading scorebook on this device…");InputImage image=InputImage.fromFilePath(this,data.getData());recognizer.process(image).addOnSuccessListener(t->{review.setText(t.getText());status.setText(t.getText().isEmpty()?"No readable text found. Try a sharper, evenly lit image.":"Review the extracted text, especially handwritten numbers.");}).addOnFailureListener(e->status.setText("Could not read image: "+e.getMessage()));}catch(Exception e){status.setText(e.getMessage());}}}
    @Override protected void onDestroy(){recognizer.close();super.onDestroy();}
}
