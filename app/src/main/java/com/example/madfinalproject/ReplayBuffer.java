package com.example.madfinalproject;

import android.media.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;

/** Joins recent camera segments into one decodable replay, keeping the latest five seconds. */
public final class ReplayBuffer {
  private ReplayBuffer() {}

  public static File export(List<File> segments, File destination) throws IOException {
    ArrayList<Long> durations = new ArrayList<>();
    long total = 0;
    for (File f : segments) {
      MediaMetadataRetriever metadata = new MediaMetadataRetriever();
      try {
        metadata.setDataSource(f.getAbsolutePath());
        long duration =
            Long.parseLong(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION))
                * 1000;
        durations.add(duration);
        total += duration;
      } finally {
        metadata.release();
      }
    }
    long wanted = Math.max(0, total - 5_000_000L), elapsed = 0, base = 0;
    MediaMuxer muxer =
        new MediaMuxer(destination.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
    boolean started = false;
    int outputTrack = -1;
    try {
      for (int index = 0; index < segments.size(); index++) {
        long duration = durations.get(index);
        if (elapsed + duration <= wanted) {
          elapsed += duration;
          continue;
        }
        MediaExtractor extractor = new MediaExtractor();
        try {
          extractor.setDataSource(segments.get(index).getAbsolutePath());
          int track = -1;
          for (int i = 0; i < extractor.getTrackCount(); i++)
            if (extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME).startsWith("video/")) {
              track = i;
              break;
            }
          if (track < 0) continue;
          MediaFormat format = extractor.getTrackFormat(track);
          if (!started) {
            outputTrack = muxer.addTrack(format);
            muxer.start();
            started = true;
          }
          extractor.selectTrack(track);
          extractor.seekTo(Math.max(0, wanted - elapsed), MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
          long first = extractor.getSampleTime(), last = base;
          ByteBuffer bytes = ByteBuffer.allocate(2 * 1024 * 1024);
          MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
          while (true) {
            bytes.clear();
            int size = extractor.readSampleData(bytes, 0);
            if (size < 0) break;
            info.offset = 0;
            info.size = size;
            info.flags = extractor.getSampleFlags();
            info.presentationTimeUs = base + extractor.getSampleTime() - first;
            muxer.writeSampleData(outputTrack, bytes, info);
            last = info.presentationTimeUs;
            if (!extractor.advance()) break;
          }
          base = last + 33_333;
        } finally {
          extractor.release();
        }
        elapsed += duration;
      }
    } finally {
      if (started) muxer.stop();
      muxer.release();
    }
    if (!started) throw new IOException("No video samples available yet");
    return destination;
  }
}
