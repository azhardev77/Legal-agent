package com.tenderops.visual;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "visual_regions")
public class VisualRegion {

  public static final String OFFICIAL_STAMP = "OFFICIAL_STAMP";
  public static final String HANDWRITTEN_SIGNATURE = "HANDWRITTEN_SIGNATURE";
  public static final String TABLE_REGION = "TABLE_REGION";

  public static final String SOURCE_SAM = "SAM_3_1";
  public static final String SOURCE_FALLBACK = "FALLBACK_HEURISTIC";

  public static final String CONTENT_MUSE = "MUSE_VISION";
  public static final String CONTENT_GEOMETRIC = "PDFBOX_GEOMETRIC";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "page_id", nullable = false)
  private Long pageId;

  @Column(name = "region_type", nullable = false, length = 32)
  private String regionType;

  @Column(nullable = false, precision = 8, scale = 6)
  private BigDecimal x0;

  @Column(nullable = false, precision = 8, scale = 6)
  private BigDecimal y0;

  @Column(nullable = false, precision = 8, scale = 6)
  private BigDecimal x1;

  @Column(nullable = false, precision = 8, scale = 6)
  private BigDecimal y1;

  @Column(name = "mask_ref", columnDefinition = "TEXT")
  private String maskRef;

  @Column(nullable = false, precision = 5, scale = 4)
  private BigDecimal confidence;

  @Column(nullable = false, length = 24)
  private String source;

  @Column(name = "crop_path", length = 512)
  private String cropPath;

  @Column(name = "region_text", columnDefinition = "TEXT")
  private String regionText;

  @Column(name = "structured_table", columnDefinition = "TEXT")
  private String structuredTable;

  @Column(name = "content_source", length = 24)
  private String contentSource;

  protected VisualRegion() {}

  public VisualRegion(Long pageId, String regionType, BigDecimal x0, BigDecimal y0,
      BigDecimal x1, BigDecimal y1, BigDecimal confidence, String source) {
    this.pageId = pageId;
    this.regionType = regionType;
    this.x0 = x0;
    this.y0 = y0;
    this.x1 = x1;
    this.y1 = y1;
    this.confidence = confidence;
    this.source = source;
  }

  public Long getId() { return id; }
  public Long getPageId() { return pageId; }
  public String getRegionType() { return regionType; }
  public BigDecimal getX0() { return x0; }
  public BigDecimal getY0() { return y0; }
  public BigDecimal getX1() { return x1; }
  public BigDecimal getY1() { return y1; }
  public String getMaskRef() { return maskRef; }
  public void setMaskRef(String v) { this.maskRef = v; }
  public BigDecimal getConfidence() { return confidence; }
  public String getSource() { return source; }
  public String getCropPath() { return cropPath; }
  public void setCropPath(String v) { this.cropPath = v; }
  public String getRegionText() { return regionText; }
  public void setRegionText(String v) { this.regionText = v; }
  public String getStructuredTable() { return structuredTable; }
  public void setStructuredTable(String v) { this.structuredTable = v; }
  public String getContentSource() { return contentSource; }
  public void setContentSource(String v) { this.contentSource = v; }
}
