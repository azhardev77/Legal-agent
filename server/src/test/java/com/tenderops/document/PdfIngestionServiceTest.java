package com.tenderops.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tenderops.common.PdfRejectedException;
import com.tenderops.company.Company;
import com.tenderops.company.CompanyRepository;
import com.tenderops.demo.TenderPdfGenerator;
import com.tenderops.tender.TenderRepository;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionJavaScript;
import org.apache.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionLaunch;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

/** TC-ING-01/02/03/04: validation, dedup, seeded-text extraction, scanned classification. */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "tenderops.jwt.secret=ApiTestDummySecret0123456789abcdefghij0123",
    "tenderops.meta-api.key="})
@Transactional
class PdfIngestionServiceTest {

  @TempDir
  Path temp;

  @Autowired
  private PdfIngestionService ingestion;

  @Autowired
  private CompanyRepository companies;

  @Autowired
  private TenderRepository tenders;

  @Autowired
  private DocumentPageRepository pages;

  @Autowired
  private TenderPdfGenerator generator;

  private Long companyId() {
    return companies.save(new Company("Ingestion Test Co")).getId();
  }

  private byte[] v1Bytes() throws IOException {
    generator.generateSamples(temp);
    return Files.readAllBytes(temp.resolve(TenderPdfGenerator.V1_FILE));
  }

  @Test
  void tcIng01RejectsNonPdfCorruptEmptyEncryptedJsLaunchAndOversized() throws Exception {
    Long company = companyId();
    assertThatThrownBy(() -> ingestion.ingestNewTender(company, "hello".getBytes(), "x.pdf"))
        .isInstanceOf(PdfRejectedException.class)
        .hasFieldOrPropertyWithValue("reason", "NOT_PDF");
    assertThatThrownBy(() -> ingestion.ingestNewTender(company, new byte[0], "x.pdf"))
        .isInstanceOf(PdfRejectedException.class)
        .hasFieldOrPropertyWithValue("reason", "EMPTY");
    assertThatThrownBy(
        () -> ingestion.ingestNewTender(company, "%PDF-1.7\n%garbage".getBytes(), "x.pdf"))
        .isInstanceOf(PdfRejectedException.class)
        .hasFieldOrPropertyWithValue("reason", "CORRUPT");
    assertThatThrownBy(
        () -> ingestion.ingestNewTender(company, encryptedPdf(), "enc.pdf"))
        .isInstanceOf(PdfRejectedException.class)
        .hasFieldOrPropertyWithValue("reason", "ENCRYPTED");
    assertThatThrownBy(() -> ingestion.ingestNewTender(company, jsPdf(), "js.pdf"))
        .isInstanceOf(PdfRejectedException.class)
        .hasFieldOrPropertyWithValue("reason", "ACTIVE_CONTENT");
    assertThatThrownBy(() -> ingestion.ingestNewTender(company, launchPdf(), "launch.pdf"))
        .isInstanceOf(PdfRejectedException.class)
        .hasFieldOrPropertyWithValue("reason", "ACTIVE_CONTENT");
    byte[] huge = new byte[26_214_401];
    huge[0] = '%';
    huge[1] = 'P';
    huge[2] = 'D';
    huge[3] = 'F';
    huge[4] = '-';
    assertThatThrownBy(() -> ingestion.ingestNewTender(company, huge, "huge.pdf"))
        .isInstanceOf(PdfRejectedException.class)
        .hasFieldOrPropertyWithValue("reason", "TOO_LARGE");
    assertThat(tenders.count()).isZero();
  }

  @Test
  void tcIng02DuplicateSha256ReturnsExistingWithoutReprocessing() throws Exception {
    Long company = companyId();
    byte[] pdf = v1Bytes();
    PdfIngestionService.IngestResult first =
        ingestion.ingestNewTender(company, pdf, "Smart_City_RFP_v1.pdf");
    assertThat(first.duplicate()).isFalse();
    assertThat(first.pageCount()).isEqualTo(6);
    PdfIngestionService.IngestResult second =
        ingestion.ingestNewTender(company, pdf, "Smart_City_RFP_v1.pdf");
    assertThat(second.duplicate()).isTrue();
    assertThat(second.versionId()).isEqualTo(first.versionId());
    assertThat(pages.countByTenderVersionId(first.versionId())).isEqualTo(6);
    assertThat(tenders.count()).isEqualTo(1);
  }

  @Test
  void tcIng03SeededPdfTextContainsGoldenClauses() throws Exception {
    Long company = companyId();
    PdfIngestionService.IngestResult result =
        ingestion.ingestNewTender(company, v1Bytes(), "Smart_City_RFP_v1.pdf");
    List<DocumentPage> rows = pages.findByTenderVersionIdOrderByPageNo(result.versionId());
    assertThat(rows).hasSize(6);
    assertThat(rows).allSatisfy(row -> {
      assertThat(row.getClassification()).isEqualTo(DocumentPage.DIGITAL_TEXT);
      assertThat(row.isNeedsHumanReview()).isFalse();
    });
    String p3 = rows.get(2).getTextContent().replaceAll("\\s+", " ");
    assertThat(p3).contains("Clause 4.1").contains("not less than Rs 100 Cr")
        .contains("Table 4-A");
    String p5 = rows.get(4).getTextContent().replaceAll("\\s+", " ");
    assertThat(p5).contains("Clause 6.1").contains("Clause 6.2").contains("Clause 8.1");
  }

  @Test
  void tcIng04ImageOnlyPageIsScannedAndFlagged() throws Exception {
    Long company = companyId();
    PdfIngestionService.IngestResult result =
        ingestion.ingestNewTender(company, imageOnlyPdf(), "scan.pdf");
    List<DocumentPage> rows = pages.findByTenderVersionIdOrderByPageNo(result.versionId());
    assertThat(rows).hasSize(1);
    assertThat(rows.get(0).getClassification()).isEqualTo(DocumentPage.SCANNED);
    assertThat(rows.get(0).isNeedsHumanReview()).isTrue();
    assertThat(rows.get(0).getReviewReason()).isEqualTo(DocumentPage.OCR_UNAVAILABLE);
  }

  private byte[] encryptedPdf() throws IOException {
    try (PDDocument doc = new PDDocument()) {
      PDPage page = new PDPage(PDRectangle.A4);
      doc.addPage(page);
      try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
        cs.beginText();
        cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
        cs.newLineAtOffset(100, 700);
        cs.showText("secret");
        cs.endText();
      }
      AccessPermission permissions = new AccessPermission();
      doc.protect(new StandardProtectionPolicy("owner-pass-1", "user-pass-1", permissions));
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      doc.save(out);
      return out.toByteArray();
    }
  }

  private byte[] jsPdf() throws IOException {
    try (PDDocument doc = new PDDocument()) {
      doc.addPage(new PDPage(PDRectangle.A4));
      doc.getDocumentCatalog().setOpenAction(new PDActionJavaScript("app.alert(1);"));
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      doc.save(out);
      return out.toByteArray();
    }
  }

  private byte[] launchPdf() throws IOException {
    try (PDDocument doc = new PDDocument()) {
      PDPage page = new PDPage(PDRectangle.A4);
      doc.addPage(page);
      PDAnnotationLink link = new PDAnnotationLink();
      link.setRectangle(new PDRectangle(100, 700, 80, 20));
      PDActionLaunch launch = new PDActionLaunch();
      PDComplexFileSpecification spec = new PDComplexFileSpecification();
      spec.setFile("C:/evil.exe");
      launch.setFile(spec);
      link.setAction(launch);
      page.getAnnotations().add(link);
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      doc.save(out);
      return out.toByteArray();
    }
  }

  private byte[] imageOnlyPdf() throws IOException {
    BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
    Graphics2D graphics = image.createGraphics();
    graphics.setColor(Color.BLUE);
    graphics.fillRect(0, 0, 100, 100);
    graphics.dispose();
    try (PDDocument doc = new PDDocument()) {
      PDPage page = new PDPage(PDRectangle.A4);
      doc.addPage(page);
      PDImageXObject ximage = LosslessFactory.createFromImage(doc, image);
      try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
        cs.drawImage(ximage, 0, 0, page.getMediaBox().getWidth(),
            page.getMediaBox().getHeight());
      }
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      doc.save(out);
      return out.toByteArray();
    }
  }
}
