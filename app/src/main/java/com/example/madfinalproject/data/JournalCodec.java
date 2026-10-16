package com.example.madfinalproject.data;

import android.util.Base64;
import com.example.madfinalproject.domain.Cricket;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.zip.*;
import javax.crypto.*;
import javax.crypto.spec.*;

/** Complete private journal encrypted before it leaves the scoring phone. */
public final class JournalCodec {
  private JournalCodec() {}

  private static SecretKeySpec key(String authority) throws Exception {
    return new SecretKeySpec(
        MessageDigest.getInstance("SHA-256")
            .digest(("Boundary private journal v1:" + authority).getBytes(StandardCharsets.UTF_8)),
        "AES");
  }

  public static String encode(Cricket.Match match, String authority) {
    try {
      ByteArrayOutputStream compressed = new ByteArrayOutputStream();
      try (ObjectOutputStream out = new ObjectOutputStream(new GZIPOutputStream(compressed))) {
        out.writeObject(match);
      }
      byte[] iv = new byte[12];
      new SecureRandom().nextBytes(iv);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, key(authority), new GCMParameterSpec(128, iv));
      byte[] encrypted = cipher.doFinal(compressed.toByteArray());
      ByteArrayOutputStream payload = new ByteArrayOutputStream();
      payload.write(iv);
      payload.write(encrypted);
      return "bnd1:" + Base64.encodeToString(payload.toByteArray(), Base64.NO_WRAP);
    } catch (Exception e) {
      throw new IllegalStateException("Could not protect private match journal", e);
    }
  }

  public static Cricket.Match decode(String encoded, String authority) {
    try {
      if (!encoded.startsWith("bnd1:"))
        throw new IOException("Unsupported journal encryption version");
      byte[] payload = Base64.decode(encoded.substring(5), Base64.NO_WRAP);
      if (payload.length < 28 || payload.length > 1_000_000)
        throw new IOException("Invalid journal size");
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE, key(authority), new GCMParameterSpec(128, payload, 0, 12));
      byte[] clear = cipher.doFinal(payload, 12, payload.length - 12);
      try (ObjectInputStream in =
          new ObjectInputStream(new GZIPInputStream(new ByteArrayInputStream(clear))) {
            @Override
            protected Class<?> resolveClass(ObjectStreamClass desc)
                throws IOException, ClassNotFoundException {
              String n = desc.getName();
              if (!n.startsWith("com.example.madfinalproject.domain.Cricket$")
                  && !n.startsWith("java.lang.")
                  && !n.startsWith("java.util.")
                  && !n.startsWith("["))
                throw new InvalidClassException("Unsupported journal class", n);
              return super.resolveClass(desc);
            }
          }) {
        return (Cricket.Match) in.readObject();
      }
    } catch (Exception e) {
      throw new IllegalStateException("Could not recover the encrypted match journal", e);
    }
  }
}
