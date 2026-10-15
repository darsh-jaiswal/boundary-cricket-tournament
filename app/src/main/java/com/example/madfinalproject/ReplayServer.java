package com.example.madfinalproject;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Temporary paired replay page served only while stump-cam screen is open. */
public final class ReplayServer implements AutoCloseable {
  private ServerSocket server;
  private volatile File clip;
  private final String token = UUID.randomUUID().toString();
  private final Runnable requestReview;

  public ReplayServer(Runnable review) throws IOException {
    requestReview = review;
    server = new ServerSocket(0);
    new Thread(
            () -> {
              while (!server.isClosed()) {
                try {
                  Socket s = server.accept();
                  new Thread(() -> serve(s), "replay-viewer").start();
                } catch (IOException ignored) {
                }
              }
            },
            "replay-link")
        .start();
  }

  public String address() {
    try {
      for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces()))
        for (InetAddress a : Collections.list(ni.getInetAddresses()))
          if (a instanceof Inet4Address
              && !a.isLoopbackAddress()
              && ni.getName().startsWith("wlan"))
            return "http://" + a.getHostAddress() + ":" + server.getLocalPort() + "/" + token + "/";
    } catch (Exception ignored) {
    }
    return "Connect both phones to the same Wi-Fi to share replays.";
  }

  public void setClip(File f) {
    clip = f;
  }

  private void serve(Socket socket) {
    try (Socket s = socket) {
      s.setSoTimeout(10000);
      BufferedReader reader =
          new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.US_ASCII));
      String first = reader.readLine();
      if (first == null || first.length() > 2048) return;
      String[] parts = first.split(" ");
      if (parts.length < 2 || !parts[0].equals("GET") || !parts[1].startsWith("/" + token + "/")) {
        s.getOutputStream()
            .write(
                "HTTP/1.1 403 Forbidden\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                    .getBytes(StandardCharsets.US_ASCII));
        return;
      }
      String range = null;
      for (int i = 0; i < 40; i++) {
        String header = reader.readLine();
        if (header == null || header.isEmpty()) break;
        if (header.toLowerCase(Locale.ROOT).startsWith("range:"))
          range = header.substring(6).trim();
      }
      if (parts[1].contains("check")) {
        requestReview.run();
        respond(s, "text/plain", "Replay requested. Wait a moment, then reload the review.");
        return;
      }
      if (parts[1].contains("clip.mp4")) {
        File f = clip;
        if (f == null) {
          respond(s, "text/plain", "No review captured yet.");
          return;
        }
        long begin = 0, end = f.length() - 1;
        if (range != null && range.matches("bytes=\\d+-\\d*")) {
          String[] r = range.substring(6).split("-", -1);
          begin = Long.parseLong(r[0]);
          if (!r[1].isEmpty()) end = Math.min(end, Long.parseLong(r[1]));
        }
        if (begin > end || begin >= f.length()) return;
        OutputStream out = s.getOutputStream();
        String header =
            "HTTP/1.1 "
                + (range == null ? "200 OK" : "206 Partial Content")
                + "\r\n"
                + "Content-Type: video/mp4\r\n"
                + "Accept-Ranges: bytes\r\n"
                + "Cache-Control: no-store\r\n"
                + "Content-Length: "
                + (end - begin + 1)
                + "\r\n"
                + (range == null
                    ? ""
                    : "Content-Range: bytes " + begin + "-" + end + "/" + f.length() + "\r\n")
                + "Connection: close\r\n\r\n";
        out.write(header.getBytes(StandardCharsets.US_ASCII));
        try (RandomAccessFile file = new RandomAccessFile(f, "r")) {
          file.seek(begin);
          byte[] data = new byte[16384];
          long left = end - begin + 1;
          while (left > 0) {
            int n = file.read(data, 0, (int) Math.min(data.length, left));
            if (n < 0) break;
            out.write(data, 0, n);
            left -= n;
          }
        }
        return;
      }
      respond(
          s,
          "text/html",
          "<!doctype html><meta name='viewport' content='width=device-width,initial-scale=1'><body"
              + " style='font:18px"
              + " system-ui;background:#142a24;color:white;padding:20px'><h1>Boundary"
              + " replay</h1><p>Both phones must stay on the same Wi-Fi. The camera screen must"
              + " remain open.</p><video id='v' controls style='width:100%' src='clip.mp4?t="
              + System.currentTimeMillis()
              + "'></video><p><button onclick='v.playbackRate=.25;v.play()'>0.25x</button> <button"
              + " onclick='v.pause();v.currentTime+=1/30'>Next frame</button> <button"
              + " onclick='v.pause();v.currentTime-=1/30'>Previous frame</button></p><p><a"
              + " style='color:#d0ed8c' href='check'>Request run-out / no-ball check</a></p><button"
              + " onclick='location.reload()'>Reload latest replay</button><p>Official makes the"
              + " decision.</p>");
    } catch (Exception ignored) {
    }
  }

  private void respond(Socket s, String type, String text) throws IOException {
    byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
    OutputStream out = s.getOutputStream();
    out.write(
        ("HTTP/1.1 200 OK\r\nContent-Type: "
                + type
                + "; charset=utf-8\r\nCache-Control: no-store\r\nContent-Length: "
                + bytes.length
                + "\r\nConnection: close\r\n\r\n")
            .getBytes(StandardCharsets.US_ASCII));
    out.write(bytes);
  }

  @Override
  public void close() {
    try {
      server.close();
    } catch (IOException ignored) {
    }
  }
}
