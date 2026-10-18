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
    Storefront.Catalog known;
    boolean fresh;
    synchronized (StoreView.class) {
      known = cached;
      fresh = known != null && System.currentTimeMillis() - cachedAt < FRESH_MS;
    }
    catalog = known;
    loading = known == null;
    render();
    if (fresh) return;
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
        // A stale catalog stays on screen; with none, the shelves fall back to sample designs.
      }
      Storefront.Catalog done = result;
      main.post(() -> {
        loading = false;
        if (done != null) catalog = done;
        render();
      });
    }, "boundary-store-catalog").start();
  }

  private static JSONObject fetch() throws Exception {
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

  private static Storefront.Catalog parse(JSONObject json) {
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
    else if (catalog == null) status.setText("Couldn't reach the store. Showing sample designs.");
    else status.setText(catalog.salesEnabled ? "Checkout open · pay securely with Razorpay"
        : "Preview · checkout opens once stock is confirmed");
    if (!loading && catalog == null) {
      LinearLayout retry = Ui.row(activity);
      retry.setPadding(gutter, dp(10), gutter, 0);
      retry.addView(Ui.chip(activity, p, "Retry", false, this::load));
      shelves.addView(retry);
    }
    List<Storefront.Product> live = catalog == null ? Collections.emptyList() : catalog.products;
    for (Storefront.Shelf shelf : Storefront.shelves(live)) shelves.addView(shelf(shelf, loading));
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
    if (!placeholder && shelf.hasSamples()) box.addView(sampleNote(gutter));
    return box;
  }

  private View card(Storefront.Product product, int width) {
    LinearLayout card = Ui.column(activity);
    card.setBackground(Ui.outline(p.surface, p.line, Math.max(1, dp(1)), dp(16)));
    card.setClipToOutline(true);
    card.addView(artFrame(product, product.design(), true), new LayoutParams(-1, artHeight(width)));
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

  /** Product drawing with its corner tag; only samples are tagged, since the catalog has no other badges. */
  private FrameLayout artFrame(Storefront.Product product, Storefront.Design design, boolean inCard) {
    FrameLayout frame = new FrameLayout(activity);
    frame.addView(new ProductArt(activity, product.category, design, p.dark), new FrameLayout.LayoutParams(-1, -1));
    if (product.sample) {
      TextView tag = Ui.text(activity, "Sample", 12, p.onAccent, true);
      tag.setPadding(dp(12), dp(6), dp(12), dp(6));
      FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
      if (inCard) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(p.accent);
        float r = dp(12);
        bg.setCornerRadii(new float[] {0, 0, 0, 0, r, r, 0, 0});
        tag.setBackground(bg);
      } else {
        tag.setBackground(Ui.round(p.accent, dp(14)));
        lp.setMargins(dp(12), dp(12), 0, 0);
      }
      frame.addView(tag, lp);
    }
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
    if (product.sample) {
      int start = s.length();
      s.append("  Sample price");
      s.setSpan(new ForegroundColorSpan(p.muted), start, s.length(), 0);
      s.setSpan(new AbsoluteSizeSpan(12, true), start, s.length(), 0);
    }
    return s;
  }

  private TextView sampleNote(int side) {
    TextView note = Ui.text(activity, Storefront.SAMPLE_NOTE, 12, p.muted, false);
    note.setPadding(side, dp(10), side, 0);
    return note;
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
    if (shelf.hasSamples()) head.addView(sampleNote(0));
    box.addView(head);
    int cell = (getResources().getDisplayMetrics().widthPixels - 2 * side) / 2 - dp(8);
    View[] cards = new View[shelf.products.size()];
    for (int i = 0; i < cards.length; i++) cards[i] = card(shelf.products.get(i), cell);
    box.addView(Ui.grid(activity, cards, 2));
    sheet(box).show();
  }

  private void openProduct(Storefront.Product product) {
    final Storefront.Variant[] chosen = {product.defaultVariant()};
    LinearLayout box = Ui.column(activity);
    box.setPadding(gutter, 0, gutter, dp(28));
    BottomSheetDialog dialog = sheet(box);

    FrameLayout artFrame = artFrame(product, product.design(), false);
    ProductArt art = (ProductArt) artFrame.getChildAt(0);
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
    TextView add = Ui.pill(activity, p, "Add to bag", true, () -> {
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
    LayoutParams al = new LayoutParams(-1, -2);
    al.topMargin = dp(14);
    box.addView(add, al);
    note.setPadding(0, dp(12), 0, 0);
    box.addView(note);

    Runnable[] refresh = new Runnable[1];
    refresh[0] = () -> {
      Storefront.Variant v = chosen[0];
      price.setText(priceText(product, v == null ? 0 : v.pricePaise, false));
      art.setDesign(v == null ? product.design() : Storefront.designFor(v.colour));
      options.removeAllViews();
      String[][] fields = {{"material", "Material"}, {"size", "Size"}, {"colour", "Colour"}};
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
      enable(add, blocker == null);
      note.setText(blocker == null ? "" : blocker);
      note.setTextColor(p.muted);
      note.setOnClickListener(null);
    };
    refresh[0].run();
    dialog.show();
  }

  private void openBag() {
    LinearLayout box = Ui.column(activity);
    box.setPadding(gutter, 0, gutter, dp(28));
    BottomSheetDialog dialog = sheet(box);
    Runnable[] draw = new Runnable[1];
    draw[0] = () -> {
      box.removeAllViews();
      LinearLayout head = Ui.row(activity);
      head.addView(Ui.text(activity, "Your bag", 24, p.ink, true), Ui.weight(1));
      int items = Storefront.count(bag);
      head.addView(Ui.text(activity, items == 1 ? "1 item" : items + " items", 14, p.muted, false));
      box.addView(head);
      if (bag.isEmpty()) {
        TextView empty = Ui.text(activity, "Your bag is empty. Pick your kit from the collections.", 15, p.muted, false);
        empty.setPadding(0, dp(18), 0, dp(18));
        box.addView(empty);
        box.addView(Ui.pill(activity, p, "Browse the collection", false, dialog::dismiss), new LayoutParams(-1, -2));
      } else {
        long subtotal = 0;
        boolean allKnown = catalog != null;
        for (Storefront.Line line : new ArrayList<>(bag)) {
          Storefront.Variant v = catalog == null ? null : catalog.variant(line.variantId);
          Storefront.Product product = catalog == null ? null : catalog.productOf(line.variantId);
          if (v == null) allKnown = false;
          else subtotal += (long) v.pricePaise * line.quantity;
          box.addView(bagLine(line, product, v, draw[0]));
          box.addView(Ui.divider(activity, p));
        }
        int shipping = catalog == null ? -1 : catalog.shippingPaise;
        box.addView(sum("Subtotal", allKnown ? Storefront.rupees(subtotal) : "Confirmed on the website", false));
        box.addView(sum("Delivery", shipping < 0 ? "Calculated at checkout" : shipping == 0 ? "Free" : Storefront.rupees(shipping), false));
        if (allKnown && shipping >= 0) box.addView(sum("Total", Storefront.rupees(subtotal + shipping), true));
        List<Storefront.Line> lines = checkoutLines();
        TextView checkout = Ui.pill(activity, p, "Checkout securely with Razorpay", true,
            () -> browse(Storefront.checkoutUrl(PlayerAccount.ORIGIN, lines)));
        enable(checkout, !lines.isEmpty());
        LayoutParams cl = new LayoutParams(-1, -2);
        cl.topMargin = dp(18);
        box.addView(checkout, cl);
        TextView how = Ui.text(activity, catalog != null && catalog.salesEnabled
            ? "You'll sign in on the Boundary website and pay through Razorpay. Prices and stock are checked again before payment."
            : "Checkout opens once stock and delivery are confirmed. Your bag will be waiting on the Boundary website.",
            13, p.muted, false);
        how.setPadding(0, dp(12), 0, 0);
        box.addView(how);
      }
      LinearLayout links = Ui.row(activity);
      links.setPadding(0, dp(18), 0, 0);
      links.addView(link("Returns", "/returns.html"));
      links.addView(Ui.text(activity, "  ·  ", 13, p.muted, false));
      links.addView(link("Terms", "/terms.html"));
      links.addView(Ui.text(activity, "  ·  ", 13, p.muted, false));
      links.addView(link("Privacy", "/privacy.html"));
      box.addView(links);
    };
    draw[0].run();
    dialog.show();
  }

  private View bagLine(Storefront.Line line, Storefront.Product product, Storefront.Variant v, Runnable redraw) {
    LinearLayout row = Ui.row(activity);
    row.setGravity(Gravity.TOP);
    row.setPadding(0, dp(14), 0, dp(14));
    FrameLayout thumb = new FrameLayout(activity);
    thumb.addView(new ProductArt(activity, product == null ? "tshirt" : product.category,
        v == null ? Storefront.DESIGNS[0] : Storefront.designFor(v.colour), p.dark), new FrameLayout.LayoutParams(-1, -1));
    thumb.setBackground(Ui.round(p.surface2, dp(12)));
    thumb.setClipToOutline(true);
    row.addView(thumb, new LayoutParams(dp(64), dp(78)));

    LinearLayout info = Ui.column(activity);
    info.setPadding(dp(12), 0, dp(8), 0);
    String title = product != null ? product.name : catalog == null ? "Loading item details…" : "No longer in the store";
    info.addView(Ui.text(activity, title, 15, p.ink, true));
    String detail = v != null ? v.material + " · " + v.size + " · " + v.colour
        : catalog == null ? "Details load when you're online" : "Remove this item";
    TextView detailText = Ui.text(activity, detail, 12, p.muted, false);
    detailText.setPadding(0, dp(4), 0, 0);
    info.addView(detailText);
    if (v != null && v.pricePaise > 0) {
      TextView price = Ui.text(activity, Storefront.rupees((long) v.pricePaise * line.quantity), 15, p.ink, true);
      price.setPadding(0, dp(6), 0, 0);
      info.addView(price);
    }
    LinearLayout stepper = Ui.row(activity);
    stepper.setPadding(0, dp(10), 0, 0);
    stepper.addView(step("−", "Remove one", line.quantity > 1,
        () -> { Storefront.setQuantity(bag, line.variantId, line.quantity - 1); saveBag(); redraw.run(); }));
    TextView qty = Ui.text(activity, String.valueOf(line.quantity), 15, p.ink, true);
    qty.setGravity(Gravity.CENTER);
    qty.setMinWidth(dp(36));
    stepper.addView(qty);
    stepper.addView(step("+", "Add one", line.quantity < Storefront.MAX_QUANTITY,
        () -> { Storefront.setQuantity(bag, line.variantId, line.quantity + 1); saveBag(); redraw.run(); }));
    info.addView(stepper);
    row.addView(info, Ui.weight(1));

    FrameLayout remove = Ui.iconButton(activity, p, "trash", 36, p.surface2, p.ink,
        () -> { Storefront.setQuantity(bag, line.variantId, 0); saveBag(); redraw.run(); });
    remove.setContentDescription("Remove " + title);
    row.addView(remove);
    return row;
  }

  private TextView step(String label, String description, boolean on, Runnable action) {
    TextView b = Ui.text(activity, label, 18, on ? p.ink : p.line, true);
    b.setGravity(Gravity.CENTER);
    b.setBackground(Ui.outline(p.surface, p.line, Math.max(1, dp(1)), dp(16)));
    b.setLayoutParams(new LayoutParams(dp(32), dp(32)));
    b.setContentDescription(description);
    b.setEnabled(on);
    if (on) b.setOnClickListener(x -> action.run());
    return b;
  }

  private View sum(String label, String value, boolean strong) {
    LinearLayout r = Ui.row(activity);
    r.setPadding(0, dp(strong ? 12 : 10), 0, 0);
    r.addView(Ui.text(activity, label, strong ? 17 : 14, strong ? p.ink : p.muted, strong), Ui.weight(1));
    r.addView(Ui.text(activity, value, strong ? 17 : 14, p.ink, true));
    return r;
  }

  /** Lines the website can check out: everything when offline, otherwise only options still in the catalog. */
  private List<Storefront.Line> checkoutLines() {
    List<Storefront.Line> out = new ArrayList<>();
    for (Storefront.Line line : bag) {
      Storefront.Variant v = catalog == null ? null : catalog.variant(line.variantId);
      if (catalog == null || v != null && v.pricePaise > 0) out.add(line);
    }
    return out;
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
