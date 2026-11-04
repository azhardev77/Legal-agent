package com.tenderops.visual;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses SAM 3.1 structured output per AI_INTEGRATION.md §3.2.
 *
 * <p>PROVISIONAL GRAMMAR (UNVERIFIED — no live probe has run; corrected by the M3 probe when a
 * key exists): records split on top-level commas; {@code box[x1,y1,x2,y2]} or
 * {@code box[x1,y1,x2,y2,c=conf]} in source-image pixels (top-left origin); optional
 * {@code mask[h=H,w=W,enc=ENC,data=DATA]} attaching to the immediately preceding box
 * (orphan masks are ignored with a logged note). Boxes are clamped to the image.
 */
public final class SamOutputParser {

  public record SamBox(double x0, double y0, double x1, double y1, double confidence,
      String maskRef) {}

  private SamOutputParser() {}

  public static List<SamBox> parse(String output, int imageWidth, int imageHeight) {
    if (output == null || output.isBlank()) {
      throw new SamFormatException("Empty SAM output");
    }
    List<SamBox> boxes = new ArrayList<>();
    List<String> records = splitTopLevel(output);
    int pendingBox = -1;
    for (String record : records) {
      String trimmed = record.trim();
      if (trimmed.startsWith("box[")) {
        boxes.add(parseBox(trimmed, imageWidth, imageHeight));
        pendingBox = boxes.size() - 1;
      } else if (trimmed.startsWith("mask[")) {
        parseMask(trimmed);
        if (pendingBox >= 0 && boxes.get(pendingBox).maskRef() == null) {
          SamBox prev = boxes.get(pendingBox);
          boxes.set(pendingBox, new SamBox(prev.x0(), prev.y0(), prev.x1(), prev.y1(),
              prev.confidence(), trimmed));
        }
      } else {
        throw new SamFormatException("Unknown SAM record: " + abbreviate(trimmed));
      }
    }
    if (boxes.isEmpty()) {
      throw new SamFormatException("SAM output contains no box records");
    }
    return boxes;
  }

  private static List<String> splitTopLevel(String output) {
    List<String> parts = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    int depth = 0;
    boolean inQuotes = false;
    for (int i = 0; i < output.length(); i++) {
      char c = output.charAt(i);
      if (c == '"' && (i == 0 || output.charAt(i - 1) != '\\')) {
        inQuotes = !inQuotes;
        current.append(c);
      } else if (!inQuotes && c == '[') {
        depth++;
        current.append(c);
      } else if (!inQuotes && c == ']') {
        depth--;
        if (depth < 0) {
          throw new SamFormatException("Unbalanced brackets in SAM output");
        }
        current.append(c);
      } else if (!inQuotes && depth == 0 && c == ',') {
        parts.add(current.toString());
        current.setLength(0);
      } else {
        current.append(c);
      }
    }
    if (depth != 0 || inQuotes) {
      throw new SamFormatException("Unbalanced brackets/quotes in SAM output");
    }
    parts.add(current.toString());
    return parts;
  }

  private static SamBox parseBox(String record, int imageWidth, int imageHeight) {
    String inner = innerOf(record, "box");
    String[] fields = inner.split(",");
    if (fields.length < 4 || fields.length > 5) {
      throw new SamFormatException("box record needs 4 pixels + optional c=: " + record);
    }
    double x1 = pixel(fields[0]);
    double y1 = pixel(fields[1]);
    double x2 = pixel(fields[2]);
    double y2 = pixel(fields[3]);
    if (!(x2 > x1) || !(y2 > y1)) {
      throw new SamFormatException("Degenerate box: " + record);
    }
    double confidence = 0.90;
    if (fields.length == 5) {
      String conf = fields[4].trim();
      if (!conf.startsWith("c=")) {
        throw new SamFormatException("Bad 5th box field (want c=): " + record);
      }
      confidence = number(conf.substring(2), "confidence");
      if (confidence < 0.0 || confidence > 1.0) {
        throw new SamFormatException("Confidence out of [0,1]: " + record);
      }
    }
    return new SamBox(clamp(x1 / imageWidth), clamp(y1 / imageHeight),
        clamp(x2 / imageWidth), clamp(y2 / imageHeight), confidence, null);
  }

  private static void parseMask(String record) {
    String inner = innerOf(record, "mask");
    String height = null;
    String width = null;
    String enc = null;
    String data = null;
    for (String field : inner.split(",")) {
      String[] kv = field.split("=", 2);
      if (kv.length != 2 || kv[0].isBlank() || kv[1].isBlank()) {
        throw new SamFormatException("Bad mask field: " + record);
      }
      switch (kv[0].trim()) {
        case "h" -> height = kv[1].trim();
        case "w" -> width = kv[1].trim();
        case "enc" -> enc = kv[1].trim();
        case "data" -> data = kv[1].trim();
        default -> throw new SamFormatException("Unknown mask key: " + record);
      }
    }
    if (height == null || width == null || enc == null || data == null) {
      throw new SamFormatException("Mask needs h/w/enc/data: " + record);
    }
    number(height, "mask h");
    number(width, "mask w");
  }

  private static String innerOf(String record, String kind) {
    if (!record.endsWith("]")) {
      throw new SamFormatException("Unterminated " + kind + " record: " + record);
    }
    return record.substring(kind.length() + 1, record.length() - 1);
  }

  private static double pixel(String field) {
    return number(field.trim(), "pixel");
  }

  private static double number(String field, String what) {
    try {
      return Double.parseDouble(field.trim());
    } catch (NumberFormatException e) {
      throw new SamFormatException("Bad " + what + " number: " + field);
    }
  }

  private static double clamp(double v) {
    return Math.min(1.0, Math.max(0.0, v));
  }

  private static String abbreviate(String record) {
    return record.length() <= 80 ? record : record.substring(0, 80) + "...";
  }
}
