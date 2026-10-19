package com.example.madfinalproject;

import static org.junit.Assert.*;

import com.example.madfinalproject.Storefront.*;
import java.util.*;
import org.junit.Test;

public class StorefrontTest {
  private static Variant variant(String id, String material, String size, int price, boolean available) {
    return new Variant(id, material, size, "Boundary Green", price, available);
  }

  private static Product product(String id, String category, Variant... variants) {
    return new Product(id, "Boundary " + id, category, "Live kit", Arrays.asList(variants), false);
  }

  @Test public void rupeesUseIndianGroupingAndDropWholePaise() {
    assertEquals("₹629", Storefront.rupees(62900));
    assertEquals("₹1,200", Storefront.rupees(120000));
    assertEquals("₹12,345", Storefront.rupees(1234500));
    assertEquals("₹1,00,000", Storefront.rupees(10000000));
    assertEquals("₹6.05", Storefront.rupees(605));
    assertEquals("₹0", Storefront.rupees(0));
  }

  @Test public void unpricedOptionsSayPriceComingSoon() {
    assertEquals("Price coming soon", Storefront.priceLabel(0));
    assertEquals("₹399", Storefront.priceLabel(39900));
  }

  @Test public void bagRoundTripsThroughTheWebsiteFormat() {
    List<Line> bag = new ArrayList<>();
    Storefront.add(bag, "club-tee-cotton-m");
    Storefront.add(bag, "club-tee-cotton-m");
    Storefront.add(bag, "club-cap-cotton");
    String encoded = Storefront.encode(bag);
    assertEquals("club-tee-cotton-m:2,club-cap-cotton:1", encoded);
    List<Line> parsed = Storefront.parse(encoded);
    assertEquals(2, parsed.size());
    assertEquals("club-tee-cotton-m", parsed.get(0).variantId);
    assertEquals(2, parsed.get(0).quantity);
    assertEquals(3, Storefront.count(parsed));
  }

  @Test public void parseDropsMalformedOutOfRangeAndRepeatedLines() {
    List<Line> bag = Storefront.parse("ok:1,bad,:2,x:0,y:11,z:-1,ok:4,a b:1,q:01,w:3:4,v:10");
    List<String> ids = new ArrayList<>();
    for (Line line : bag) ids.add(line.variantId + ":" + line.quantity);
    assertEquals(Arrays.asList("ok:1", "q:1", "v:10"), ids);
    assertTrue(Storefront.parse(null).isEmpty());
    assertTrue(Storefront.parse("").isEmpty());
  }

  @Test public void parseKeepsAtMostTenLines() {
    StringBuilder s = new StringBuilder();
    for (int i = 0; i < 14; i++) s.append(i == 0 ? "" : ",").append("v").append(i).append(":1");
    assertEquals(Storefront.MAX_LINES, Storefront.parse(s.toString()).size());
  }

  @Test public void addStopsAtTenOfAnOptionAndTenOptions() {
    List<Line> bag = new ArrayList<>();
    for (int i = 0; i < 10; i++) assertEquals(Added.ADDED, Storefront.add(bag, "tee"));
    assertEquals(Added.QUANTITY_LIMIT, Storefront.add(bag, "tee"));
    for (int i = 1; i < 10; i++) Storefront.add(bag, "v" + i);
    assertEquals(Added.BAG_FULL, Storefront.add(bag, "one-more"));
    assertEquals(10, bag.size());
  }

  @Test public void setQuantityClampsAndRemoves() {
    List<Line> bag = new ArrayList<>(Storefront.parse("a:2,b:1"));
    Storefront.setQuantity(bag, "a", 40);
    assertEquals(10, bag.get(0).quantity);
    Storefront.setQuantity(bag, "a", 0);
    assertEquals(1, bag.size());
    assertEquals("b", bag.get(0).variantId);
  }

  @Test public void checkoutUrlEncodesTheBag() {
    String url = Storefront.checkoutUrl("https://example.test", Storefront.parse("club-tee-cotton-m:2,club-cap-cotton:1"));
    assertEquals("https://example.test/shop.html?bag=club-tee-cotton-m%3A2%2Cclub-cap-cotton%3A1", url);
  }

  @Test public void shelvesShowThreeCategoriesInOrderAndHideBottles() {
    List<Product> live = Arrays.asList(
        product("bottle", "bottle", variant("b1", "Steel", "750 ml", 29900, true)),
        product("maroon-cap", "cap", variant("c1", "Cotton twill", "Adjustable", 39900, false)),
        product("blue-jersey", "tshirt", variant("t1", "Cotton", "M", 89900, false)));
    List<Shelf> shelves = Storefront.shelves(live);
    assertEquals(3, shelves.size());
    assertEquals("Jerseys", shelves.get(0).title);
    assertEquals("Full-Sleeve Jerseys", shelves.get(1).title);
    assertEquals("Caps", shelves.get(2).title);
    for (Shelf shelf : shelves) for (Product p : shelf.products) assertNotEquals("bottle", p.category);
    assertEquals("blue-jersey", shelves.get(0).products.get(0).id);
    assertFalse(shelves.get(0).products.get(0).sample);
  }

  @Test public void sparseShelvesContainOnlyPhotographedCatalogProducts() {
    List<Product> live = Arrays.asList(
        product("blue-jersey", "tshirt", variant("blue-m", "Standard", "M", 89900, true)),
        product("old-generic", "cap", variant("old-cap", "Cotton", "Adjustable", 39900, true)));
    List<Shelf> shelves = Storefront.shelves(live);
    assertEquals(1, shelves.get(0).products.size());
    assertFalse(shelves.get(0).hasSamples());
    assertTrue(shelves.get(1).products.isEmpty());
    assertTrue(shelves.get(2).products.isEmpty());
    for (Shelf shelf : Storefront.shelves(Collections.emptyList())) assertTrue(shelf.products.isEmpty());
  }

  @Test public void onlyTheFourOriginalProductPhotosAreAllowed() {
    for (String id : Arrays.asList("black-full-sleeve", "blue-jersey", "white-jersey", "maroon-cap"))
      assertEquals("/assets/products/" + id + ".png", Storefront.photoPath(id));
    assertNull(Storefront.photoPath("club-tee"));
    assertNull(Storefront.photoPath("../secret"));
    assertNull(Storefront.photoPath(null));
  }

  @Test public void pricedLiveOptionsCanBeSavedButUnpricedCannot() {
    Variant priced = variant("t-m", "Cotton", "M", 89900, false), unpriced = variant("t-l", "Cotton", "L", 0, false);
    Product tee = product("blue-jersey", "tshirt", priced, unpriced);
    assertNull(Storefront.addBlocker(tee, priced));
    assertNotNull(Storefront.addBlocker(tee, unpriced));
    assertEquals("In stock", Storefront.availability(tee, variant("x", "Cotton", "S", 89900, true), true));
    assertTrue(Storefront.availability(tee, priced, false).startsWith("Stock confirmation pending"));
  }

  @Test public void chooseKeepsOtherSelectionsWhenItCan() {
    Variant cottonM = variant("cm", "Cotton", "M", 1, true), cottonL = variant("cl", "Cotton", "L", 1, true),
        polyM = variant("pm", "Polyester", "M", 1, true), polyS = variant("ps", "Polyester", "S", 1, true);
    List<Variant> all = Arrays.asList(cottonM, cottonL, polyS, polyM);
    assertSame(polyM, Storefront.choose(all, cottonM, "material", "Polyester"));
    assertSame(cottonL, Storefront.choose(all, cottonM, "size", "L"));
    assertSame(cottonM, Storefront.choose(all, cottonM, "size", "XXL"));
    assertEquals(Arrays.asList("Cotton", "Polyester"), Storefront.options(all, "material"));
    assertEquals(Arrays.asList("M", "L", "S"), Storefront.options(all, "size"));
  }

  @Test public void defaultVariantPrefersBuyableThenPriced() {
    Variant unpriced = variant("u", "Cotton", "S", 0, false), priced = variant("p", "Cotton", "M", 100, false),
        buyable = variant("b", "Cotton", "L", 100, true);
    assertSame(buyable, product("x", "tshirt", unpriced, priced, buyable).defaultVariant());
    assertSame(priced, product("y", "tshirt", unpriced, priced).defaultVariant());
  }

  @Test public void fromPriceReportsTheLowestPricedOption() {
    Product p = product("x", "tshirt", variant("a", "Cotton", "S", 0, false), variant("b", "Cotton", "M", 99900, false),
        variant("c", "Cotton", "L", 89900, false));
    assertEquals(89900, p.fromPrice());
    assertTrue(p.pricesVary());
    assertEquals(0, product("z", "cap", variant("d", "Twill", "Adjustable", 0, false)).fromPrice());
  }

  @Test public void catalogFindsVariantsAcrossProducts() {
    Variant m = variant("t-m", "Cotton", "M", 89900, false);
    Catalog catalog = new Catalog(Collections.singletonList(product("blue-jersey", "tshirt", m)), false, -1);
    assertSame(m, catalog.variant("t-m"));
    assertEquals("blue-jersey", catalog.productOf("t-m").id);
    assertNull(catalog.variant("missing"));
  }
}
