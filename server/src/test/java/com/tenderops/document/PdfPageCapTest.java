package com.tenderops.document;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tenderops.common.PdfRejectedException;
import com.tenderops.company.Company;
import com.tenderops.company.CompanyRepository;
import java.io.ByteArrayOutputStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

/** Page-count cap half of TC-ING-01 (limit overridden to 2 pages for speed). */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "tenderops.jwt.secret=ApiTestDummySecret0123456789abcdefghij0123",
    "tenderops.meta-api.key=",
    "tenderops.limits.max-pdf-pages=2"})
@Transactional
class PdfPageCapTest {

  @Autowired
  private PdfIngestionService ingestion;

  @Autowired
  private CompanyRepository companies;

  @Test
  void pageCountAboveCapIsRejected() throws Exception {
    Long company = companies.save(new Company("Cap Test Co")).getId();
    byte[] pdf;
    try (PDDocument doc = new PDDocument()) {
      doc.addPage(new PDPage(PDRectangle.A4));
      doc.addPage(new PDPage(PDRectangle.A4));
      doc.addPage(new PDPage(PDRectangle.A4));
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      doc.save(out);
      pdf = out.toByteArray();
    }
    assertThatThrownBy(() -> ingestion.ingestNewTender(company, pdf, "three.pdf"))
        .isInstanceOf(PdfRejectedException.class)
        .hasFieldOrPropertyWithValue("reason", "PAGE_LIMIT");
  }
}
