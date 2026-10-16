package com.example.madfinalproject;

import android.os.Bundle;
import android.webkit.*;
import androidx.appcompat.app.AppCompatActivity;

public final class SpectatorActivity extends AppCompatActivity {
  @Override
  public void onCreate(Bundle saved) {
    super.onCreate(saved);
    WebView web = new WebView(this);
    setContentView(web);
    UiInsets.apply(web);
    web.getSettings().setJavaScriptEnabled(true);
    web.getSettings().setAllowFileAccess(false);
    web.getSettings().setAllowContentAccess(false);
    web.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
    web.setWebViewClient(
        new WebViewClient() {
          @Override
          public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            return !"boundary-cricket-tournament.vercel.app".equals(request.getUrl().getHost());
          }
        });
    web.loadUrl("https://boundary-cricket-tournament.vercel.app/");
  }
}
