package com.example.madfinalproject;

import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import android.content.*;
import android.app.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class LoginEntryDeviceTest {
  @Test public void firstLaunchHasGoogleAndGuestAndLogoutReturnsToSignedOutEntry() throws Exception {
    Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
    Context context=instrumentation.getTargetContext();
    SharedPreferences account=context.getSharedPreferences("boundary-account",0),session=context.getSharedPreferences("boundary-session",0);
    account.edit().clear().commit();session.edit().clear().commit();
    AtomicReference<Intent> opened=new AtomicReference<>();
    Instrumentation.ActivityMonitor monitor=new Instrumentation.ActivityMonitor(){
      @Override public Instrumentation.ActivityResult onStartActivity(Intent intent){
        if(Intent.ACTION_VIEW.equals(intent.getAction())){opened.set(intent);return new Instrumentation.ActivityResult(Activity.RESULT_CANCELED,null);}
        return null;
      }
    };
    instrumentation.addMonitor(monitor);
    try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
      onView(withContentDescription("Sign in with Google")).check(matches(isDisplayed()));
      onView(withText("Explore as a guest")).perform(scrollTo(),click());
      assertTrue(session.getBoolean("guest",false));
      // Model a previously saved personal profile and a linked identity without a server credential.
      context.getSharedPreferences("boundary-profile",0).edit().putString("name","Previous Player").putString("email","private@example.test").commit();
      account.edit().putBoolean("linked",true).putString("id","user_fixture").putString("name","Previous Player").commit();
      java.lang.reflect.Field running=MainActivity.class.getDeclaredField("logoutWorkerRunning");running.setAccessible(true);running.setBoolean(null,true);
      scenario.onActivity(activity->{
        try{java.lang.reflect.Method logout=MainActivity.class.getDeclaredMethod("logout");logout.setAccessible(true);logout.invoke(activity);}
        catch(Exception error){throw new AssertionError(error);}
      });
      onView(withText("Log out")).inRoot(isDialog()).perform(click());
      onView(withText("You're signed out")).check(matches(isDisplayed()));
      assertFalse(PlayerAccount.linked(context));assertTrue(account.getAll().isEmpty());
      assertEquals("",new ProfileStore(context).name());assertEquals("",new ProfileStore(context).email());assertNull(new ProfileStore(context).visiblePhoto());
      assertNotNull(opened.get());assertEquals(PlayerAccount.ORIGIN+"/logout.html",opened.get().getDataString());
      scenario.recreate();onView(withText("You're signed out")).check(matches(isDisplayed()));
    }finally{
      instrumentation.removeMonitor(monitor);account.edit().clear().commit();session.edit().clear().commit();
      java.lang.reflect.Field running=MainActivity.class.getDeclaredField("logoutWorkerRunning");running.setAccessible(true);running.setBoolean(null,false);
      context.getSharedPreferences("boundary-profile",0).edit().clear().commit();
    }
  }

  @Test public void switchingAccountsNeverShowsThePreviousLocalProfile() throws Exception {
    Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
    SharedPreferences account=context.getSharedPreferences("boundary-account",0),session=context.getSharedPreferences("boundary-session",0);
    context.getSharedPreferences("boundary-profile",0).edit().clear().commit();
    context.getSharedPreferences("boundary-profile-user_second",0).edit().clear().commit();
    session.edit().clear().commit();
    try{
      account.edit().clear().putBoolean("linked",true).putString("id","user_first").putString("name","First Player").commit();
      ProfileStore store=new ProfileStore(context);store.saveDetails("Private First Name","first@example.test");store.claim("XI","First Player",true);
      java.io.File firstPhoto=store.photo();
      PlayerAccount.disconnectLocally(context);assertEquals("",store.name());assertTrue(store.claims().isEmpty());
      account.edit().putBoolean("linked",true).putString("id","user_second").putString("name","Second Player").commit();
      session.edit().putBoolean("signedOut",false).commit();
      assertEquals("Second Player",store.name());assertEquals("",store.email());assertTrue(store.claims().isEmpty());assertNotEquals(firstPhoto,store.photo());
      store.saveDetails("Private Second Name","second@example.test");
      assertTrue(store.clearCurrentProfile());assertEquals("",store.email());assertEquals("Second Player",store.name());
      account.edit().putString("id","user_first").putString("name","First Player").commit();
      assertEquals("Private First Name",store.name());assertEquals("first@example.test",store.email());assertFalse(store.claims().isEmpty());assertEquals(firstPhoto,store.photo());
    }finally{
      account.edit().clear().commit();session.edit().clear().commit();context.getSharedPreferences("boundary-profile",0).edit().clear().commit();
      context.getSharedPreferences("boundary-profile-user_second",0).edit().clear().commit();
    }
  }

  @Test public void offlineRemoteRevocationCannotKeepOrRestoreTheActiveAccount() throws Exception {
    Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
    SharedPreferences account=context.getSharedPreferences("boundary-account",0),pending=context.getSharedPreferences("boundary-revocations",0);
    account.edit().clear().putBoolean("linked",true).putString("token","test-only-never-sent").commit();pending.edit().clear().commit();
    try{
      long revision=PlayerAccount.sessionRevision(context);PlayerAccount.disconnectLocally(context);
      assertTrue(account.getAll().isEmpty());assertFalse(PlayerAccount.linked(context));assertTrue(PlayerAccount.sessionRevision(context)>revision);
      try{PlayerAccount.retryRevocations(context,token->{throw new java.io.IOException("offline");});fail("Expected offline failure");}catch(java.io.IOException expected){}
      assertTrue(account.getAll().isEmpty());assertTrue(pending.getStringSet("tokens",java.util.Collections.emptySet()).contains("test-only-never-sent"));
      java.util.concurrent.atomic.AtomicInteger calls=new java.util.concurrent.atomic.AtomicInteger();
      PlayerAccount.retryRevocations(context,token->{assertEquals("test-only-never-sent",token);calls.incrementAndGet();});
      PlayerAccount.retryRevocations(context,token->calls.incrementAndGet());
      assertEquals(1,calls.get());assertTrue(pending.getStringSet("tokens",java.util.Collections.emptySet()).isEmpty());
    }finally{account.edit().clear().commit();pending.edit().clear().commit();context.getSharedPreferences("boundary-session",0).edit().clear().commit();}
  }
}
