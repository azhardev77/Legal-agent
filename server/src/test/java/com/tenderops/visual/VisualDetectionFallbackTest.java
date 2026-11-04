package com.tenderops.visual;

import static org.assertj.core.api.Assertions.assertThat;

import com.tenderops.company.Company;
import com.tenderops.company.CompanyRepository;
import com.tenderops.demo.TenderPdfGenerator;
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

/**
 * TC-VIS-04 (service half): SAM errors fall back to geometric detection per page; the run
 * continues in FALLBACK mode with logged retries + fallback rows. Dead local endpoint, no key
 * material in logs.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "tenderops.jwt.secret=ApiTestDummySecret0123456789abcdefghij0123",
    "tenderops.meta-api.key=TESTKEY-FALLBACK-1",
    "tenderops.meta-api.base-url=http://127.0.0.1:1",
    "tenderops.meta-api.max-attempts=2",
    "tenderops.meta-api.backoff-initial-ms=10"})
@Transactional
class VisualDetectionFallbackTest {

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
  private AiCallLogRepository callLogs;

  @Test
  void samFailureFallsBackAndPipelineContinues() throws Exception {
    Long company = companies.save(new Company("Fallback Test Co")).getId();
    generator.generateSamples(temp);
    byte[] pdf = Files.readAllBytes(temp.resolve(TenderPdfGenerator.V1_FILE));
    PdfIngestionService.IngestResult ingest =
        ingestion.ingestNewTender(company, pdf, "Smart_City_RFP_v1.pdf");
    TenderVersion version = versions.findById(ingest.versionId()).orElseThrow();

    VisualDetectionService.DetectResult result = detections.detectVersion(version);

    assertThat(result.samMode()).isEqualTo("FALLBACK");
    assertThat(result.regions()).hasSize(3);
    assertThat(result.regions()).allSatisfy(region ->
        assertThat(region.getSource()).isEqualTo(VisualRegion.SOURCE_FALLBACK));

    List<AiCallLog> logs = callLogs.findByComponentOrderById("SAM");
    assertThat(logs.stream().map(AiCallLog::getStatus).toList())
        .contains("FALLBACK_USED", "CONNECTION_ERROR");
    assertThat(logs.stream().filter(log -> "FALLBACK_USED".equals(log.getStatus())))
        .hasSize(2);
    assertThat(logs).allSatisfy(log -> {
      assertThat(String.valueOf(log.getPurpose())).doesNotContain("TESTKEY");
      assertThat(String.valueOf(log.getErrorText())).doesNotContain("TESTKEY");
    });
  }
}
