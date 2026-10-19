package com.example.madfinalproject;

import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Public production reads only; the bag is local and restored after the test. */
@RunWith(AndroidJUnit4.class)
public class StorePhotoDeviceTest {
  @Test public void originalPhotosLoadAndSelectedSizeReachesBag() throws Exception {
    Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
    android.content.SharedPreferences prefs=context.getSharedPreferences("boundary-store",0);
    String previous=prefs.getString("bag",null);
    prefs.edit().remove("bag").commit();
    try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
      onView(withContentDescription("Store")).perform(click());
      AtomicInteger photos=new AtomicInteger(),loaded=new AtomicInteger();
      for(int i=0;i<300;i++) {
        scenario.onActivity(activity->{
          photos.set(0);loaded.set(0);
          count(activity.getWindow().getDecorView(),photos,loaded);
        });
        if(photos.get()==4&&loaded.get()==4) break;
        Thread.sleep(100);
      }
      assertEquals("Only four product photos",4,photos.get());
      assertEquals("Every original photo must load",4,loaded.get());
      onView(withText("Blue Jersey")).perform(scrollTo(),click());
      onView(withText("XXL")).inRoot(isDialog()).perform(scrollTo(),click());
      onView(withText("Add to bag")).inRoot(isDialog()).perform(scrollTo(),click());
      onView(withText("Added to your bag · View bag")).inRoot(isDialog()).perform(scrollTo()).check(matches(isDisplayed()));
      assertEquals("blue-jersey-xxl:1",prefs.getString("bag",""));
      Bitmap shot=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
      assertNotNull(shot);
      try(FileOutputStream out=new FileOutputStream(new File(context.getExternalFilesDir("ui-checks"),"photo-store.png"))) {
        shot.compress(Bitmap.CompressFormat.PNG,100,out);
      }finally{shot.recycle();}
    }finally{
      if(previous==null)prefs.edit().remove("bag").commit();else prefs.edit().putString("bag",previous).commit();
    }
  }

  private void count(View view,AtomicInteger photos,AtomicInteger loaded) {
    if(view instanceof ProductPhoto) {
      photos.incrementAndGet();
      ImageView image=(ImageView)((ProductPhoto)view).getChildAt(0);
      if(image.getDrawable()!=null) loaded.incrementAndGet();
    }
    if(view instanceof ViewGroup) for(int i=0;i<((ViewGroup)view).getChildCount();i++)
      count(((ViewGroup)view).getChildAt(i),photos,loaded);
  }
}
