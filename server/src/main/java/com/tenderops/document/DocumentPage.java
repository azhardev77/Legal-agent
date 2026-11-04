package com.tenderops.document;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "document_pages")
public class DocumentPage {

  public static final String DIGITAL_TEXT = "DIGITAL_TEXT";
  public static final String SCANNED = "SCANNED";
  public static final String MIXED = "MIXED";

  /** Review reason when a SCANNED/MIXED page cannot be machine-read (TC-VIS-08). */
  public static final String OCR_UNAVAILABLE = "OCR_UNAVAILABLE";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "tender_version_id", nullable = false)
  private Long tenderVersionId;

  @Column(name = "page_no", nullable = false)
  private int pageNo;

  @Column(name = "text_content", columnDefinition = "TEXT")
  private String textContent;

  @Column(nullable = false, length = 16)
  private String classification;

  @Column(name = "needs_human_review", nullable = false)
  private boolean needsHumanReview;

  @Column(name = "review_reason", length = 64)
  private String reviewReason;

  @Column(name = "rendered_path", length = 512)
  private String renderedPath;

  @Column(name = "image_width")
  private Integer imageWidth;

  @Column(name = "image_height")
  private Integer imageHeight;

  @Column(name = "extraction_confidence", precision = 5, scale = 4)
  private BigDecimal extractionConfidence;

  protected DocumentPage() {}

  public DocumentPage(Long tenderVersionId, int pageNo, String textContent,
      String classification) {
    this.tenderVersionId = tenderVersionId;
    this.pageNo = pageNo;
    this.textContent = textContent;
    this.classification = classification;
  }

  public Long getId() { return id; }
  public Long getTenderVersionId() { return tenderVersionId; }
  public int getPageNo() { return pageNo; }
  public String getTextContent() { return textContent; }
  public String getClassification() { return classification; }
  public boolean isNeedsHumanReview() { return needsHumanReview; }
  public void setNeedsHumanReview(boolean v) { this.needsHumanReview = v; }
  public String getReviewReason() { return reviewReason; }
  public void setReviewReason(String v) { this.reviewReason = v; }
  public String getRenderedPath() { return renderedPath; }
  public void setRenderedPath(String v) { this.renderedPath = v; }
  public Integer getImageWidth() { return imageWidth; }
  public void setImageWidth(Integer v) { this.imageWidth = v; }
  public Integer getImageHeight() { return imageHeight; }
  public void setImageHeight(Integer v) { this.imageHeight = v; }
  public BigDecimal getExtractionConfidence() { return extractionConfidence; }
  public void setExtractionConfidence(BigDecimal v) { this.extractionConfidence = v; }
}
