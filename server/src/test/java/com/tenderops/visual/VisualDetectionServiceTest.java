package com.tenderops.visual;

import static org.assertj.core.api.Assertions.assertThat;

import com.tenderops.company.Company;
import com.tenderops.company.CompanyRepository;
import com.tenderops.demo.TenderPdfGenerator;
import com.tenderops.document.DocumentPage;
import com.tenderops.document.DocumentPageRepository;
import com.tenderops.document.PdfIngestionService;
import com.tenderops.tender.TenderVersion;
import com.tenderops.tender.TenderVersionRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

/** No-key detection run: candidate pages detected geometrically, samMode=FALLBACK. */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "tenderops.jwt.secret=ApiTestDummySecret0123456789abcdefghij0123",
    "tenderops.meta-api.key="})
@Transactional
class VisualDetectionServiceTest {

  @TempDir
  Path temp;

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

  @Test
  void detectsTableStampAndSignatureInFallbackMode() throws Exception {
    Long company = companies.save(new Company("Visual Test Co")).getId();
    generator.generateSamples(temp);
    byte[] pdf = Files.readAllBytes(temp.resolve(TenderPdfGenerator.V1_FILE));
    PdfIngestionService.IngestResult ingest =
        ingestion.ingestNewTender(company, pdf, "Smart_City_RFP_v1.pdf");
    TenderVersion version = versions.findById(ingest.versionId()).orElseThrow();

    VisualDetectionService.DetectResult result = detections.detectVersion(version);

    assertThat(result.samMode()).isEqualTo("FALLBACK");
    assertThat(result.regions()).hasSize(3);
    assertThat(result.regions()).allSatisfy(region -> {
      assertThat(region.getSource()).isEqualTo(VisualRegion.SOURCE_FALLBACK);
      assertThat(region.getX0().scale()).isEqualTo(6);
      assertThat(region.getConfidence().scale()).isEqualTo(4);
    });
    List<DocumentPage> rows = pages.findByTenderVersionIdOrderByPageNo(version.getId());
    assertThat(regions.findByPageIdOrderById(rows.get(0).getId())).isEmpty();
    assertThat(regions.findByPageIdOrderById(rows.get(1).getId())).isEmpty();
    List<VisualRegion> p3 = regions.findByPageIdOrderById(rows.get(2).getId());
    assertThat(p3).hasSize(1);
    assertThat(p3.get(0).getRegionType()).isEqualTo(VisualRegion.TABLE_REGION);
    assertThat(regions.findByPageIdOrderById(rows.get(3).getId())).isEmpty();
    assertThat(regions.findByPageIdOrderById(rows.get(4).getId())).isEmpty();
    List<VisualRegion> p6 = regions.findByPageIdOrderById(rows.get(5).getId());
    assertThat(p6.stream().map(VisualRegion::getRegionType).toList()).containsExactlyInAnyOrder(
        VisualRegion.OFFICIAL_STAMP, VisualRegion.HANDWRITTEN_SIGNATURE);
  }
}
