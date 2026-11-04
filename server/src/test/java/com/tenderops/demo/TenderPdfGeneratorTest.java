package com.tenderops.demo;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenderops.common.FileSafetyValidator;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** M2 gate: generated PDFs (6/2/2 pages), golden clause texts, ground truth, determinism. */
class TenderPdfGeneratorTest {

  @TempDir
  Path temp;

  private final TenderPdfGenerator generator = new TenderPdfGenerator(new ObjectMapper());

  private String textOf(Path pdf) throws Exception {
    try (PDDocument doc = Loader.loadPDF(pdf.toFile())) {
      return new PDFTextStripper().getText(doc);
    }
  }

  /** Collapse layout line-wraps so assertions check content, not line-break positions. */
  private static String oneLine(String text) {
    return text.replaceAll("\\s+", " ");
  }

  private int pagesOf(Path pdf) throws Exception {
    try (PDDocument doc = Loader.loadPDF(pdf.toFile())) {
      return doc.getNumberOfPages();
    }
  }

  @Test
  void generatesThreeSamplesWithExpectedPageCounts() throws Exception {
    generator.generateSamples(temp);
    assertThat(pagesOf(temp.resolve(TenderPdfGenerator.V1_FILE))).isEqualTo(6);
    assertThat(pagesOf(temp.resolve(TenderPdfGenerator.CORR_FILE))).isEqualTo(2);
    assertThat(pagesOf(temp.resolve(TenderPdfGenerator.VARIANT_B_FILE))).isEqualTo(2);
    assertThat(Files.exists(temp.resolve(TenderPdfGenerator.TRUTH_FILE))).isTrue();
  }

  @Test
  void v1TextContainsEachGoldenClause() throws Exception {
    generator.generateSamples(temp);
    String text = oneLine(textOf(temp.resolve(TenderPdfGenerator.V1_FILE)));
    assertThat(text).contains("Smart City Command & Control Centre")
        .contains("SCCC/2026/RFP/001")
        .contains("Metropolitan Smart City Development Corporation (DEMO)")
        .contains("Rs 60 Cr")
        .contains("Rs 1.2 Cr")
        .contains("Rs 50,000")
        .contains("15 December 2026, 17:00 IST")
        .contains("Table 4-A: Financial years considered for evaluation")
        .contains("FY 2023-24")
        .contains("01-Apr-2023 to 31-Mar-2024")
        .contains("Clause 4.1")
        .contains("not less than Rs 100 Cr")
        .contains("Clause 4.2")
        .contains("not less than Rs 30 Cr")
        .contains("Clause 5.1")
        .contains("80/50/40 rule")
        .contains("Clause 6.1")
        .contains("CMMI Level 3")
        .contains("Clause 6.2")
        .contains("Bank Solvency Certificate of not less than Rs 15 Cr")
        .contains("Clause 8.1")
        .contains("ISO 27001")
        .contains("MSDC DEMO SEAL")
        .contains("Signature of Authorized Signatory");
  }

  @Test
  void corrigendumAndVariantTexts() throws Exception {
    generator.generateSamples(temp);
    String corr = oneLine(textOf(temp.resolve(TenderPdfGenerator.CORR_FILE)));
    assertThat(corr).contains("Corrigendum No. 1 to Tender No. SCCC/2026/RFP/001")
        .contains("Clause 5.1 is substituted by")
        .contains("at least two (2) similar projects")
        .contains("Clause 6.1 is substituted by")
        .contains("OR a valid ISO 9001 certification")
        .contains("All other clauses and the deadline are unchanged.");
    String variant = oneLine(textOf(temp.resolve(TenderPdfGenerator.VARIANT_B_FILE)));
    assertThat(variant).contains("VB/2026/001")
        .contains("Rs 50 Cr")
        .contains("20 December 2026, 17:00 IST")
        .contains("Rs. 110 crore")
        .contains("Rs. 20 crore")
        .contains("Rs. 30 crore")
        .contains("valid ISO 9001 certificate");
  }

  @Test
  void groundTruthMatchesSection5() throws Exception {
    generator.generateSamples(temp);
    String json = Files.readString(temp.resolve(TenderPdfGenerator.TRUTH_FILE),
        StandardCharsets.UTF_8);
    GroundTruth truth = new ObjectMapper().readValue(json, GroundTruth.class);
    assertThat(truth.documents()).hasSize(3);
    GroundTruth.DocTruth v1 = truth.documents().stream()
        .filter(d -> d.file().equals(TenderPdfGenerator.V1_FILE)).findFirst().orElseThrow();
    assertThat(v1.pages()).isEqualTo(6);
    assertThat(v1.table().page()).isEqualTo(3);
    assertThat(v1.table().rows()).isEqualTo(4);
    assertThat(v1.table().cols()).isEqualTo(2);
    assertThat(v1.table().cells()).isEqualTo(List.of(
        List.of("Financial Year", "Period"),
        List.of("FY 2023-24", "01-Apr-2023 to 31-Mar-2024"),
        List.of("FY 2024-25", "01-Apr-2024 to 31-Mar-2025"),
        List.of("FY 2025-26", "01-Apr-2025 to 31-Mar-2026")));
    assertThat(v1.stamp().page()).isEqualTo(6);
    assertThat(v1.signature().page()).isEqualTo(6);
    assertThat(v1.table().bbox().x0()).isGreaterThanOrEqualTo(0);
    assertThat(v1.table().bbox().x1()).isLessThanOrEqualTo(595.28);
    assertThat(v1.stamp().bbox().x1() - v1.stamp().bbox().x0()).isCloseTo(140.0,
        org.assertj.core.data.Offset.offset(0.01));
    GroundTruth.DocTruth corr = truth.documents().stream()
        .filter(d -> d.file().equals(TenderPdfGenerator.CORR_FILE)).findFirst().orElseThrow();
    assertThat(corr.pages()).isEqualTo(2);
    assertThat(corr.stamp().page()).isEqualTo(2);
    assertThat(corr.signature().page()).isEqualTo(2);
  }

  @Test
  void generationIsByteDeterministic() throws Exception {
    Path first = temp.resolve("first");
    Path second = temp.resolve("second");
    generator.generateSamples(first);
    generator.generateSamples(second);
    for (String file : List.of(TenderPdfGenerator.V1_FILE, TenderPdfGenerator.CORR_FILE,
        TenderPdfGenerator.VARIANT_B_FILE)) {
      String a = FileSafetyValidator.sha256Hex(Files.readAllBytes(first.resolve(file)));
      String b = FileSafetyValidator.sha256Hex(Files.readAllBytes(second.resolve(file)));
      assertThat(a).isEqualTo(b);
    }
  }

  @Test
  void supportDocIsMarkedDemo() throws Exception {
    byte[] bytes = generator.supportDoc("Test certificate", List.of("Line one."));
    Path out = temp.resolve("support.pdf");
    Files.write(out, bytes);
    assertThat(textOf(out)).contains("DEMO DOCUMENT").contains("Test certificate")
        .contains("Line one.");
  }
}
