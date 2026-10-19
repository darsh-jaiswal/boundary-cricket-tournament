package com.example.madfinalproject;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Store rules with no Android types: shelves, product photos, rupee prices and the app-to-website bag format. */
public final class Storefront {
  private Storefront() {}

  public static final int MAX_LINES = 10, MAX_QUANTITY = 10;
  public static final String BRAND = "Boundary", EYEBROW = "Boundary Teamwear";
  /** Category key and shelf title, in display order. Bottles are not part of the storefront. */
  public static final String[][] SHELVES = {{"tshirt", "Jerseys"}, {"full-sleeve", "Full-Sleeve Jerseys"}, {"cap", "Caps"}};
  private static final Pattern VARIANT_ID = Pattern.compile("[A-Za-z0-9._-]{1,100}");

  public static final class Variant {
    public final String id, material, size, colour;
    public final int pricePaise;
    public final boolean available;

    public Variant(String id, String material, String size, String colour, int pricePaise, boolean available) {
      this.id = id;
      this.material = material;
      this.size = size;
      this.colour = colour;
      this.pricePaise = pricePaise;
      this.available = available;
    }

    String field(String name) {
      switch (name) {
        case "material": return material;
        case "size": return size;
        default: return colour;
      }
    }
  }

  public static final class Product {
    public final String id, name, category, description;
    public final List<Variant> variants;
    public final boolean sample;

    public Product(String id, String name, String category, String description, List<Variant> variants, boolean sample) {
      this.id = id;
      this.name = name;
      this.category = category;
      this.description = description;
      this.variants = Collections.unmodifiableList(new ArrayList<>(variants));
      this.sample = sample;
    }

    /** Lowest positive price, or 0 when no option is priced yet. */
    public int fromPrice() {
      int low = 0;
      for (Variant v : variants) if (v.pricePaise > 0 && (low == 0 || v.pricePaise < low)) low = v.pricePaise;
      return low;
    }

    public boolean pricesVary() {
      int low = fromPrice();
      for (Variant v : variants) if (v.pricePaise > 0 && v.pricePaise != low) return true;
      return false;
    }

    /** First option that can be bought now, else the first priced one, else the first. */
    public Variant defaultVariant() {
      for (Variant v : variants) if (v.available && v.pricePaise > 0) return v;
      for (Variant v : variants) if (v.pricePaise > 0) return v;
      return variants.isEmpty() ? null : variants.get(0);
    }
  }

  /** The public GET /api/shop response, validated at the boundary. shippingPaise is -1 when not configured. */
  public static final class Catalog {
    public final List<Product> products;
    public final boolean salesEnabled;
    public final int shippingPaise;

    public Catalog(List<Product> products, boolean salesEnabled, int shippingPaise) {
      this.products = Collections.unmodifiableList(new ArrayList<>(products));
      this.salesEnabled = salesEnabled;
      this.shippingPaise = shippingPaise;
    }

    public Product productOf(String variantId) {
      for (Product p : products) for (Variant v : p.variants) if (v.id.equals(variantId)) return p;
      return null;
    }

    public Variant variant(String variantId) {
      Product p = productOf(variantId);
      if (p != null) for (Variant v : p.variants) if (v.id.equals(variantId)) return v;
      return null;
    }
  }

  public static final class Shelf {
    public final String category, title;
    public final List<Product> products;

    Shelf(String category, String title, List<Product> products) {
      this.category = category;
      this.title = title;
      this.products = Collections.unmodifiableList(products);
    }

    public boolean hasSamples() {
      for (Product p : products) if (p.sample) return true;
      return false;
    }
  }

  /** Show catalog products only; never invent listings when a shelf is empty. */
  public static List<Shelf> shelves(List<Product> live) {
    List<Shelf> out = new ArrayList<>();
    for (String[] shelf : SHELVES) {
      List<Product> items = new ArrayList<>();
      for (Product p : live) if (!p.sample && photoPath(p.id) != null && p.category.equals(shelf[0]) && !p.variants.isEmpty()) items.add(p);
      out.add(new Shelf(shelf[0], shelf[1], items));
    }
    return out;
  }

  /** Allowlisted original product photos, hosted by the same Boundary deployment. */
  public static String photoPath(String id) {
    if (id == null) return null;
    switch (id) {
      case "black-full-sleeve": case "blue-jersey": case "white-jersey": case "maroon-cap":
        return "/assets/products/" + id + ".png";
      default: return null;
    }
  }

  /** Why this option cannot go in the bag, or null when it can. Priced live options may be saved before stock is confirmed, as on the website. */
  public static String addBlocker(Product product, Variant variant) {
    if (product.sample) return "Sample design — not for sale yet. Real stock appears when the shop opens.";
    if (variant == null) return "Choose an option first.";
    if (variant.pricePaise <= 0) return "Price coming soon — this option can't be added to your bag yet.";
    return null;
  }

  public static String availability(Product product, Variant variant, boolean salesEnabled) {
    if (product.sample) return "Preview only";
    if (variant == null) return "";
    if (variant.available) return salesEnabled ? "In stock" : "In stock · checkout opens soon";
    return "Stock confirmation pending · saving it to your bag doesn't reserve it";
  }

  /** Distinct values of "material", "size" or "colour", in catalog order. */
  public static List<String> options(List<Variant> variants, String field) {
    List<String> out = new ArrayList<>();
    for (Variant v : variants) {
      String value = v.field(field);
      if (value != null && !value.isEmpty() && !out.contains(value)) out.add(value);
    }
    return out;
  }

  /** The option with field set to value that keeps as many of the current choices as possible. */
  public static Variant choose(List<Variant> variants, Variant current, String field, String value) {
    Variant best = null;
    int bestScore = -1;
    for (Variant v : variants) {
      if (!value.equals(v.field(field))) continue;
      int score = 0;
      if (current != null) for (String f : new String[] {"material", "size", "colour"})
        if (v.field(f).equals(current.field(f))) score++;
      if (score > bestScore) {
        best = v;
        bestScore = score;
      }
    }
    return best == null ? current : best;
  }

  public static String rupees(long paise) {
    long whole = Math.abs(paise) / 100, fraction = Math.abs(paise) % 100;
    String digits = Long.toString(whole);
    StringBuilder grouped = new StringBuilder();
    int n = digits.length();
    if (n <= 3) grouped.append(digits);
    else {
      // Indian grouping: the last three digits, then pairs (1,00,000).
      String head = digits.substring(0, n - 3);
      int first = head.length() % 2;
      if (first > 0) grouped.append(head, 0, first).append(',');
      for (int i = first; i < head.length(); i += 2) grouped.append(head, i, i + 2).append(',');
      grouped.append(digits.substring(n - 3));
    }
    return (paise < 0 ? "-" : "") + "₹" + grouped + (fraction == 0 ? "" : String.format(Locale.ROOT, ".%02d", fraction));
  }

  public static String priceLabel(int paise) { return paise > 0 ? rupees(paise) : "Price coming soon"; }

  public static boolean validVariantId(String id) { return id != null && VARIANT_ID.matcher(id).matches(); }

  public static final class Line {
    public final String variantId;
    public final int quantity;

    public Line(String variantId, int quantity) {
      this.variantId = variantId;
      this.quantity = quantity;
    }
  }

  public enum Added { ADDED, QUANTITY_LIMIT, BAG_FULL }

  public static Added add(List<Line> bag, String variantId) {
    for (int i = 0; i < bag.size(); i++) {
      Line line = bag.get(i);
      if (!line.variantId.equals(variantId)) continue;
      if (line.quantity >= MAX_QUANTITY) return Added.QUANTITY_LIMIT;
      bag.set(i, new Line(variantId, line.quantity + 1));
      return Added.ADDED;
    }
    if (bag.size() >= MAX_LINES) return Added.BAG_FULL;
    bag.add(new Line(variantId, 1));
    return Added.ADDED;
  }

  /** Sets a line's quantity, clamped to MAX_QUANTITY; zero or less removes it. */
  public static void setQuantity(List<Line> bag, String variantId, int quantity) {
    for (int i = 0; i < bag.size(); i++) {
      if (!bag.get(i).variantId.equals(variantId)) continue;
      if (quantity <= 0) bag.remove(i);
      else bag.set(i, new Line(variantId, Math.min(MAX_QUANTITY, quantity)));
      return;
    }
  }

  public static int count(List<Line> bag) {
    int n = 0;
    for (Line line : bag) n += line.quantity;
    return n;
  }

  /** "variantId:qty,variantId:qty", the format the website's ?bag= parameter accepts. */
  public static String encode(List<Line> bag) {
    StringBuilder s = new StringBuilder();
    for (Line line : bag) {
      if (s.length() > 0) s.append(',');
      s.append(line.variantId).append(':').append(line.quantity);
    }
    return s.toString();
  }

  /** Reads an encoded bag, dropping malformed or repeated lines and anything past MAX_LINES. */
  public static List<Line> parse(String text) {
    List<Line> bag = new ArrayList<>();
    if (text == null || text.isEmpty()) return bag;
    for (String entry : text.split(",")) {
      if (bag.size() >= MAX_LINES) break;
      String[] parts = entry.trim().split(":", -1);
      if (parts.length != 2 || !validVariantId(parts[0]) || !parts[1].matches("\\d{1,2}")) continue;
      int quantity = Integer.parseInt(parts[1]);
      if (quantity < 1 || quantity > MAX_QUANTITY) continue;
      boolean repeated = false;
      for (Line line : bag) repeated |= line.variantId.equals(parts[0]);
      if (!repeated) bag.add(new Line(parts[0], quantity));
    }
    return bag;
  }

  public static String checkoutUrl(String origin, List<Line> bag) {
    try {
      return origin + "/shop.html?bag=" + URLEncoder.encode(encode(bag), "UTF-8");
    } catch (UnsupportedEncodingException impossible) {
      throw new IllegalStateException(impossible);
    }
  }
}
