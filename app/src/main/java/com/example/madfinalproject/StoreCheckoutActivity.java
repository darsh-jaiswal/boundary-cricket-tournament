package com.example.madfinalproject;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.razorpay.Checkout;
import com.razorpay.PaymentData;
import com.razorpay.PaymentResultWithDataListener;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;

/** Account-scoped delivery details and server-priced checkout with the official Razorpay SDK. */
public final class StoreCheckoutActivity extends AppCompatActivity implements PaymentResultWithDataListener {
  private Ui.Palette palette;
  private LinearLayout root, content, bottom;
  private TextView status;
  private Storefront.Catalog catalog;
  private List<Storefront.Line> bag;
  private JSONObject address;
  private SharedPreferences checkoutPrefs;
  private String owner, orderId="", requestId="";
  private boolean busy, editing, bagChanged;
  private final ExecutorService network=Executors.newSingleThreadExecutor();
  private final Map<String,EditText> inputs=new LinkedHashMap<>();

  @Override public void onCreate(Bundle state){
    super.onCreate(state);Ui.applyAppearance(this);palette=Ui.palette(this);
    if(!PlayerAccount.linked(this)){finish();return;}
    owner=getSharedPreferences("boundary-account",0).getString("id","");
    checkoutPrefs=getSharedPreferences("boundary-checkout-"+owner,0);
    bag=Storefront.parse(getSharedPreferences("boundary-store",0).getString("bag",""));
    try{address=new JSONObject(checkoutPrefs.getString("address","{}"));}catch(JSONException error){address=new JSONObject();}
    orderId=checkoutPrefs.getString("orderId","");
    if(!orderId.isEmpty()&&!checkoutPrefs.getString("orderBag", "").equals(Storefront.encode(bag))){bagChanged=true;}
    requestId=checkoutPrefs.getString("requestId","");
    Checkout.preload(getApplicationContext());draw();
    run(()->StoreView.fetch(),value->{catalog=StoreView.parse(value);draw();});
  }
  @Override protected void onResume(){super.onResume();if(owner!=null&&!sameAccount())finish();}
  private boolean sameAccount(){return PlayerAccount.linked(this)&&owner.equals(getSharedPreferences("boundary-account",0).getString("id",""));}
  private void draw(){
    root=Ui.column(this);root.setBackgroundColor(palette.bg);
    LinearLayout header=Ui.row(this);header.setPadding(dp(16),dp(10),dp(16),dp(10));
    header.addView(Ui.chip(this,palette,"Back",false,()->{if(editing){editing=false;draw();}else finish();}));
    header.addView(Ui.text(this,editing?"Delivery address":"Your cart",23,palette.ink,true),Ui.weight(1));root.addView(header);
    ScrollView scroll=new ScrollView(this);content=Ui.column(this);content.setPadding(dp(18),dp(8),dp(18),dp(24));scroll.addView(content);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
    status=Ui.text(this,busy?"Connecting…":"",14,palette.muted,false);status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);content.addView(status);
    bottom=Ui.column(this);bottom.setPadding(dp(18),dp(12),dp(18),dp(12));bottom.setBackgroundColor(palette.surface);root.addView(bottom);
    setContentView(root);UiInsets.apply(root);
    if(editing){addressForm();return;}
    if(bagChanged){label("Your cart changed after the unpaid order was created. Cancel that order to checkout this cart.",17);content.addView(button("Cancel unpaid order",()->run(()->PlayerAccount.shopRequest(this,new JSONObject().put("action","cancel").put("orderId",orderId)),value->{clearOrder();draw();})));return;}
    if(bag.isEmpty()){label("Your cart is empty. Choose a jersey or cap from the store.",17);return;}
    long subtotal=0;boolean available=catalog!=null;
    for(Storefront.Line line:new ArrayList<>(bag)){
      Storefront.Variant variant=catalog==null?null:catalog.variant(line.variantId);
      Storefront.Product product=catalog==null?null:catalog.productOf(line.variantId);
      LinearLayout card=Ui.card(this,palette),row=Ui.row(this),details=Ui.column(this);
      if(product!=null)row.addView(new ProductPhoto(this,product.id,product.name,palette),new LinearLayout.LayoutParams(dp(92),dp(125)));
      details.setPadding(dp(12),0,0,0);details.addView(Ui.text(this,product==null?"Loading item…":product.name,17,palette.ink,true));
      if(variant!=null){subtotal+=(long)variant.pricePaise*line.quantity;available&=variant.available;
        details.addView(Ui.text(this,variant.colour+" · "+Storefront.rupees(variant.pricePaise),14,palette.muted,false));
        List<Storefront.Variant> sizes=product.variants;
        Spinner size=new Spinner(this);List<String> labels=new ArrayList<>();int selected=0;
        for(int i=0;i<sizes.size();i++){labels.add("Size: "+sizes.get(i).size);if(sizes.get(i).id.equals(variant.id))selected=i;}
        size.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,labels));size.setSelection(selected);size.setEnabled(!busy&&orderId.isEmpty());
        size.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
          public void onNothingSelected(android.widget.AdapterView<?> parent){}
          public void onItemSelected(android.widget.AdapterView<?> parent,View view,int position,long id){String next=sizes.get(position).id;if(!next.equals(line.variantId)){
            Storefront.Line existing=null;for(Storefront.Line item:bag)if(item.variantId.equals(next))existing=item;
            int qty=line.quantity+(existing==null?0:existing.quantity);
            if(qty>Storefront.MAX_QUANTITY){status.setText("Maximum ten of one size.");return;}
            Storefront.setQuantity(bag,line.variantId,0);if(existing==null)bag.add(new Storefront.Line(next,qty));else Storefront.setQuantity(bag,next,qty);changed();
          }}
        });details.addView(size);
      }
      LinearLayout quantity=Ui.row(this);
      quantity.addView(button("−",()->{if(line.quantity>1){Storefront.setQuantity(bag,line.variantId,line.quantity-1);changed();}}));
      quantity.addView(Ui.text(this,"  "+line.quantity+"  ",17,palette.ink,true));
      quantity.addView(button("+",()->{if(line.quantity<10){Storefront.setQuantity(bag,line.variantId,line.quantity+1);changed();}}));
      details.addView(quantity);details.addView(button("Remove",()->{Storefront.setQuantity(bag,line.variantId,0);changed();}));
      row.addView(details,Ui.weight(1));card.addView(row);content.addView(card);content.addView(Ui.space(this,1,14));
    }
    LinearLayout delivery=Ui.card(this,palette);
    delivery.addView(Ui.text(this,address.length()==0?"Where do we send this?":"Deliver to",18,palette.ink,true));
    if(address.length()>0)delivery.addView(Ui.text(this,address.optString("name")+"\n"+address.optString("address")+", "+address.optString("city")+", "+address.optString("state")+"\nPIN: "+address.optString("pincode")+" · "+address.optString("phone"),15,palette.ink,false));
    delivery.addView(button(address.length()==0?"Add delivery address":"Edit address",()->{if(!orderId.isEmpty()){status.setText("Cancel the unpaid order before changing its delivery address.");return;}editing=true;draw();}));content.addView(delivery);
    label("Price details",21);summary("Items",Storefront.rupees(subtotal));
    int shipping=catalog==null?-1:catalog.shippingPaise;
    summary("Delivery fee",shipping<0?"Loading…":shipping==0?"Free":Storefront.rupees(shipping));
    summary("Total amount",catalog==null?"Loading…":Storefront.rupees(subtotal+Math.max(shipping,0)));
    label("By paying, you agree to the store Terms & Conditions and Privacy Policy. Delivery is within India.",13);
    LinearLayout policies=Ui.row(this);for(String[] link:new String[][]{{"Terms","terms"},{"Privacy","privacy"},{"Returns","returns"}})policies.addView(Ui.chip(this,palette,link[0],false,()->startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW,android.net.Uri.parse(PlayerAccount.ORIGIN+"/"+link[1]+".html")))));content.addView(policies);
    if(!orderId.isEmpty()){
      label("An unpaid order is saved. Resume payment, or cancel it before changing your cart.",14);
      content.addView(button("Cancel unpaid order",()->run(()->PlayerAccount.shopRequest(this,new JSONObject().put("action","cancel").put("orderId",orderId)),value->{clearOrder();draw();status.setText("Unpaid order cancelled.");})));
    }
    bottom.addView(Ui.text(this,"Total amount  "+(catalog==null?"…":Storefront.rupees(subtotal+Math.max(shipping,0))),19,palette.ink,true));
    TextView pay=Ui.pill(this,palette,address.length()==0?"Add address":orderId.isEmpty()?"Pay":"Resume payment",true,()->{if(address.length()==0){editing=true;draw();}else pay();});
    pay.setEnabled(!busy&&(!orderId.isEmpty()||(available&&catalog.salesEnabled)));pay.setAlpha(pay.isEnabled()?1f:.5f);bottom.addView(pay);
  }
  private TextView button(String text,Runnable action){TextView view=Ui.chip(this,palette,text,false,()->{if(!busy)action.run();});view.setEnabled(!busy);view.setMinHeight(dp(48));return view;}
  private void label(String value,int size){TextView text=Ui.text(this,value,size,palette.ink,size>=18);text.setPadding(0,dp(14),0,dp(10));content.addView(text);}
  private void summary(String title,String value){LinearLayout row=Ui.row(this);row.setPadding(0,dp(8),0,dp(8));row.addView(Ui.text(this,title,16,palette.muted,false),Ui.weight(1));row.addView(Ui.text(this,value,17,palette.ink,true));content.addView(row);}
  private void changed(){if(!orderId.isEmpty()){status.setText("Cancel the saved unpaid order before changing items.");bag=Storefront.parse(getSharedPreferences("boundary-store",0).getString("bag",""));return;}
    getSharedPreferences("boundary-store",0).edit().putString("bag",Storefront.encode(bag)).apply();requestId="";checkoutPrefs.edit().remove("requestId").apply();draw();}
  private void addressForm(){
    inputs.clear();label("Contact details",21);
    field("name","Full name",getSharedPreferences("boundary-account",0).getString("name",""));field("phone","Mobile number for delivery","");
    label("Your Google account email is used for order updates. No phone sign-in is required.",13);
    label("Address",21);field("house","House / building","");field("locality","Locality / town","");field("landmark","Landmark (optional)","");field("pincode","PIN code","");field("city","City","");field("state","State","");
    bottom.addView(Ui.pill(this,palette,"Save address",true,()->{
      for(Map.Entry<String,EditText> entry:inputs.entrySet())if(!entry.getKey().equals("landmark")&&entry.getValue().getText().toString().trim().length()<2){entry.getValue().setError("Required");entry.getValue().requestFocus();return;}
      if(!value("phone").matches("[6-9][0-9]{9}")){inputs.get("phone").setError("Enter a 10-digit Indian mobile number");return;}
      if(!value("pincode").matches("[1-9][0-9]{5}")){inputs.get("pincode").setError("Enter a 6-digit PIN code");return;}
      try{JSONObject saved=new JSONObject();for(String key:inputs.keySet())saved.put(key,value(key));saved.put("address",value("house")+", "+value("locality")+(value("landmark").isEmpty()?"":", "+value("landmark")));
        if(saved.getString("address").length()>250){status.setText("Shorten the address to 250 characters.");return;}
        address=saved;checkoutPrefs.edit().putString("address",address.toString()).remove("requestId").apply();requestId="";editing=false;draw();
      }catch(JSONException error){status.setText("Could not save this address.");}
    }));
  }
  private String value(String key){return inputs.get(key).getText().toString().trim();}
  private void field(String key,String title,String fallback){label(title,14);EditText edit=new EditText(this);edit.setText(address.optString(key,fallback));edit.setSingleLine(true);edit.setTextColor(palette.ink);edit.setMinHeight(dp(52));edit.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(key.equals("phone")?10:key.equals("pincode")?6:100)});if(key.equals("phone")||key.equals("pincode"))edit.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);inputs.put(key,edit);content.addView(edit);}
  private void pay(){
    if(busy)return;
    if(!orderId.isEmpty()){run(()->PlayerAccount.shopOrder(this,orderId),this::openPayment);return;}
    if(requestId.isEmpty()){requestId=UUID.randomUUID().toString();checkoutPrefs.edit().putString("requestId",requestId).apply();}
    run(()->{JSONArray items=new JSONArray();for(Storefront.Line line:bag)items.put(new JSONObject().put("variantId",line.variantId).put("quantity",line.quantity));return PlayerAccount.shopRequest(this,new JSONObject().put("action","checkout").put("requestId",requestId).put("termsVersion","2026-10-19").put("items",items).put("shipping",address));},this::openPayment);
  }
  private void openPayment(JSONObject order){
    if("paid".equals(order.optString("status"))||"refunded".equals(order.optString("status"))){confirmed(order);return;}
    orderId=order.optString("id");checkoutPrefs.edit().putString("orderId",orderId).putString("orderBag",Storefront.encode(bag)).apply();
    if(!"checkout".equals(order.optString("status"))||order.optString("razorpayOrderId").isEmpty()){draw();status.setText("Payment is being checked. Please retry shortly.");return;}
    try{Checkout checkout=new Checkout();checkout.setKeyID(order.getString("keyId"));JSONObject options=new JSONObject().put("name","Boundary Club Shop").put("description","Boundary teamwear").put("order_id",order.getString("razorpayOrderId")).put("amount",order.getLong("amountPaise")).put("currency","INR").put("theme",new JSONObject().put("color","#147653"));if(order.has("prefill"))options.put("prefill",order.getJSONObject("prefill"));busy=true;draw();checkout.open(this,options);}catch(Exception error){busy=false;draw();status.setText("Could not open Razorpay. Your order is saved; tap Resume payment.");}
  }
  @Override public void onPaymentSuccess(String paymentId,PaymentData data){busy=false;if(!sameAccount()){finish();return;}run(()->PlayerAccount.shopRequest(this,new JSONObject().put("action","verify").put("orderId",orderId).put("razorpay_payment_id",data.getPaymentId()).put("razorpay_order_id",data.getOrderId()).put("razorpay_signature",data.getSignature())),this::confirmed);}
  private void confirmed(JSONObject order){if("paid".equals(order.optString("status"))||"refunded".equals(order.optString("status"))){bag.clear();getSharedPreferences("boundary-store",0).edit().remove("bag").apply();clearOrder();draw();status.setText("Order "+order.optString("id")+" · "+order.optString("status"));}else{draw();status.setText("Payment is awaiting confirmation. Resume payment to check its status before paying again.");}}
  @Override public void onPaymentError(int code,String response,PaymentData data){busy=false;draw();status.setText("Payment was not completed. Your order is saved; you can resume or cancel it.");}
  private void clearOrder(){orderId="";requestId="";bagChanged=false;checkoutPrefs.edit().remove("orderId").remove("orderBag").remove("requestId").apply();}
  private interface Job{JSONObject run()throws Exception;}
  private void run(Job job,java.util.function.Consumer<JSONObject> success){if(busy)return;busy=true;draw();network.execute(()->{try{JSONObject value=job.run();runOnUiThread(()->{if(isFinishing()||isDestroyed()||!sameAccount())return;busy=false;success.accept(value);});}catch(Exception error){runOnUiThread(()->{if(isFinishing()||isDestroyed())return;busy=false;draw();status.setText(error.getMessage()==null?"Connection failed. Please retry.":error.getMessage());});}});}
  private int dp(int value){return Ui.dp(this,value);}
  @Override protected void onDestroy(){network.shutdownNow();super.onDestroy();}
}
