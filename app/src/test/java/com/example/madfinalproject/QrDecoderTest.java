package com.example.madfinalproject;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import java.util.Arrays;
import org.junit.Test;

public class QrDecoderTest {
  private static final int SIDE = 240;
  private static final String ID = "user_2pQx9ZkLmN0aB";

  private static BitMatrix code(String text) throws Exception {
    return new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, SIDE, SIDE);
  }

  /** A camera Y plane: rows padded to rowStride with noise, the last row unpadded as on real devices. */
  private static byte[] yPlane(BitMatrix m, int rowStride, boolean inverted) {
    byte[] y = new byte[rowStride * (SIDE - 1) + SIDE];
    Arrays.fill(y, (byte) 0x5a);
    for (int row = 0; row < SIDE; row++)
      for (int x = 0; x < SIDE; x++) y[row * rowStride + x] = (byte) (m.get(x, row) != inverted ? 0x10 : 0xf0);
    return y;
  }

  @Test public void readsAProfileCodeFromAPaddedCameraFrame() throws Exception {
    String text = QrScanActivity.Decoder.luminance(yPlane(code(Social.qrPayload(ID)), SIDE + 64, false), SIDE + 64, SIDE, SIDE);
    assertEquals(ID, Social.parseQr(text));
  }

  @Test public void readsALightOnDarkCode() throws Exception {
    String text = QrScanActivity.Decoder.luminance(yPlane(code(Social.qrPayload(ID)), SIDE, true), SIDE, SIDE, SIDE);
    assertEquals(ID, Social.parseQr(text));
  }

  @Test public void readsAGalleryImage() throws Exception {
    BitMatrix m = code(Social.qrPayload(ID));
    int[] argb = new int[SIDE * SIDE];
    for (int row = 0; row < SIDE; row++)
      for (int x = 0; x < SIDE; x++) argb[row * SIDE + x] = m.get(x, row) ? 0xff17251d : 0xfff3f6ef;
    assertEquals(ID, Social.parseQr(QrScanActivity.Decoder.pixels(argb, SIDE, SIDE)));
  }

  @Test public void otherCodesDecodeButAreNotProfiles() throws Exception {
    String text = QrScanActivity.Decoder.luminance(yPlane(code("https://example.com/menu"), SIDE, false), SIDE, SIDE, SIDE);
    assertEquals("https://example.com/menu", text);
    assertNull(Social.parseQr(text));
  }

  @Test public void blankFrameHasNoCode() {
    byte[] y = new byte[SIDE * SIDE];
    Arrays.fill(y, (byte) 0x80);
    assertNull(QrScanActivity.Decoder.luminance(y, SIDE, SIDE, SIDE));
  }
}
