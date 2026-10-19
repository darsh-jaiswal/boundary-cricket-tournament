package com.example.madfinalproject;

import static org.junit.Assert.*;
import android.content.Context;
import android.content.Intent;
import android.app.Activity;
import android.app.Instrumentation;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.madfinalproject.data.Repository;
import com.example.madfinalproject.domain.Cricket.Team;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class LogoutDeviceTest {
  @Test public void recreatedActivityCompletesPendingBrowserSignOut() throws Exception {
    Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
    Context context=instrumentation.getTargetContext();
    android.content.SharedPreferences prefs=context.getSharedPreferences("MainActivity",0);
    java.util.concurrent.atomic.AtomicReference<Intent> opened=new java.util.concurrent.atomic.AtomicReference<>();
    Instrumentation.ActivityMonitor monitor=new Instrumentation.ActivityMonitor(){
      @Override public Instrumentation.ActivityResult onStartActivity(Intent intent){
        if(Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getDataString()!=null
            && intent.getDataString().equals(PlayerAccount.ORIGIN+"/logout.html")) {
          opened.set(intent);return new Instrumentation.ActivityResult(Activity.RESULT_CANCELED,null);
        }
        return null;
      }
    };
    prefs.edit().putString("role","Organizer").putBoolean("logoutInProgress",true).commit();
    java.lang.reflect.Field running=MainActivity.class.getDeclaredField("logoutWorkerRunning");running.setAccessible(true);running.setBoolean(null,true);
    instrumentation.addMonitor(monitor);
    try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
      scenario.recreate();
      // Model the worker completing after the original Activity has been destroyed.
      prefs.edit().putBoolean("logoutInProgress",false).putBoolean("browserSignOutPending",true).putString("role","Spectator").commit();
      running.setBoolean(null,false);
      for(int i=0;i<30 && opened.get()==null;i++)Thread.sleep(100);
      assertNotNull("Recreated activity must open browser logout",opened.get());
      assertFalse(prefs.getBoolean("browserSignOutPending",false));
      scenario.onActivity(activity->{
        try{java.lang.reflect.Field role=MainActivity.class.getDeclaredField("role");role.setAccessible(true);assertEquals("Spectator",role.get(activity));}
        catch(ReflectiveOperationException error){throw new AssertionError(error);}
      });
    } finally {running.setBoolean(null,false);instrumentation.removeMonitor(monitor);prefs.edit().remove("logoutInProgress").remove("browserSignOutPending").remove("logoutError").putString("role","Organizer").commit();}
  }

  @Test public void interruptedProcessStopsPollingAndOffersRetry() throws Exception {
    Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
    android.content.SharedPreferences main=context.getSharedPreferences("MainActivity",0),account=context.getSharedPreferences("boundary-account",0);
    java.lang.reflect.Field running=MainActivity.class.getDeclaredField("logoutWorkerRunning");running.setAccessible(true);running.setBoolean(null,false);
    main.edit().putBoolean("logoutInProgress",true).commit();account.edit().putString("token","test-only-not-sent").commit();
    try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
      assertFalse(main.getBoolean("logoutInProgress",false));
      assertEquals("test-only-not-sent",account.getString("token",""));
      androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withText("Logout did not finish"))
          .check(androidx.test.espresso.assertion.ViewAssertions.matches(androidx.test.espresso.matcher.ViewMatchers.isDisplayed()));
    }finally{main.edit().remove("logoutInProgress").remove("logoutError").commit();account.edit().clear().commit();}
  }

  @Test public void clearingUnlinkedSessionRemovesIdentityAndPreservesTeams() throws Exception {
    Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
    android.content.SharedPreferences prefs=context.getSharedPreferences("boundary-account",0);
    // No server token: tests local teardown without making a real player-service request.
    prefs.edit().clear().putBoolean("linked",true).putString("id","user_logouttest")
        .putString("name","Logout Test").putString("username","logout_test").commit();
    Repository repo=new Repository(context);Team team=new Team("Logout persistence test");repo.save(team);
    try {
      PlayerAccount.signOutDevice(context);
      assertFalse(PlayerAccount.linked(context));assertTrue(prefs.getAll().isEmpty());
      boolean retained=false;for(Team saved:repo.teams())if(saved.id.equals(team.id))retained=true;
      assertTrue("Logout must retain saved teams",retained);
    } finally {repo.getWritableDatabase().delete("records","id=?",new String[]{team.id});repo.close();prefs.edit().clear().commit();}
  }
}
