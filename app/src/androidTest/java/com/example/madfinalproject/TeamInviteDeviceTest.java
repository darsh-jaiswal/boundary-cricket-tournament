package com.example.madfinalproject;

import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.madfinalproject.data.Repository;
import com.example.madfinalproject.domain.Cricket.Team;
import com.example.madfinalproject.domain.RegisteredPlayer;
import java.io.*;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class TeamInviteDeviceTest {
  private static final String OWNER="user_teamfixture",TEAM="10000000-0000-4000-8000-000000000001";
  private Context context(){return InstrumentationRegistry.getInstrumentation().getTargetContext();}
  private void signInFixture(){context().getSharedPreferences("boundary-account",0).edit().clear().putBoolean("linked",true).putString("id",OWNER).putString("name","Fixture Organizer").commit();}
  private void clean(){context().getSharedPreferences("boundary-account",0).edit().clear().commit();context().getSharedPreferences("boundary-session",0).edit().clear().commit();context().getSharedPreferences("boundary-team-links",0).edit().clear().commit();}

  @Test public void invitationCopyAndQrUseTheSameTeamLink() throws Exception {
    signInFixture();String url=PlayerAccount.ORIGIN+"/join.html?invite="+"a".repeat(64);
    context().getSharedPreferences("boundary-team-links",0).edit().putString(OWNER+":"+TEAM,url).putLong(OWNER+":"+TEAM+":expires",System.currentTimeMillis()+60000).commit();
    Intent intent=new Intent(context(),TeamInviteActivity.class).putExtra("teamId",TEAM).putExtra("teamName","Boundary Test XI");
    try(ActivityScenario<TeamInviteActivity> scenario=ActivityScenario.launch(intent)) {
      onView(withText("Copy link")).perform(scrollTo(),click());
      scenario.onActivity(activity->{ClipboardManager clipboard=(ClipboardManager)activity.getSystemService(Context.CLIPBOARD_SERVICE);assertEquals(url,clipboard.getPrimaryClip().getItemAt(0).getText().toString());});
      onView(withText("Team QR")).perform(scrollTo(),click());
      onView(withContentDescription("Team invitation QR code for Boundary Test XI")).inRoot(isDialog()).check(matches(isDisplayed()));
      onView(withText("Done")).inRoot(isDialog()).perform(click());
      onView(withText("Share via WhatsApp")).perform(scrollTo()).check(matches(isDisplayed()));
      Bitmap bitmap=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();assertNotNull(bitmap);
      try(FileOutputStream out=new FileOutputStream(new File(context().getExternalFilesDir("ui-checks"),"team-invitation.png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}finally{bitmap.recycle();}
    }finally{clean();}
  }

  @Test public void sameRegisteredAccountCannotBeAddedTwiceAfterItsNameChanges() throws Exception {
    signInFixture();context().getSharedPreferences("MainActivity",0).edit().putString("role","Organizer").commit();
    Repository repo=new Repository(context());Team team=new Team("Receipt fixture XI");repo.save(team);
    try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
      for(String name:new String[]{"Original Player","Changed Player"}){
        scenario.onActivity(activity->{
          activity.getPreferences(0).edit().putString("addingPlayerTeam",team.id).commit();
          activity.onActivityResult(88,Activity.RESULT_OK,new Intent().putExtra("ownerId",OWNER).putExtra("playerId","user_playerfixture").putExtra("playerName",name));
        });
      }
      onView(withText("Already in this team")).inRoot(isDialog()).check(matches(isDisplayed()));
      Team saved=repo.teams().stream().filter(t->t.id.equals(team.id)).findFirst().get();assertEquals(1,saved.players.size());assertEquals("Original Player",saved.players.get(0));
      assertEquals(1,repo.list("registered-player",RegisteredPlayer.class).stream().filter(p->p.teamId.equals(team.id)).count());
    }finally{repo.getWritableDatabase().delete("records","id=? OR id LIKE ?",new String[]{team.id,team.id+":%"});repo.close();clean();}
  }
}
