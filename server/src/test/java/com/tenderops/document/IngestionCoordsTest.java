package com.tenderops.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenderops.common.CoordinateUtils;
import com.tenderops.demo.TenderPdfGenerator;
import java.nio.file.Path;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * TC-ING-05 (real-PDF part): word boxes from actual PDFs normalize into [0,1] with top-left
 * origin; a heading near the page top has y0 &lt; 0.2; rotated pages stay in range.
 */
class IngestionCoordsTest {

  @TempDir
  Path temp;

  private final TenderPdfGenerator generator = new TenderPdfGenerator(new ObjectMapper());

  @Test
  void headingNearTopHasSmallY0AndAllBoxesInUnitRange() throws Exception {
    generator.generateSamples(temp);
    try (PDDocument doc = Loader.loadPDF(temp.resolve(TenderPdfGenerator.V1_FILE).toFile())) {
      PDPage page = doc.getPage(0);
      double pageW = page.getMediaBox().getWidth();
      double pageH = page.getMediaBox().getHeight();
      List<PdfWordBoxExtractor.WordBox> words = PdfWordBoxExtractor.extract(doc, 0);
      assertThat(words).isNotEmpty();
      boolean sawHeading = false;
      for (PdfWordBoxExtractor.WordBox word : words) {
        CoordinateUtils.NormalizedBox box = CoordinateUtils.pdfBoxToNormalized(
            word.x0(), word.y0(), word.x1() - word.x0(), word.y1() - word.y0(),
            pageW, pageH, page.getRotation());
        assertThat(box.x0().doubleValue()).isBetween(0.0, 1.0);
        assertThat(box.y0().doubleValue()).isBetween(0.0, 1.0);
        assertThat(box.x1().doubleValue()).isBetween(0.0, 1.0);
        assertThat(box.y1().doubleValue()).isBetween(0.0, 1.0);
        if (!sawHeading && word.text().equals("Smart")) {
          assertThat(box.y0().doubleValue()).isLessThan(0.2);
          sawHeading = true;
        }
      }
      assertThat(sawHeading).isTrue();
    }
  }

  @Test
  void rotatedPageBoxesStayInUnitRange() throws Exception {
    Path pdf = temp.resolve("rotated.pdf");
    try (PDDocument doc = new PDDocument()) {
      PDPage page = new PDPage(PDRectangle.A4);
      page.setRotation(90);
      doc.addPage(page);
      try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
        cs.beginText();
        cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 16);
        cs.newLineAtOffset(100, 700);
        cs.showText("Rotated heading");
        cs.endText();
      }
      doc.save(pdf.toFile());
    }
    try (PDDocument doc = Loader.loadPDF(pdf.toFile())) {
      PDPage page = doc.getPage(0);
      assertThat(page.getRotation()).isEqualTo(90);
      List<PdfWordBoxExtractor.WordBox> words = PdfWordBoxExtractor.extract(doc, 0);
      assertThat(words).isNotEmpty();
      PdfWordBoxExtractor.WordBox first = words.get(0);
      assertThat(first.text()).isEqualTo("Rotated");
      assertThat(first.x0()).isCloseTo(100.0, within(0.5));
      assertThat(first.y0()).isLessThanOrEqualTo(700.0);
      assertThat(first.y1()).isGreaterThanOrEqualTo(700.0);
      for (PdfWordBoxExtractor.WordBox word : words) {
        CoordinateUtils.NormalizedBox box = CoordinateUtils.pdfBoxToNormalized(
            word.x0(), word.y0(), word.x1() - word.x0(), word.y1() - word.y0(),
            page.getMediaBox().getWidth(), page.getMediaBox().getHeight(),
            page.getRotation());
        assertThat(box.x0().doubleValue()).isBetween(0.0, 1.0);
        assertThat(box.y0().doubleValue()).isBetween(0.0, 1.0);
        assertThat(box.x1().doubleValue()).isBetween(0.0, 1.0);
        assertThat(box.y1().doubleValue()).isBetween(0.0, 1.0);
        assertThat(box.x1().doubleValue()).isGreaterThan(box.x0().doubleValue());
        assertThat(box.y1().doubleValue()).isGreaterThan(box.y0().doubleValue());
      }
    }
  }
}
