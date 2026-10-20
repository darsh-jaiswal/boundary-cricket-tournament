package com.example.madfinalproject;

import android.app.AlertDialog;
import android.content.*;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.*;

/** Team invitation, username lookup and organizer-approved join requests. */
public final class TeamInviteActivity extends AppCompatActivity {
  private final ExecutorService network=Executors.newSingleThreadExecutor();
  private Ui.Palette palette;
  private LinearLayout body,requests;
  private TextView status,linkText;
  private EditText username;
  private String teamId,teamName,link="",owner="";
  private boolean busy;
  private TextView moreRequests;
  private Runnable refreshed=()->{};

  @Override public void onCreate(Bundle state){
    super.onCreate(state);Ui.applyAppearance(this);palette=Ui.palette(this);
    teamId=getIntent().getStringExtra("teamId");teamName=getIntent().getStringExtra("teamName");
    if(teamId==null||!teamId.matches("[a-fA-F0-9-]{36}")||teamName==null){finish();return;}
    ScrollView scroll=new ScrollView(this);body=Ui.column(this);body.setPadding(dp(18),dp(16),dp(18),dp(24));
    scroll.setBackgroundColor(palette.bg);scroll.addView(body);
    android.view.View refreshable=PullRefresh.wrap(this,scroll,done->{
      if(busy||!PlayerAccount.linked(this)){done.run();return;}
      refreshed=done;loadRequests();
    });setContentView(refreshable);UiInsets.apply(refreshable);
    body.addView(Ui.pill(this,palette,"Back",false,this::finish));
    label(body,"Add players to "+teamName,26,true);
    status=label(body,"",14,false);status.setAccessibilityLiveRegion(android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE);
    label(body,"Invite players via",18,true);
    LinearLayout invite=card();label(invite,"Team link",20,true);
    linkText=label(invite,"Your private invitation opens Google sign-in and a request to join this team.",13,false);
    label(invite,"Share with your players. You approve each request before adding a player to the saved squad.",14,false);
    button(invite,"Share via WhatsApp",()->withLink(()->share(true)));
    LinearLayout controls=Ui.row(this);
    controls.addView(Ui.chip(this,palette,"Share",false,()->withLink(()->share(false))),Ui.weight(1));
    controls.addView(Ui.chip(this,palette,"Copy link",false,()->withLink(this::copy)),Ui.weight(1));
    controls.addView(Ui.chip(this,palette,"Team QR",false,()->withLink(this::qr)),Ui.weight(1));invite.addView(controls);
    label(body,"Add an existing player",18,true);
    LinearLayout search=card();label(search,"Search by @username",19,true);
    label(search,"Use the permanent username they chose after Google sign-in.",13,false);
    username=new EditText(this);username.setHint("@username, email or player code");username.setSingleLine(true);
    username.setTextColor(palette.ink);username.setHintTextColor(palette.muted);username.setMinHeight(dp(52));search.addView(username);
    button(search,"Find player",this::find);
    label(body,"Join requests",20,true);button(body,"Refresh join requests",this::loadRequests);
    requests=Ui.column(this);body.addView(requests);
  }

  @Override protected void onResume(){
    super.onResume();
    if(PlayerAccount.linked(this)){
      owner=getSharedPreferences("boundary-account",0).getString("id","");
      SharedPreferences prefs=getSharedPreferences("boundary-team-links",0);
      String key=owner+":"+teamId;
      link=prefs.getLong(key+":expires",0)>System.currentTimeMillis()?prefs.getString(key,""):"";
      if(!validLink(link))link="";
      linkText.setText(link.isEmpty()?"Tap Share, Copy link or Team QR to create an invitation valid for seven days.":link);
      loadRequests();
    }else{
      owner="";link="";requests.removeAllViews();status.setText("Sign in with Google before managing this team.");
      requests.addView(Ui.googleSignIn(this,()->startActivity(new Intent(this,PlayerAccountActivity.class).putExtra("autoBegin",true))));
    }
  }

  private boolean connected(){
    if(PlayerAccount.linked(this))return true;
    status.setText("Continue with Google to invite or add players.");
    startActivity(new Intent(this,PlayerAccountActivity.class).putExtra("autoBegin",true));return false;
  }

  private static boolean validLink(String value){return value.matches("https://boundary-cricket-tournament\\.vercel\\.app/join\\.html\\?invite=[a-f0-9]{64}");}
  private void withLink(Runnable action){
    if(!connected()||busy)return;
    if(validLink(link)){action.run();return;}
    call(()->{
      JSONObject result=PlayerAccount.teamRequest(this,new JSONObject().put("action","create").put("teamId",teamId).put("teamName",teamName));
      String url=result.getString("url");if(!validLink(url))throw new IllegalStateException("Invalid invitation returned by server");
      return result;
    },result->{
      link=result.optString("url");linkText.setText(link);
      getSharedPreferences("boundary-team-links",0).edit().putString(owner+":"+teamId,link)
          .putLong(owner+":"+teamId+":expires",result.optLong("expiresAt",0)).apply();action.run();
    });
  }

  private void share(boolean whatsapp){
    String text="Join "+teamName+" on Boundary: "+link+"\nSign in with Google, choose your @username, and tap Request to join.";
    Intent send=new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,text);
    try{if(whatsapp){send.setPackage("com.whatsapp");startActivity(send);}else startActivity(Intent.createChooser(send,"Share team invitation"));}
    catch(ActivityNotFoundException missing){send.setPackage(null);startActivity(Intent.createChooser(send,"Share team invitation"));}
  }
  private void copy(){((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Boundary team invitation",link));status.setText("Team invitation copied.");}
  private void qr(){
    try{
      LinearLayout box=Ui.column(this);box.setPadding(dp(24),dp(24),dp(24),dp(24));
      ImageView image=new ImageView(this);Bitmap bitmap=ProfileShare.qr(link,dp(240),palette.accent);
      image.setImageBitmap(bitmap);image.setContentDescription("Team invitation QR code for "+teamName);box.addView(image,new LinearLayout.LayoutParams(-1,dp(240)));
      label(box,"Scan to sign in and request to join "+teamName+".",14,false);
      new AlertDialog.Builder(this).setTitle("Team QR / link").setView(box).setPositiveButton("Done",null).setNeutralButton("Copy link",(d,w)->copy()).show();
    }catch(Exception error){status.setText("Could not create the team QR code. Use Copy link instead.");}
  }
  private void find(){
    if(!connected())return;String identifier=username.getText().toString().trim();
    if(identifier.isEmpty()){username.setError("Enter a username");return;}
    call(()->PlayerAccount.request(this,new JSONObject().put("action","lookup").put("identifier",identifier)),player->confirm(player,false));
  }
  private void confirm(JSONObject player,boolean requested){
    if(!requested&&!player.optBoolean("verified")){status.setText("This player has not completed Google registration.");return;}
    new AlertDialog.Builder(this).setTitle(player.optString("name")+" · @"+player.optString("username"))
        .setMessage("Add this registered player to "+teamName+"?").setNegativeButton("Cancel",null)
        .setPositiveButton("Add player",(d,w)->{
          if(requested) call(()->PlayerAccount.teamRequest(this,new JSONObject().put("action","accept").put("teamId",teamId).put("playerId",player.optString("id"))),this::finishPlayer);
          else finishPlayer(player);
        }).show();
  }
  private void finishPlayer(JSONObject player){
    if(!PlayerAccount.linked(this)||!owner.equals(getSharedPreferences("boundary-account",0).getString("id","")))return;
    setResult(RESULT_OK,new Intent().putExtra("playerId",player.optString("id")).putExtra("playerName",player.optString("name")).putExtra("ownerId",owner));finish();
  }
  private void loadRequests(){ loadRequests(null,false); }
  private void loadRequests(JSONObject before,boolean append){
    if(!PlayerAccount.linked(this)||busy)return;
    call(()->PlayerAccount.teamRequest(this,new JSONObject().put("action","requests").put("teamId",teamId).put("before",before)),result->{
      if(!append)requests.removeAllViews();
      if(moreRequests!=null)requests.removeView(moreRequests);
      JSONArray rows=result.optJSONArray("requests");
      if(rows==null||rows.length()==0){label(requests,"No requests yet. Share your team link to invite players.",14,false);return;}
      for(int i=0;i<rows.length();i++){
        JSONObject row=rows.optJSONObject(i);if(row==null)continue;
        String title=row.optString("name")+" · @"+row.optString("username");
        button(requests,title+(row.optString("status").equals("accepted")?" · Add to this device":" · Review request"),()->{
          confirm(row,true);
        });
      }
      JSONObject next=result.optJSONObject("next");
      if(next!=null)moreRequests=button(requests,"Load more join requests",()->loadRequests(next,true));
    });
  }
  private interface Job{JSONObject run()throws Exception;}
  private void call(Job job,java.util.function.Consumer<JSONObject> done){
    if(busy||isFinishing()||isDestroyed())return;busy=true;status.setText("Connecting…");
    long revision=PlayerAccount.sessionRevision(this);String user=owner;
    network.execute(()->{
      JSONObject result=null;Exception error=null;
      try{result=job.run();}catch(Exception failure){error=failure;}
      JSONObject value=result;Exception failure=error;
      runOnUiThread(()->{
        busy=false;Runnable complete=refreshed;refreshed=()->{};complete.run();
        if(isFinishing()||isDestroyed()||revision!=PlayerAccount.sessionRevision(this)
            ||!user.equals(getSharedPreferences("boundary-account",0).getString("id","")))return;
        if(failure!=null){status.setText(failure.getMessage()==null?"Could not load team invitations. Retry when online.":failure.getMessage());return;}
        status.setText("");done.accept(value);
      });
    });
  }
  private LinearLayout card(){LinearLayout card=Ui.column(this);card.setPadding(dp(16),dp(12),dp(16),dp(16));card.setBackground(Ui.round(palette.surface,dp(20)));body.addView(card);return card;}
  private TextView label(LinearLayout parent,String text,int size,boolean bold){TextView view=Ui.text(this,text,size,palette.ink,bold);view.setPadding(0,dp(14),0,dp(12));parent.addView(view);return view;}
  private TextView button(LinearLayout parent,String text,Runnable action){TextView view=Ui.pill(this,palette,text,true,action);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(8),0,dp(8));parent.addView(view,lp);return view;}
  private int dp(int value){return Ui.dp(this,value);}
  @Override protected void onDestroy(){network.shutdownNow();super.onDestroy();}
}
