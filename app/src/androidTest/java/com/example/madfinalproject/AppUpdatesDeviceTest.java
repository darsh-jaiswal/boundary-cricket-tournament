package com.example.madfinalproject;

import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.LinearLayout;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.concurrent.atomic.*;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class AppUpdatesDeviceTest {
  private UpdateRelease future() {
    return new UpdateRelease("9.0.0",2000000000L,
        "https://github.com/darsh-jaiswal/boundary-cricket-tournament/releases/download/v9.0.0/Boundary-Android-arm64.apk",
        "a".repeat(64),95000000);
  }

  @Test public void newReleaseAppearsAndUpdateOpensExactConfirmedDownload() throws Exception {
    Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
    Context context=instrumentation.getTargetContext();
    context.getSharedPreferences(AppUpdates.PROMPT_PREFS,0).edit().clear().commit();
    AtomicReference<AppUpdates> updates=new AtomicReference<>();
    AtomicReference<Intent> opened=new AtomicReference<>();
    AtomicReference<android.widget.TextView> banner=new AtomicReference<>();
    AtomicInteger requests=new AtomicInteger();
    AtomicBoolean fail=new AtomicBoolean();
    Instrumentation.ActivityMonitor monitor=new Instrumentation.ActivityMonitor(){
      @Override public Instrumentation.ActivityResult onStartActivity(Intent intent){
        if(Intent.ACTION_VIEW.equals(intent.getAction())) {opened.set(intent);return new Instrumentation.ActivityResult(Activity.RESULT_CANCELED,null);}
        return null;
      }
    };
    instrumentation.addMonitor(monitor);
    try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
      scenario.onActivity(activity->{
        AppUpdates updater=new AppUpdates(activity,()->{requests.incrementAndGet();if(fail.get())throw new java.io.IOException("offline");return future();},200);
        updates.set(updater);
        LinearLayout root=new LinearLayout(activity);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(0,Ui.dp(activity,48),0,0);
        banner.set((android.widget.TextView)updater.createBanner());root.addView(banner.get());
        activity.setContentView(root);updater.start();
      });
      for(int i=0;i<100;i++){
        AtomicBoolean visible=new AtomicBoolean();scenario.onActivity(a->visible.set(banner.get().getVisibility()==View.VISIBLE));
        if(visible.get())break;Thread.sleep(50);
      }
      // A new release must prompt automatically without tapping the banner.
      Thread.sleep(700);
      onView(withText("Update Boundary")).inRoot(isDialog()).check(matches(isDisplayed()));
      onView(withText("Later")).inRoot(isDialog()).perform(click());
      assertNull(opened.get());
      Thread.sleep(700);
      onView(withText("Update Boundary")).check(androidx.test.espresso.assertion.ViewAssertions.doesNotExist());
      onView(withText("Update available · v9.0.0 · Tap to update")).check(matches(isCompletelyDisplayed()));
      onView(withText("Update available · v9.0.0 · Tap to update")).perform(click());
      onView(withText("Update now")).inRoot(isDialog()).perform(click());
      assertNotNull(opened.get());assertEquals(future().source,opened.get().getDataString());
      // Fail a manual request: display retry guidance, never claim the app is current.
      scenario.onActivity(activity->{fail.set(true);updates.get().checkNow();});
      for(int i=0;i<20;i++){Thread.sleep(50);instrumentation.waitForIdleSync();}
      onView(withText("Couldn't check for updates")).inRoot(isDialog()).check(matches(isDisplayed()));
      onView(withText("OK")).inRoot(isDialog()).perform(click());
      scenario.onActivity(activity->updates.get().stop());
      Thread.sleep(250);int stopped=requests.get();Thread.sleep(500);
      assertEquals("Background polling must stop",stopped,requests.get());
      scenario.onActivity(activity->updates.get().close());
    }finally{instrumentation.removeMonitor(monitor);context.getSharedPreferences(AppUpdates.PROMPT_PREFS,0).edit().clear().commit();}
  }

  @Test public void currentVersionRemainsHiddenAndManualCheckReportsLatest() throws Exception {
    AtomicReference<AppUpdates> updates=new AtomicReference<>();
    try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
      scenario.onActivity(activity->{
        AppUpdates updater=new AppUpdates(activity,()->new UpdateRelease("1.0.1",1,
            "https://github.com/darsh-jaiswal/boundary-cricket-tournament/releases/download/v1.0.1/Boundary-Android-arm64.apk",
            "a".repeat(64),95000000),60000);
        updates.set(updater);LinearLayout root=new LinearLayout(activity);
        View banner=updater.createBanner();banner.setTag("update-banner");root.addView(banner);
        activity.setContentView(root);updater.start();updater.checkNow();
      });
      for(int i=0;i<20;i++){Thread.sleep(50);InstrumentationRegistry.getInstrumentation().waitForIdleSync();}
      onView(withText("You're up to date")).inRoot(isDialog()).check(matches(isDisplayed()));
      scenario.onActivity(activity->updates.get().close());
    }
  }

  @Test public void automaticPromptWaitsUntilTheScoringScreenIsLeft() throws Exception {
    Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
    context.getSharedPreferences(AppUpdates.PROMPT_PREFS,0).edit().clear().commit();
    AtomicReference<AppUpdates> updates=new AtomicReference<>();
    try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
      scenario.onActivity(activity->{
        try{java.lang.reflect.Field page=MainActivity.class.getDeclaredField("page");page.setAccessible(true);page.set(activity,"Score");}
        catch(Exception error){throw new AssertionError(error);}
        AppUpdates updater=new AppUpdates(activity,this::future,200);updates.set(updater);
        LinearLayout root=new LinearLayout(activity);root.setPadding(0,Ui.dp(activity,48),0,0);root.addView(updater.createBanner());
        activity.setContentView(root);updater.start();
      });
      Thread.sleep(1200);
      onView(withText("Update Boundary")).check(androidx.test.espresso.assertion.ViewAssertions.doesNotExist());
      onView(withText("Update available · v9.0.0 · Tap to update")).check(matches(isDisplayed()));
      scenario.onActivity(activity->{
        try{java.lang.reflect.Field page=MainActivity.class.getDeclaredField("page");page.setAccessible(true);page.set(activity,"Matches");}
        catch(Exception error){throw new AssertionError(error);}
        updates.get().refresh();
      });
      Thread.sleep(900);
      onView(withText("Update Boundary")).inRoot(isDialog()).check(matches(isDisplayed()));
      scenario.onActivity(activity->updates.get().close());
    }finally{context.getSharedPreferences(AppUpdates.PROMPT_PREFS,0).edit().clear().commit();}
  }
}
