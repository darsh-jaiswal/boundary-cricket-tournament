package com.example.madfinalproject;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.widget.NestedScrollView;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** The Store tab: shelves of Boundary teamwear, a bag kept on the phone, and checkout on the website with Razorpay. */
public final class StoreView extends LinearLayout {
  private static final String PREFS = "boundary-store", BAG = "bag", SHOP = "/shop.html";
  private static final long FRESH_MS = 2 * 60_000L;
  private static final int MAX_RESPONSE = 256_000;
  // Kept across redraws of the tab so switching tabs does not refetch every time.
  private static Storefront.Catalog cached;
  private static long cachedAt;

  private final Activity activity;
  private final Ui.Palette p;
  private final int gutter;
  private final TextView status, badge;
  private final FrameLayout bagButton;
  private final LinearLayout shelves;
  private final List<Storefront.Line> bag;
  private Storefront.Catalog catalog;
  private boolean loading;
  private boolean fetching, failed;
  private Runnable refreshed = () -> {};

  public StoreView(Activity activity, Ui.Palette palette) {
    super(activity);
    this.activity = activity;
    p = palette;
    gutter = dp(18);
    setOrientation(VERTICAL);
    bag = Storefront.parse(activity.getSharedPreferences(PREFS, 0).getString(BAG, ""));

    LinearLayout bar = Ui.row(activity);
    bar.setPadding(gutter, dp(2), gutter, dp(2));
    status = Ui.text(activity, "", 13, p.muted, false);
    status.setPadding(0, 0, dp(10), 0);
    bar.addView(status, Ui.weight(1));
    FrameLayout share = Ui.iconButton(activity, p, "share", 44, p.surface, p.ink, this::share);
    share.setBackground(ring());
    share.setContentDescription("Share the Boundary Store");
    bar.addView(share);
    bar.addView(Ui.space(activity, 10, 1));
    bagButton = Ui.iconButton(activity, p, "bag", 44, p.surface, p.ink, this::openBag);
    bagButton.setBackground(ring());
    badge = Ui.text(activity, "", 10, p.onAccent, true);
    badge.setGravity(Gravity.CENTER);
    badge.setMinWidth(dp(18));
    badge.setPadding(dp(4), 0, dp(4), 0);
    badge.setBackground(Ui.round(p.accent, dp(9)));
    bagButton.addView(badge, new FrameLayout.LayoutParams(-2, dp(18), Gravity.TOP | Gravity.END));
    bar.addView(bagButton);
    addView(bar);

    shelves = Ui.column(activity);
    addView(shelves);
    addView(footer());
    refreshBadge();
    load();
  }

  @Override protected void onAttachedToWindow() {
    super.onAttachedToWindow();
    // Shelves scroll edge to edge like the reference, so the store cancels the page body's side padding.
    if (getParent() instanceof ViewGroup && getLayoutParams() instanceof MarginLayoutParams) {
      ViewGroup parent = (ViewGroup) getParent();
      MarginLayoutParams lp = (MarginLayoutParams) getLayoutParams();
      if (lp.leftMargin != -parent.getPaddingLeft() || lp.rightMargin != -parent.getPaddingRight()) {
        lp.leftMargin = -parent.getPaddingLeft();
        lp.rightMargin = -parent.getPaddingRight();
        setLayoutParams(lp);
      }
      parent.setClipToPadding(false);
    }
  }

  // ---- Catalog ----

  private void load() {
    if (fetching) return;
    Storefront.Catalog known;
    boolean fresh;
    synchronized (StoreView.class) {
      known = cached;
      fresh = known != null && System.currentTimeMillis() - cachedAt < FRESH_MS;
    }
    catalog = known;
    loading = known == null;
    render();
    if (fresh) { finishRefresh(); return; }
    fetching = true;
    Handler main = new Handler(Looper.getMainLooper());
    new Thread(() -> {
      Storefront.Catalog result = null;
      try {
        result = parse(fetch());
        synchronized (StoreView.class) {
          cached = result;
          cachedAt = System.currentTimeMillis();
        }
      } catch (Exception offline) {
        // A stale catalog stays on screen; with none, a retry prompt is shown.
      }
      Storefront.Catalog done = result;
      main.post(() -> {
        fetching = false;
        failed = done == null;
        loading = false;
        if (done != null) catalog = done;
        render();
        finishRefresh();
      });
    }, "boundary-store-catalog").start();
  }

  public void refresh(Runnable complete) {
    refreshed = complete;
    synchronized (StoreView.class) { cachedAt = 0; }
    load();
  }

  private void finishRefresh() { Runnable done = refreshed; refreshed = () -> {}; done.run(); }

  static JSONObject fetch() throws Exception {
    HttpURLConnection connection = (HttpURLConnection) new URL(PlayerAccount.ORIGIN + "/api/shop").openConnection();
    connection.setConnectTimeout(10000);
    connection.setReadTimeout(15000);
    connection.setRequestProperty("Accept", "application/json");
    try {
      int code = connection.getResponseCode();
      if (code != 200) throw new IllegalStateException("Store returned " + code);
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      try (InputStream in = connection.getInputStream()) {
        byte[] buffer = new byte[4096];
        int count;
        while ((count = in.read(buffer)) != -1) {
          bytes.write(buffer, 0, count);
          if (bytes.size() > MAX_RESPONSE) throw new IllegalStateException("Store response too large");
        }
      }
      return new JSONObject(bytes.toString("UTF-8"));
    } finally {
      connection.disconnect();
    }
  }

  static Storefront.Catalog parse(JSONObject json) {
    List<Storefront.Product> products = new ArrayList<>();
    JSONArray items = json.optJSONArray("products");
    for (int i = 0; items != null && i < items.length(); i++) {
      JSONObject o = items.optJSONObject(i);
      if (o == null || str(o, "id").isEmpty() || str(o, "name").isEmpty()) continue;
      List<Storefront.Variant> variants = new ArrayList<>();
      JSONArray vs = o.optJSONArray("variants");
      for (int j = 0; vs != null && j < vs.length(); j++) {
        JSONObject v = vs.optJSONObject(j);
        if (v == null || !Storefront.validVariantId(str(v, "id"))) continue;
        variants.add(new Storefront.Variant(str(v, "id"), str(v, "material"), str(v, "size"), str(v, "colour"),
            Math.max(0, v.optInt("pricePaise", 0)), v.optBoolean("available", false)));
      }
      products.add(new Storefront.Product(str(o, "id"), str(o, "name"), str(o, "category"), str(o, "description"), variants, false));
    }
    int shipping = json.isNull("shippingPaise") ? -1 : json.optInt("shippingPaise", -1);
    return new Storefront.Catalog(products, json.optBoolean("salesEnabled", false), shipping);
  }

  private static String str(JSONObject o, String key) { return o.isNull(key) ? "" : o.optString(key, ""); }

  // ---- Shelves ----

  private void render() {
    shelves.removeAllViews();
    if (loading) status.setText("Loading the collection…");
    else if (catalog == null) status.setText("Couldn't reach the store. Tap Retry to load the collection.");
    else if (failed) status.setText("Couldn't refresh · Showing the saved collection. Pull down to retry.");
    else status.setText(catalog.salesEnabled ? "Checkout open · pay securely with Razorpay"
        : "Preview · checkout opens once stock is confirmed");
    if (!loading && catalog == null) {
      LinearLayout retry = Ui.row(activity);
      retry.setPadding(gutter, dp(10), gutter, 0);
      retry.addView(Ui.chip(activity, p, "Retry", false, this::load));
      shelves.addView(retry);
    }
    List<Storefront.Product> live = catalog == null ? Collections.emptyList() : catalog.products;
    for (Storefront.Shelf shelf : Storefront.shelves(live))
      if (loading || !shelf.products.isEmpty()) shelves.addView(shelf(shelf, loading));
    if (!loading && catalog != null && Storefront.shelves(live).stream().allMatch(s -> s.products.isEmpty()))
      status.setText("The collection is currently empty. Please check back soon.");
  }

  private View shelf(Storefront.Shelf shelf, boolean placeholder) {
    LinearLayout box = Ui.column(activity);
    LinearLayout head = Ui.row(activity);
    head.setPadding(gutter, dp(24), gutter - dp(2), dp(14));
    LinearLayout titles = Ui.column(activity);
    titles.addView(Ui.text(activity, Storefront.EYEBROW, 12, p.muted, false));
    TextView title = Ui.text(activity, shelf.title, 24, p.ink, true);
    title.setPadding(0, dp(6), 0, 0);
    titles.addView(title);
    head.addView(titles, Ui.weight(1));
    if (!placeholder) {
      head.addView(new IconView(activity, "chevron-right", p.ink), new LayoutParams(dp(26), dp(26)));
      head.setOnClickListener(v -> openShelf(shelf));
      head.setContentDescription("See all " + shelf.title);
      ripple(head);
    }
    box.addView(head);

    HorizontalScrollView scroller = new HorizontalScrollView(activity);
    scroller.setHorizontalScrollBarEnabled(false);
    scroller.setClipToPadding(false);
    scroller.setPadding(gutter, 0, gutter, dp(4));
    LinearLayout track = Ui.row(activity);
    track.setGravity(Gravity.TOP);
    int width = cardWidth();
    int count = placeholder ? 2 : shelf.products.size();
    for (int i = 0; i < count; i++) {
      View card;
      if (placeholder) {
        card = new View(activity);
        card.setBackground(Ui.round(p.surface2, dp(16)));
      } else card = card(shelf.products.get(i), width);
      LayoutParams lp = new LayoutParams(width, placeholder ? artHeight(width) + dp(96) : -2);
      if (i > 0) lp.leftMargin = dp(12);
      track.addView(card, lp);
    }
    scroller.addView(track);
    box.addView(scroller);
    return box;
  }

  private View card(Storefront.Product product, int width) {
    LinearLayout card = Ui.column(activity);
    card.setBackground(Ui.outline(p.surface, p.line, Math.max(1, dp(1)), dp(16)));
    card.setClipToOutline(true);
    card.addView(artFrame(product), new LayoutParams(-1, artHeight(width)));
    LinearLayout info = Ui.column(activity);
    info.setPadding(dp(14), dp(12), dp(14), dp(16));
    info.addView(Ui.text(activity, Storefront.BRAND, 12, p.muted, false));
    TextView name = Ui.text(activity, product.name, 15, p.ink, false);
    name.setSingleLine(true);
    name.setEllipsize(TextUtils.TruncateAt.END);
    name.setPadding(0, dp(6), 0, 0);
    info.addView(name);
    TextView price = Ui.text(activity, "", 17, p.ink, true);
    price.setText(priceText(product, product.fromPrice(), product.pricesVary()));
    price.setPadding(0, dp(12), 0, 0);
    info.addView(price);
    card.addView(info);
    card.setOnClickListener(v -> openProduct(product));
    card.setContentDescription(product.name + ", " + Storefront.priceLabel(product.fromPrice())
        + (product.sample ? ", sample design, not for sale" : ""));
    ripple(card);
    return card;
  }

  private FrameLayout artFrame(Storefront.Product product) {
    FrameLayout frame = new FrameLayout(activity);
    frame.addView(new ProductPhoto(activity, product.id, product.name, p), new FrameLayout.LayoutParams(-1, -1));
    return frame;
  }

  private CharSequence priceText(Storefront.Product product, int paise, boolean from) {
    SpannableStringBuilder s = new SpannableStringBuilder();
    if (paise <= 0) {
      s.append("Price coming soon");
      s.setSpan(new ForegroundColorSpan(p.muted), 0, s.length(), 0);
      s.setSpan(new AbsoluteSizeSpan(14, true), 0, s.length(), 0);
      return s;
    }
    if (from) s.append("From ");
    s.append(Storefront.rupees(paise));
    return s;
  }

  // ---- Sheets ----

  private BottomSheetDialog sheet(View content) {
    BottomSheetDialog dialog = new BottomSheetDialog(activity);
    LinearLayout frame = Ui.column(activity);
    GradientDrawable bg = new GradientDrawable();
    bg.setColor(p.surface);
    float r = dp(24);
    bg.setCornerRadii(new float[] {r, r, r, r, 0, 0, 0, 0});
    frame.setBackground(bg);
    View handle = new View(activity);
    handle.setBackground(Ui.round(p.line, dp(2)));
    LayoutParams hl = new LayoutParams(dp(40), dp(4));
    hl.gravity = Gravity.CENTER_HORIZONTAL;
    hl.setMargins(0, dp(10), 0, dp(10));
    frame.addView(handle, hl);
    NestedScrollView scroll = new NestedScrollView(activity);
    scroll.addView(content);
    frame.addView(scroll, new LayoutParams(-1, -2));
    dialog.setContentView(frame);
    dialog.getBehavior().setSkipCollapsed(true);
    dialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
    dialog.setOnShowListener(d -> {
      if (frame.getParent() instanceof View) ((View) frame.getParent()).setBackgroundColor(Color.TRANSPARENT);
    });
    return dialog;
  }

  /** The chevron's full grid of one category. */
  private void openShelf(Storefront.Shelf shelf) {
    LinearLayout box = Ui.column(activity);
    int side = gutter - dp(4);
    box.setPadding(side, 0, side, dp(28));
    LinearLayout head = Ui.column(activity);
    head.setPadding(dp(4), 0, dp(4), dp(12));
    head.addView(Ui.text(activity, Storefront.EYEBROW, 12, p.muted, false));
    TextView title = Ui.text(activity, shelf.title, 24, p.ink, true);
    title.setPadding(0, dp(6), 0, 0);
    head.addView(title);
    box.addView(head);
    int cell = (getResources().getDisplayMetrics().widthPixels - 2 * side) / 2 - dp(8);
    View[] cards = new View[shelf.products.size()];
    for (int i = 0; i < cards.length; i++) cards[i] = card(shelf.products.get(i), cell);
    box.addView(Ui.grid(activity, cards, 2));
    sheet(box).show();
  }

  private void openProduct(Storefront.Product product) {
    reloadBag();
    final Storefront.Variant[] chosen = {product.defaultVariant()};
    LinearLayout box = Ui.column(activity);
    box.setPadding(gutter, 0, gutter, dp(28));
    android.app.Dialog dialog = new android.app.Dialog(activity, android.R.style.Theme_Material_Light_NoActionBar);
    LinearLayout frame=Ui.column(activity);frame.setBackgroundColor(p.bg);
    LinearLayout header=Ui.row(activity);header.setPadding(gutter,dp(12),gutter,dp(12));
    header.addView(Ui.chip(activity,p,"Back",false,dialog::dismiss));
    header.addView(Ui.text(activity,product.name,18,p.ink,true),Ui.weight(1));
    header.addView(Ui.chip(activity,p,"Cart",false,()->{dialog.dismiss();openBag();}));frame.addView(header);
    NestedScrollView scroll=new NestedScrollView(activity);scroll.addView(box);frame.addView(scroll,new LayoutParams(-1,0,1));
    LinearLayout actions=Ui.row(activity);actions.setPadding(gutter,dp(12),gutter,dp(12));actions.setBackgroundColor(p.surface);frame.addView(actions);
    dialog.setContentView(frame);UiInsets.apply(frame);

    FrameLayout artFrame = artFrame(product);
    artFrame.setBackground(Ui.round(p.surface2, dp(18)));
    artFrame.setClipToOutline(true);
    box.addView(artFrame, new LayoutParams(-1, dp(300)));
    TextView brand = Ui.text(activity, Storefront.BRAND, 13, p.muted, false);
    brand.setPadding(0, dp(18), 0, 0);
    box.addView(brand);
    TextView name = Ui.text(activity, product.name, 22, p.ink, true);
    name.setPadding(0, dp(6), 0, 0);
    box.addView(name);
    TextView price = Ui.text(activity, "", 22, p.ink, true);
    price.setPadding(0, dp(12), 0, 0);
    box.addView(price);
    if (!product.description.isEmpty()) {
      TextView about = Ui.text(activity, product.description, 14, p.muted, false);
      about.setPadding(0, dp(10), 0, 0);
      box.addView(about);
    }
    LinearLayout options = Ui.column(activity);
    box.addView(options);
    TextView availability = Ui.text(activity, "", 13, p.muted, true);
    availability.setPadding(0, dp(18), 0, 0);
    box.addView(availability);
    TextView note = Ui.text(activity, "", 13, p.muted, false);
    TextView add = Ui.pill(activity, p, "Add to cart", true, () -> {
      Storefront.Variant v = chosen[0];
      if (Storefront.addBlocker(product, v) != null) return;
      switch (Storefront.add(bag, v.id)) {
        case ADDED:
          saveBag();
          note.setText("Added to your bag · View bag");
          note.setTextColor(p.accent);
          note.setOnClickListener(x -> { dialog.dismiss(); openBag(); });
          break;
        case QUANTITY_LIMIT:
          note.setText("You already have " + Storefront.MAX_QUANTITY + " of this option in your bag.");
          break;
        default:
          note.setText("Your bag holds up to " + Storefront.MAX_LINES + " different options.");
      }
    });
    TextView buy=Ui.pill(activity,p,"Buy now",false,()->{
      Storefront.Variant v=chosen[0];if(Storefront.addBlocker(product,v)!=null)return;
      if(Storefront.add(bag,v.id)==Storefront.Added.ADDED){saveBag();dialog.dismiss();openBag();}
      else note.setText("Your cart has reached its item limit.");
    });
    actions.addView(buy,Ui.weight(1));actions.addView(Ui.space(activity,10,1));actions.addView(add,Ui.weight(1));
    note.setPadding(0, dp(12), 0, 0);
    box.addView(note);

    Runnable[] refresh = new Runnable[1];
    refresh[0] = () -> {
      Storefront.Variant v = chosen[0];
      price.setText(priceText(product, v == null ? 0 : v.pricePaise, false));
      options.removeAllViews();
      String[][] fields = {{"size", "Select size"}};
      for (String[] field : fields) {
        List<String> values = Storefront.options(product.variants, field[0]);
        if (values.isEmpty()) continue;
        TextView label = Ui.text(activity, field[1], 13, p.muted, true);
        label.setPadding(0, dp(18), 0, dp(8));
        options.addView(label);
        HorizontalScrollView strip = new HorizontalScrollView(activity);
        strip.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = Ui.row(activity);
        for (String value : values) {
          boolean on = v != null && value.equals(v.field(field[0]));
          TextView chip = Ui.chip(activity, p, value, on, () -> {
            chosen[0] = Storefront.choose(product.variants, chosen[0], field[0], value);
            refresh[0].run();
          });
          LayoutParams cl = new LayoutParams(-2, -2);
          cl.rightMargin = dp(8);
          chips.addView(chip, cl);
        }
        strip.addView(chips);
        options.addView(strip);
      }
      availability.setText(Storefront.availability(product, v, catalog != null && catalog.salesEnabled));
      availability.setTextColor(v != null && v.available && !product.sample ? p.accent : p.muted);
      String blocker = Storefront.addBlocker(product, v);
      enable(add, blocker == null && v != null && v.available);
      enable(buy, blocker == null && v != null && v.available);
      note.setText(blocker == null ? "" : blocker);
      note.setTextColor(p.muted);
      note.setOnClickListener(null);
    };
    refresh[0].run();
    dialog.show();
    dialog.getWindow().setLayout(-1,-1);
  }

  private void openBag() {
    activity.startActivity(new Intent(activity, StoreCheckoutActivity.class));
  }

  // ---- Footer, links and small helpers ----

  private View footer() {
    LinearLayout box = Ui.column(activity);
    box.setPadding(gutter, dp(32), gutter, dp(8));
    box.addView(Ui.text(activity, "Help & policies", 18, p.ink, true));
    LinearLayout card = Ui.card(activity, p);
    card.setPadding(dp(4), dp(4), dp(4), dp(4));
    card.addView(policy("return", "Return & Exchange Policy", "7-day returns and exchanges", "/returns.html"));
    card.addView(Ui.divider(activity, p));
    card.addView(policy("doc", "Terms & Conditions", null, "/terms.html"));
    card.addView(Ui.divider(activity, p));
    card.addView(policy("shield", "Privacy Policy", null, "/privacy.html"));
    LayoutParams lp = new LayoutParams(-1, -2);
    lp.topMargin = dp(12);
    box.addView(card, lp);
    TextView pay = Ui.text(activity,
        "Payments are processed by Razorpay on the Boundary website. The app never sees your card or UPI details.",
        12, p.muted, false);
    pay.setPadding(dp(2), dp(12), dp(2), 0);
    box.addView(pay);
    return box;
  }

  private View policy(String icon, String title, String subtitle, String path) {
    LinearLayout r = Ui.row(activity);
    r.setPadding(dp(12), dp(14), dp(8), dp(14));
    r.addView(new IconView(activity, icon, p.accent), new LayoutParams(dp(22), dp(22)));
    LinearLayout text = Ui.column(activity);
    text.setPadding(dp(14), 0, dp(8), 0);
    text.addView(Ui.text(activity, title, 15, p.ink, true));
    if (subtitle != null) {
      TextView s = Ui.text(activity, subtitle, 12, p.muted, false);
      s.setPadding(0, dp(4), 0, 0);
      text.addView(s);
    }
    r.addView(text, Ui.weight(1));
    r.addView(new IconView(activity, "chevron-right", p.muted), new LayoutParams(dp(20), dp(20)));
    r.setOnClickListener(v -> browse(PlayerAccount.ORIGIN + path));
    r.setContentDescription(title);
    ripple(r);
    return r;
  }

  private TextView link(String label, String path) {
    TextView t = Ui.text(activity, label, 13, p.accent, true);
    t.setPadding(0, dp(6), 0, dp(6));
    t.setOnClickListener(v -> browse(PlayerAccount.ORIGIN + path));
    return t;
  }

  private void share() {
    Intent send = new Intent(Intent.ACTION_SEND).setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, "Boundary Store")
        .putExtra(Intent.EXTRA_TEXT, "Boundary teamwear: jerseys, full-sleeve jerseys and caps. " + PlayerAccount.ORIGIN + SHOP);
    activity.startActivity(Intent.createChooser(send, "Share the Boundary Store"));
  }

  private void browse(String url) {
    try {
      activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
    } catch (ActivityNotFoundException none) {
      Toast.makeText(activity, "Install a web browser to open " + url, Toast.LENGTH_LONG).show();
    }
  }

  @Override protected void onWindowVisibilityChanged(int visibility) {
    super.onWindowVisibilityChanged(visibility);
    if (visibility == VISIBLE && bag != null && badge != null) reloadBag();
  }

  private void reloadBag() {
    bag.clear();bag.addAll(Storefront.parse(activity.getSharedPreferences(PREFS,0).getString(BAG,"")));refreshBadge();
  }

  private void saveBag() {
    activity.getSharedPreferences(PREFS, 0).edit().putString(BAG, Storefront.encode(bag)).apply();
    refreshBadge();
  }

  private void refreshBadge() {
    int count = Storefront.count(bag);
    badge.setVisibility(count > 0 ? VISIBLE : GONE);
    badge.setText(count > 99 ? "99+" : String.valueOf(count));
    bagButton.setContentDescription(count == 0 ? "Bag, empty" : count == 1 ? "Bag, 1 item" : "Bag, " + count + " items");
  }

  private void enable(TextView button, boolean on) {
    button.setEnabled(on);
    button.setBackground(Ui.round(on ? p.accent : p.surface2, dp(24)));
    button.setTextColor(on ? p.onAccent : p.muted);
  }

  private GradientDrawable ring() {
    GradientDrawable d = Ui.circle(p.surface);
    d.setStroke(Math.max(1, dp(1)), p.line);
    return d;
  }

  private void ripple(View v) {
    TypedValue value = new TypedValue();
    if (activity.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, value, true))
      v.setForeground(activity.getDrawable(value.resourceId));
  }

  private int cardWidth() { return Math.min(dp(300), Math.round(getResources().getDisplayMetrics().widthPixels * 0.64f)); }

  private static int artHeight(int width) { return Math.round(width * 1.3f); }

  private int dp(float v) { return Ui.dp(getContext(), v); }
}
