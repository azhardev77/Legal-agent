package com.tenderops.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.tenderops.company.Company;
import com.tenderops.company.CompanyRepository;
import com.tenderops.demo.TenderPdfGenerator;
import com.tenderops.document.PdfIngestionService;
import com.tenderops.tender.TenderRequirement;
import com.tenderops.tender.TenderRequirementRepository;
import com.tenderops.tender.TenderVersion;
import com.tenderops.tender.TenderVersionRepository;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

/**
 * TC-AI-06/10 (fallback halves): no-key extraction of the golden tender persists 6 verified
 * requirements with Java-converted amounts, fingerprints and parsed tender metadata.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "tenderops.jwt.secret=ApiTestDummySecret0123456789abcdefghij0123",
    "tenderops.meta-api.key="})
@Transactional
class ExtractionServiceTest {

  @TempDir
  Path temp;

  @Autowired
  private ExtractionService extraction;

  @Autowired
  private PdfIngestionService ingestion;

  @Autowired
  private TenderPdfGenerator generator;

  @Autowired
  private CompanyRepository companies;

  @Autowired
  private TenderVersionRepository versions;

  @Autowired
  private TenderRequirementRepository requirements;

  private TenderVersion ingestV1() throws Exception {
    Long company = companies.save(new Company("Extract Test Co")).getId();
    generator.generateSamples(temp);
    byte[] pdf = Files.readAllBytes(temp.resolve(TenderPdfGenerator.V1_FILE));
    PdfIngestionService.IngestResult ingest =
        ingestion.ingestNewTender(company, pdf, "Smart_City_RFP_v1.pdf");
    return versions.findById(ingest.versionId()).orElseThrow();
  }

  private TenderRequirement byRef(List<TenderRequirement> rows, String ref) {
    return rows.stream().filter(row -> row.getClauseRef().equals(ref)).findFirst()
        .orElseThrow(() -> new AssertionError("Missing clause " + ref));
  }

  @Test
  void fallbackExtractsGoldenTender() throws Exception {
    TenderVersion version = ingestV1();

    ExtractionService.ExtractResult result = extraction.extractVersion(version);

    assertThat(result.museMode()).isEqualTo("FALLBACK");
    assertThat(result.requirements()).hasSize(6);
    assertThat(result.requirements().stream().map(TenderRequirement::getClauseRef).toList())
        .containsExactly("4.1", "4.2", "5.1", "6.1", "6.2", "8.1");
    assertThat(result.requirements()).allSatisfy(row -> {
      assertThat(row.isQuoteVerified()).isTrue();
      assertThat(row.getOriginalQuote()).isNotBlank();
      assertThat(row.getFingerprint()).hasSize(64);
      assertThat(row.getSourcePage()).isBetween(3, 5);
    });
    assertThat(result.requirements().stream().map(TenderRequirement::getFingerprint).toList())
        .doesNotHaveDuplicates();

    TenderRequirement turnover = byRef(result.requirements(), "4.1");
    assertThat(turnover.getMetric()).isEqualTo("AVG_ANNUAL_TURNOVER");
    assertThat(turnover.getAmountValue()).isEqualByComparingTo(new BigDecimal("1000000000"));
    assertThat(turnover.getCurrency()).isEqualTo("INR");
    assertThat(turnover.getLookbackYears()).isEqualTo(3);
    assertThat(turnover.getConfidence()).isEqualByComparingTo(new BigDecimal("0.85"));

    TenderRequirement projects = byRef(result.requirements(), "5.1");
    assertThat(projects.getMetric()).isEqualTo("OTHER");
    assertThat(projects.getConditionTree()).contains("\"op\":\"OR\"");
    assertThat(projects.getConditionTree()).contains("SIMILAR_PROJECTS_COUNT_VALUE");
    assertThat(projects.getConditionTree()).contains("SIMILAR_PROJECTS_80_50_40");

    TenderRequirement desirable = byRef(result.requirements(), "8.1");
    assertThat(desirable.isMandatory()).isFalse();

    TenderVersion reloaded = versions.findById(version.getId()).orElseThrow();
    assertThat(reloaded.getReference()).isEqualTo("SCCC/2026/RFP/001");
    assertThat(reloaded.getEstimatedValue())
        .isEqualByComparingTo(new BigDecimal("600000000"));
    assertThat(reloaded.getEmdAmount()).isEqualByComparingTo(new BigDecimal("12000000"));
    assertThat(reloaded.getTenderFee()).isEqualByComparingTo(new BigDecimal("50000"));
    assertThat(reloaded.getSubmissionDeadline())
        .isEqualTo(OffsetDateTime.parse("2026-12-15T17:00+05:30"));
  }

  @Test
  void extractionIsIdempotentPerVersion() throws Exception {
    TenderVersion version = ingestV1();
    extraction.extractVersion(version);
    ExtractionService.ExtractResult second = extraction.extractVersion(version);
    assertThat(second.requirements()).hasSize(6);
    assertThat(requirements.countByTenderVersionId(version.getId())).isEqualTo(6);
  }

  @Test
  void foreignCurrencyDowngradesToOther() throws Exception {
    Long company = companies.save(new Company("Currency Test Co")).getId();
    byte[] pdf;
    try (org.apache.pdfbox.pdmodel.PDDocument doc =
        new org.apache.pdfbox.pdmodel.PDDocument()) {
      org.apache.pdfbox.pdmodel.PDPage page =
          new org.apache.pdfbox.pdmodel.PDPage(
              org.apache.pdfbox.pdmodel.common.PDRectangle.A4);
      doc.addPage(page);
      try (org.apache.pdfbox.pdmodel.PDPageContentStream cs =
          new org.apache.pdfbox.pdmodel.PDPageContentStream(doc, page)) {
        cs.beginText();
        cs.setFont(new org.apache.pdfbox.pdmodel.font.PDType1Font(
            org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA), 11);
        cs.newLineAtOffset(57, 780);
        cs.showText("Clause 1.1 The average annual turnover shall be not less than USD 100 Cr.");
        cs.endText();
      }
      java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
      doc.save(out);
      pdf = out.toByteArray();
    }
    PdfIngestionService.IngestResult ingest =
        ingestion.ingestNewTender(company, pdf, "usd.pdf");
    TenderVersion version = versions.findById(ingest.versionId()).orElseThrow();

    ExtractionService.ExtractResult result = extraction.extractVersion(version);

    assertThat(result.requirements()).hasSize(1);
    assertThat(result.requirements().get(0).getMetric()).isEqualTo("OTHER");
    assertThat(result.requirements().get(0).getAmountText()).isEqualTo("USD 100 Cr");
    assertThat(result.requirements().get(0).getAmountValue()).isNull();
    assertThat(result.requirements().get(0).isQuoteVerified()).isTrue();
  }
}
