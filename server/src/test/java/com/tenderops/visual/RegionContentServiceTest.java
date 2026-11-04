package com.tenderops.visual;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenderops.company.Company;
import com.tenderops.company.CompanyRepository;
import com.tenderops.demo.TenderPdfGenerator;
import com.tenderops.document.DocumentPage;
import com.tenderops.document.DocumentPageRepository;
import com.tenderops.document.PdfIngestionService;
import com.tenderops.document.StorageService;
import com.tenderops.tender.TenderVersion;
import com.tenderops.tender.TenderVersionRepository;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

/**
 * TC-VIS-06/07/08: geometric table rebuild is exactly the generated 4x2 cells, stamp text is
 * "MSDC DEMO SEAL", and SCANNED pages without vision degrade to needsHumanReview.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "tenderops.jwt.secret=ApiTestDummySecret0123456789abcdefghij0123",
    "tenderops.meta-api.key="})
@Transactional
class RegionContentServiceTest {

  @TempDir
  Path temp;

  @Autowired
  private RegionContentService content;

  @Autowired
  private VisualDetectionService detections;

  @Autowired
  private PdfIngestionService ingestion;

  @Autowired
  private TenderPdfGenerator generator;

  @Autowired
  private CompanyRepository companies;

  @Autowired
  private TenderVersionRepository versions;

  @Autowired
  private DocumentPageRepository pages;

  @Autowired
  private VisualRegionRepository regions;

  @Autowired
  private StorageService storage;

  @Autowired
  private com.tenderops.document.PageRenderer pageRenderer;

  @Autowired
  private ObjectMapper objectMapper;

  private TenderVersion ingestV1(Long company) throws Exception {
    generator.generateSamples(temp);
    byte[] pdf = Files.readAllBytes(temp.resolve(TenderPdfGenerator.V1_FILE));
    PdfIngestionService.IngestResult ingest =
        ingestion.ingestNewTender(company, pdf, "Smart_City_RFP_v1.pdf");
    return versions.findById(ingest.versionId()).orElseThrow();
  }

  private byte[] pdfBytes(TenderVersion version) {
    return storage.load(version.getStoragePath());
  }

  @Test
  void tcVis06TableRebuildIsExactlyTheGeneratedCells() throws Exception {
    Long company = companies.save(new Company("Content Test Co")).getId();
    TenderVersion version = ingestV1(company);
    detections.detectVersion(version);
    DocumentPage p3 = pages.findByTenderVersionIdOrderByPageNo(version.getId()).get(2);
    VisualRegion table = regions.findByPageIdOrderById(p3.getId()).stream()
        .filter(r -> r.getRegionType().equals(VisualRegion.TABLE_REGION))
        .findFirst().orElseThrow();

    VisualRegion enriched;
    try (PDDocument doc = Loader.loadPDF(new RandomAccessReadBuffer(pdfBytes(version)))) {
      enriched = content.ensureTableContent(version, p3, table, doc);
    }

    assertThat(enriched.getContentSource()).isEqualTo(VisualRegion.CONTENT_GEOMETRIC);
    assertThat(enriched.getCropPath()).isNotNull();
    assertThat(storage.load(enriched.getCropPath())).isNotEmpty();
    Map<?, ?> json = objectMapper.readValue(enriched.getStructuredTable(), Map.class);
    assertThat(json.get("rows")).isEqualTo(List.of(
        List.of("Financial Year", "Period"),
        List.of("FY 2023-24", "01-Apr-2023 to 31-Mar-2024"),
        List.of("FY 2024-25", "01-Apr-2024 to 31-Mar-2025"),
        List.of("FY 2025-26", "01-Apr-2025 to 31-Mar-2026")));
  }

  @Test
  void tcVis07StampTextIsTheSealInscription() throws Exception {
    Long company = companies.save(new Company("Stamp Test Co")).getId();
    TenderVersion version = ingestV1(company);
    detections.detectVersion(version);
    DocumentPage p6 = pages.findByTenderVersionIdOrderByPageNo(version.getId()).get(5);
    VisualRegion stamp = regions.findByPageIdOrderById(p6.getId()).stream()
        .filter(r -> r.getRegionType().equals(VisualRegion.OFFICIAL_STAMP))
        .findFirst().orElseThrow();

    VisualRegion enriched;
    try (PDDocument doc = Loader.loadPDF(new RandomAccessReadBuffer(pdfBytes(version)))) {
      enriched = content.ensureStampContent(version, p6, stamp, doc);
    }

    assertThat(enriched.getContentSource()).isEqualTo(VisualRegion.CONTENT_GEOMETRIC);
    assertThat(enriched.getCropPath()).isNotNull();
    assertThat(enriched.getRegionText().replaceAll("\\s+", " ").trim())
        .isEqualToIgnoringCase("MSDC DEMO SEAL");
  }

  @Test
  void tcVis08ScannedPageWithoutVisionStaysFlaggedAndUnfilled() throws Exception {
    Long company = companies.save(new Company("Scan Test Co")).getId();
    PdfIngestionService.IngestResult ingest =
        ingestion.ingestNewTender(company, imageOnlyPdf(), "scan.pdf");
    TenderVersion version = versions.findById(ingest.versionId()).orElseThrow();
    DocumentPage page = pages.findByTenderVersionIdOrderByPageNo(version.getId()).get(0);
    assertThat(page.isNeedsHumanReview()).isTrue();

    VisualRegion table = regions.save(new VisualRegion(page.getId(),
        VisualRegion.TABLE_REGION, new BigDecimal("0.050000"), new BigDecimal("0.050000"),
        new BigDecimal("0.950000"), new BigDecimal("0.950000"), new BigDecimal("0.6500"),
        VisualRegion.SOURCE_FALLBACK));
    VisualRegion enriched;
    try (PDDocument doc = Loader.loadPDF(new RandomAccessReadBuffer(pdfBytes(version)))) {
      enriched = content.ensureTableContent(version, page, table, doc);
    }

    assertThat(enriched.getContentSource()).isNull();
    assertThat(enriched.getStructuredTable()).isNull();
    assertThat(enriched.getCropPath()).isNotNull();
    DocumentPage reloaded = pages.findById(page.getId()).orElseThrow();
    assertThat(reloaded.isNeedsHumanReview()).isTrue();
    assertThat(reloaded.getReviewReason()).isEqualTo(DocumentPage.OCR_UNAVAILABLE);
  }

  @Test
  void renderFailureFlagsPageInsteadOfCrashing() throws Exception {
    Long company = companies.save(new Company("Render Test Co")).getId();
    TenderVersion version = ingestV1(company);
    DocumentPage badPage = pages.save(
        new DocumentPage(version.getId(), 999, "", DocumentPage.DIGITAL_TEXT));

    DocumentPage result = pageRenderer.ensureRendered(version, badPage);

    assertThat(result.getRenderedPath()).isNull();
    assertThat(result.isNeedsHumanReview()).isTrue();
    assertThat(result.getReviewReason())
        .isEqualTo(com.tenderops.document.PageRenderer.RENDER_FAILED);
  }

  private byte[] imageOnlyPdf() throws Exception {
    BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
    Graphics2D graphics = image.createGraphics();
    graphics.setColor(Color.GRAY);
    graphics.fillRect(0, 0, 100, 100);
    graphics.dispose();
    try (PDDocument doc = new PDDocument()) {
      PDPage page = new PDPage(PDRectangle.A4);
      doc.addPage(page);
      try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
        cs.drawImage(LosslessFactory.createFromImage(doc, image), 0, 0,
            page.getMediaBox().getWidth(), page.getMediaBox().getHeight());
      }
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      doc.save(out);
      return out.toByteArray();
    }
  }
}
