package com.tenderops.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenderops.common.FileSafetyValidator;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Component;

/**
 * Generates the demo tender PDFs with PDFBox (MASTER_PROMPT section 5).
 * R14 outcome: no open-license rupee font is obtainable offline, so all amounts use "Rs." text
 * with standard Helvetica. Output bytes are deterministic (fixed metadata + fixed trailer IDs).
 */
@Component
public class TenderPdfGenerator {

  public static final String V1_FILE = "Smart_City_RFP_v1.pdf";
  public static final String CORR_FILE = "Corrigendum_1.pdf";
  public static final String VARIANT_B_FILE = "Variant_B_Tender.pdf";
  public static final String TRUTH_FILE = "ground-truth.json";
  public static final float MARGIN = 56.7f;

  private static final TimeZone IST = TimeZone.getTimeZone("Asia/Kolkata");
  private static final PDFont REGULAR = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
  private static final PDFont BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

  private final ObjectMapper objectMapper;

  public TenderPdfGenerator(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /** Generates the three sample PDFs plus ground-truth JSON into {@code dir}. */
  public void generateSamples(Path dir) throws IOException {
    Files.createDirectories(dir);
    Map<String, GroundTruth.DocTruth> truth = new LinkedHashMap<>();
    truth.put(V1_FILE, writeV1(dir.resolve(V1_FILE)));
    truth.put(CORR_FILE, writeCorrigendum(dir.resolve(CORR_FILE)));
    truth.put(VARIANT_B_FILE, writeVariant(dir.resolve(VARIANT_B_FILE), VariantSpec.variantB()));
    String json = objectMapper.writerWithDefaultPrettyPrinter()
        .writeValueAsString(new GroundTruth(new ArrayList<>(truth.values())));
    Files.writeString(dir.resolve(TRUTH_FILE), json, StandardCharsets.UTF_8);
  }

  /** Generates a small "DEMO DOCUMENT" supporting PDF. */
  public byte[] supportDoc(String title, List<String> lines) throws IOException {
    try (PDDocument doc = new PDDocument()) {
      PDPage page = newPage(doc);
      float width = page.getMediaBox().getWidth();
      try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
        float y = page.getMediaBox().getHeight() - MARGIN;
        y = heading(cs, "DEMO DOCUMENT", width, y, 16);
        y = heading(cs, title, width, y, 12);
        for (String line : lines) {
          y = para(cs, line, y, 10, 14);
        }
      }
      stampFixedMetadata(doc, "support|" + title);
      return toBytes(doc);
    }
  }

  /** Generates a variant tender from a parameter set (Variant B uses {@link VariantSpec#variantB}). */
  public byte[] generateVariant(VariantSpec spec) throws IOException {
    try (PDDocument doc = new PDDocument()) {
      PDPage p1 = newPage(doc);
      float width = p1.getMediaBox().getWidth();
      try (PDPageContentStream cs = new PDPageContentStream(doc, p1)) {
        float y = p1.getMediaBox().getHeight() - MARGIN;
        y = heading(cs, spec.title(), width, y, 16);
        y = gap(y, 10);
        y = para(cs, "Tender No. " + spec.reference(), y, 11, 15);
        y = para(cs, "Issuing authority: " + spec.authority(), y, 11, 15);
        y = para(cs, "Estimated Contract Value: " + spec.estimatedText(), y, 11, 15);
        y = para(cs, "Bid submission deadline: " + spec.deadlineText(), y, 11, 15);
      }
      PDPage p2 = newPage(doc);
      try (PDPageContentStream cs = new PDPageContentStream(doc, p2)) {
        float y = p2.getMediaBox().getHeight() - MARGIN;
        y = heading(cs, "Eligibility Criteria", p2.getMediaBox().getWidth(), y, 14);
        for (VariantSpec.ClauseSpec clause : spec.clauses()) {
          y = para(cs, "Clause " + clause.ref() + " " + clause.text(), y, 10, 14);
          y = gap(y, 6);
        }
      }
      stampFixedMetadata(doc, "variant|" + spec.reference());
      return toBytes(doc);
    }
  }

  // ---------------------------------------------------------------- v1

  private GroundTruth.DocTruth writeV1(Path target) throws IOException {
    try (PDDocument doc = new PDDocument()) {
      pageV1P1(doc);
      pageV1P2(doc);
      GroundTruth.TableTruth table = pageV1P3(doc);
      pageV1P4(doc);
      pageV1P5(doc);
      GroundTruth.RegionTruth stamp = pageV1P6Stamp(doc);
      GroundTruth.RegionTruth signature = pageV1P6Signature();
      stampFixedMetadata(doc, "v1");
      Files.write(target, toBytes(doc));
      return new GroundTruth.DocTruth(V1_FILE, 6, table, stamp, signature);
    }
  }

  private void pageV1P1(PDDocument doc) throws IOException {
    PDPage page = newPage(doc);
    float width = page.getMediaBox().getWidth();
    try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
      float y = page.getMediaBox().getHeight() - MARGIN;
      y = heading(cs, "Smart City Command & Control Centre  Request for Proposal", width, y, 15);
      y = gap(y, 12);
      y = para(cs, "Tender No. SCCC/2026/RFP/001", y, 11, 15);
      y = para(cs, "Issuing authority: Metropolitan Smart City Development Corporation (DEMO)",
          y, 11, 15);
      y = para(cs, "Estimated Contract Value: Rs 60 Cr", y, 11, 15);
    }
  }

  private void pageV1P2(PDDocument doc) throws IOException {
    PDPage page = newPage(doc);
    try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
      float y = page.getMediaBox().getHeight() - MARGIN;
      y = heading(cs, "Bid Particulars", page.getMediaBox().getWidth(), y, 14);
      y = para(cs, "Earnest Money Deposit (EMD): Rs 1.2 Cr", y, 11, 15);
      y = para(cs, "Tender fee: Rs 50,000", y, 11, 15);
      y = para(cs, "Bid submission deadline: 15 December 2026, 17:00 IST", y, 11, 15);
    }
  }

  private GroundTruth.TableTruth pageV1P3(PDDocument doc) throws IOException {
    PDPage page = newPage(doc);
    float width = page.getMediaBox().getWidth();
    List<List<String>> cells = List.of(
        List.of("Financial Year", "Period"),
        List.of("FY 2023-24", "01-Apr-2023 to 31-Mar-2024"),
        List.of("FY 2024-25", "01-Apr-2024 to 31-Mar-2025"),
        List.of("FY 2025-26", "01-Apr-2025 to 31-Mar-2026"));
    GroundTruth.Box box;
    try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
      float y = page.getMediaBox().getHeight() - MARGIN;
      y = heading(cs, "Section 4 Financial Eligibility", width, y, 14);
      y = para(cs, "Table 4-A: Financial years considered for evaluation", y, 11, 15);
      box = drawTable(cs, width, y, cells);
      y = (float) box.y0() - 18;
      y = para(cs, "Clause 4.1 The average annual turnover of the bidder over the last three "
          + "audited financial years shall be not less than Rs 100 Cr.", y, 10, 14);
      y = gap(y, 6);
      y = para(cs, "Clause 4.2 The net worth of the bidder as of the last audited financial "
          + "year shall be not less than Rs 30 Cr.", y, 10, 14);
    }
    return new GroundTruth.TableTruth(3, box, 4, 2, cells);
  }

  private void pageV1P4(PDDocument doc) throws IOException {
    PDPage page = newPage(doc);
    try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
      float y = page.getMediaBox().getHeight() - MARGIN;
      y = heading(cs, "Section 5 Technical Eligibility", page.getMediaBox().getWidth(), y, 14);
      y = para(cs, "Clause 5.1 The bidder must have successfully completed in the last 7 years "
          + "either (a) three (3) similar projects each of value not less than Rs 25 Cr, or "
          + "(b) similar projects satisfying the 80/50/40 rule: one project of at least 80%, "
          + "or two projects of at least 50% each, or three projects of at least 40% each, of "
          + "the estimated contract value. Similar projects means Smart City or Highway "
          + "infrastructure projects.", y, 10, 14);
    }
  }

  private void pageV1P5(PDDocument doc) throws IOException {
    PDPage page = newPage(doc);
    try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
      float y = page.getMediaBox().getHeight() - MARGIN;
      y = heading(cs, "Section 6 Certifications & Documents", page.getMediaBox().getWidth(), y, 14);
      y = para(cs, "Clause 6.1 The bidder must hold a valid CMMI Level 3 certification as on "
          + "the bid submission date.", y, 10, 14);
      y = gap(y, 6);
      y = para(cs, "Clause 6.2 The bidder must submit a Bank Solvency Certificate of not less "
          + "than Rs 15 Cr.", y, 10, 14);
      y = gap(y, 6);
      y = para(cs, "Clause 8.1 (Desirable, non-mandatory) Possession of a valid ISO 27001 "
          + "certification is desirable and carries bonus marks.", y, 10, 14);
    }
  }

  private GroundTruth.RegionTruth pageV1P6Stamp(PDDocument doc) throws IOException {
    PDPage page = newPage(doc);
    float width = page.getMediaBox().getWidth();
    float height = page.getMediaBox().getHeight();
    GroundTruth.Box stampBox;
    try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
      float y = height - MARGIN;
      y = heading(cs, "Declaration", width, y, 14);
      y = para(cs, "The bidder declares that all information furnished in this bid is true to "
          + "the best of its knowledge.", y, 10, 14);
      stampBox = drawStamp(cs, width / 2f, 420f, 70f);
      float sigY = 190f;
      drawSignature(cs, MARGIN + 30f, sigY);
      textLine(cs, REGULAR, 10, MARGIN + 30f, sigY - 40f, "Signature of Authorized Signatory");
    }
    GroundTruth.Box sigBox = new GroundTruth.Box(MARGIN + 20, 150, MARGIN + 210, 230);
    lastSignatureBox = sigBox;
    return new GroundTruth.RegionTruth(6, stampBox);
  }

  private GroundTruth.Box lastSignatureBox;

  private GroundTruth.RegionTruth pageV1P6Signature() {
    return new GroundTruth.RegionTruth(6, lastSignatureBox);
  }

  // ---------------------------------------------------------------- corrigendum

  private GroundTruth.DocTruth writeCorrigendum(Path target) throws IOException {
    GroundTruth.RegionTruth stamp;
    GroundTruth.RegionTruth signature;
    try (PDDocument doc = new PDDocument()) {
      PDPage p1 = newPage(doc);
      try (PDPageContentStream cs = new PDPageContentStream(doc, p1)) {
        float y = p1.getMediaBox().getHeight() - MARGIN;
        y = heading(cs, "Corrigendum No. 1 to Tender No. SCCC/2026/RFP/001",
            p1.getMediaBox().getWidth(), y, 14);
        y = para(cs, "Clause 5.1 is substituted by the following: The bidder must have "
            + "successfully completed in the last 7 years at least two (2) similar projects "
            + "each of value not less than Rs 25 Cr. Similar projects means Smart City or "
            + "Highway infrastructure projects.", y, 10, 14);
        y = gap(y, 6);
        y = para(cs, "Clause 6.1 is substituted by the following: The bidder must hold a valid "
            + "CMMI Level 3 certification OR a valid ISO 9001 certification as on the bid "
            + "submission date.", y, 10, 14);
        y = gap(y, 6);
        y = para(cs, "All other clauses and the deadline are unchanged.", y, 10, 14);
      }
      PDPage p2 = newPage(doc);
      float width = p2.getMediaBox().getWidth();
      try (PDPageContentStream cs = new PDPageContentStream(doc, p2)) {
        float y = p2.getMediaBox().getHeight() - MARGIN;
        y = heading(cs, "Corrigendum Authentication", width, y, 14);
        stamp = new GroundTruth.RegionTruth(2, drawStamp(cs, width / 2f, 480f, 64f));
        drawSignature(cs, MARGIN + 30f, 250f);
        textLine(cs, REGULAR, 10, MARGIN + 30f, 210f, "Signature of Authorized Signatory");
        signature = new GroundTruth.RegionTruth(2,
            new GroundTruth.Box(MARGIN + 20, 210, MARGIN + 210, 290));
      }
      stampFixedMetadata(doc, "corr");
      Files.write(target, toBytes(doc));
      return new GroundTruth.DocTruth(CORR_FILE, 2, null, stamp, signature);
    }
  }

  private GroundTruth.DocTruth writeVariant(Path target, VariantSpec spec) throws IOException {
    Files.write(target, generateVariant(spec));
    return new GroundTruth.DocTruth(VARIANT_B_FILE, 2, null, null, null);
  }

  // ---------------------------------------------------------------- drawing helpers

  private PDPage newPage(PDDocument doc) {
    PDPage page = new PDPage(PDRectangle.A4);
    doc.addPage(page);
    return page;
  }

  private float heading(PDPageContentStream cs, String text, float pageWidth, float yTop,
      int size) throws IOException {
    textLine(cs, BOLD, size, MARGIN, yTop - size, text);
    return yTop - size - 14;
  }

  private float para(PDPageContentStream cs, String text, float yTop, int size, int leading)
      throws IOException {
    float maxWidth = PDRectangle.A4.getWidth() - 2 * MARGIN;
    float y = yTop;
    for (String line : wrap(text, size, maxWidth)) {
      textLine(cs, REGULAR, size, MARGIN, y - size, line);
      y -= leading;
    }
    return y;
  }

  private float gap(float y, int h) {
    return y - h;
  }

  private List<String> wrap(String text, int size, float maxWidth) throws IOException {
    List<String> lines = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    for (String word : text.split(" ")) {
      String trial = current.length() == 0 ? word : current + " " + word;
      float w = REGULAR.getStringWidth(trial) / 1000f * size;
      if (w <= maxWidth) {
        current = new StringBuilder(trial);
      } else {
        lines.add(current.toString());
        current = new StringBuilder(word);
      }
    }
    if (current.length() > 0) {
      lines.add(current.toString());
    }
    return lines;
  }

  private void textLine(PDPageContentStream cs, PDFont font, int size, float x, float y,
      String text) throws IOException {
    cs.beginText();
    cs.setFont(font, size);
    cs.newLineAtOffset(x, y);
    cs.showText(text);
    cs.endText();
  }

  private GroundTruth.Box drawTable(PDPageContentStream cs, float pageWidth, float yTop,
      List<List<String>> cells) throws IOException {
    float rowH = 20f;
    float x0 = MARGIN;
    float x1 = MARGIN + 150f;
    float x2 = pageWidth - MARGIN;
    float yBottom = yTop - cells.size() * rowH;
    cs.setLineWidth(0.8f);
    cs.setStrokingColor(Color.BLACK);
    cs.moveTo(x0, yTop);
    cs.lineTo(x2, yTop);
    cs.lineTo(x2, yBottom);
    cs.lineTo(x0, yBottom);
    cs.lineTo(x0, yTop);
    cs.stroke();
    for (int r = 1; r < cells.size(); r++) {
      float y = yTop - r * rowH;
      cs.moveTo(x0, y);
      cs.lineTo(x2, y);
      cs.stroke();
    }
    cs.moveTo(x1, yTop);
    cs.lineTo(x1, yBottom);
    cs.stroke();
    for (int r = 0; r < cells.size(); r++) {
      float baseline = yTop - r * rowH - 14f;
      PDFont font = r == 0 ? BOLD : REGULAR;
      textLine(cs, font, 9, x0 + 6f, baseline, cells.get(r).get(0));
      textLine(cs, font, 9, x1 + 6f, baseline, cells.get(r).get(1));
    }
    return new GroundTruth.Box(x0, yBottom, x2, yTop);
  }

  private GroundTruth.Box drawStamp(PDPageContentStream cs, float cx, float cy, float r)
      throws IOException {
    cs.setLineWidth(2.5f);
    cs.setStrokingColor(0f, 0f, 0.8f);
    circle(cs, cx, cy, r);
    cs.stroke();
    cs.setLineWidth(1.2f);
    circle(cs, cx, cy, r - 16f);
    cs.stroke();
    String text = "MSDC DEMO SEAL";
    float w = BOLD.getStringWidth(text) / 1000f * 11;
    cs.setNonStrokingColor(0f, 0f, 0.8f);
    textLine(cs, BOLD, 11, cx - w / 2f, cy - 4f, text);
    cs.setNonStrokingColor(Color.BLACK);
    return new GroundTruth.Box(cx - r, cy - r, cx + r, cy + r);
  }

  private void circle(PDPageContentStream cs, float cx, float cy, float r) throws IOException {
    float k = 0.5523f * r;
    cs.moveTo(cx + r, cy);
    cs.curveTo(cx + r, cy + k, cx + k, cy + r, cx, cy + r);
    cs.curveTo(cx - k, cy + r, cx - r, cy + k, cx - r, cy);
    cs.curveTo(cx - r, cy - k, cx - k, cy - r, cx, cy - r);
    cs.curveTo(cx + k, cy - r, cx + r, cy - k, cx + r, cy);
  }

  private void drawSignature(PDPageContentStream cs, float x, float y) throws IOException {
    cs.setLineWidth(1.4f);
    cs.setStrokingColor(0.1f, 0.1f, 0.1f);
    cs.moveTo(x, y);
    cs.curveTo(x + 20, y + 28, x + 35, y - 18, x + 60, y + 6);
    cs.curveTo(x + 85, y + 26, x + 100, y - 12, x + 130, y + 4);
    cs.curveTo(x + 150, y + 12, x + 165, y, x + 180, y + 8);
    cs.stroke();
    cs.moveTo(x + 10, y - 14);
    cs.lineTo(x + 150, y - 14);
    cs.stroke();
    cs.setStrokingColor(Color.BLACK);
  }

  private void stampFixedMetadata(PDDocument doc, String docName) {
    Calendar cal = Calendar.getInstance(IST);
    cal.set(2026, Calendar.NOVEMBER, 4, 10, 0, 0);
    cal.set(Calendar.MILLISECOND, 0);
    PDDocumentInformation info = doc.getDocumentInformation();
    info.setCreationDate(cal);
    info.setModificationDate((Calendar) cal.clone());
    info.setProducer("TenderOps AI demo generator");
    String id = FileSafetyValidator.sha256Hex(
        (docName + "|2026-11-04T10:00+05:30").getBytes(StandardCharsets.UTF_8));
    COSArray ids = new COSArray();
    ids.add(new COSString(id));
    ids.add(new COSString(id));
    doc.getDocument().setDocumentID(ids);
  }

  private byte[] toBytes(PDDocument doc) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    doc.save(out);
    return out.toByteArray();
  }
}
