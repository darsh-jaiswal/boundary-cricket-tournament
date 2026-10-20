package com.example.madfinalproject;

import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import android.content.Context;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.madfinalproject.data.Repository;
import com.example.madfinalproject.domain.Cricket.Team;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class PullRefreshDeviceTest {
  @Test public void pullingAtTheTopReloadsSavedTeamsWithoutLosingThem() throws Exception {
    Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
    context.getSharedPreferences("boundary-session",0).edit().putBoolean("guest",true).commit();
    Repository repo=new Repository(context);Team added=new Team("Pull refresh fixture XI");
    AtomicReference<LinearLayout> original=new AtomicReference<>();
    try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
      scenario.onActivity(activity->{
        try{
          java.lang.reflect.Field page=MainActivity.class.getDeclaredField("page");page.setAccessible(true);page.set(activity,"Teams");
          java.lang.reflect.Method draw=MainActivity.class.getDeclaredMethod("draw");draw.setAccessible(true);draw.invoke(activity);
          java.lang.reflect.Field body=MainActivity.class.getDeclaredField("body");body.setAccessible(true);original.set((LinearLayout)body.get(activity));
        }catch(Exception error){throw new AssertionError(error);}
      });
      repo.save(added);
      onView(isAssignableFrom(SwipeRefreshLayout.class)).perform(swipeDown());
      onView(withText("Pull refresh fixture XI")).check(matches(isDisplayed()));
      scenario.onActivity(activity->{
        try{java.lang.reflect.Field body=MainActivity.class.getDeclaredField("body");body.setAccessible(true);assertNotSame(original.get(),body.get(activity));}
        catch(Exception error){throw new AssertionError(error);}
      });
      assertTrue(repo.teams().stream().anyMatch(team->team.id.equals(added.id)));
    }finally{repo.getWritableDatabase().delete("records","id=?",new String[]{added.id});repo.close();context.getSharedPreferences("boundary-session",0).edit().clear().commit();}
  }
}
