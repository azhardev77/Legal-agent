package com.tenderops.company;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "company_certificates")
public class Certificate {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "company_id", nullable = false)
  private Long companyId;

  @Column(nullable = false)
  private String name;

  @Column(name = "level_text")
  private String levelText;

  @Column(name = "level_number")
  private Integer levelNumber;

  @Column(name = "issue_date")
  private LocalDate issueDate;

  @Column(name = "expiry_date")
  private LocalDate expiryDate;

  @Column(precision = 19, scale = 4)
  private BigDecimal amount;

  @Column(nullable = false)
  private String currency = "INR";

  @Column(name = "document_id")
  private Long documentId;

  protected Certificate() {}

  public Certificate(Long companyId, String name) {
    this.companyId = companyId;
    this.name = name;
  }

  public Long getId() { return id; }
  public Long getCompanyId() { return companyId; }
  public String getName() { return name; }
  public void setName(String name) { this.name = name; }
  public String getLevelText() { return levelText; }
  public void setLevelText(String levelText) { this.levelText = levelText; }
  public Integer getLevelNumber() { return levelNumber; }
  public void setLevelNumber(Integer levelNumber) { this.levelNumber = levelNumber; }
  public LocalDate getIssueDate() { return issueDate; }
  public void setIssueDate(LocalDate issueDate) { this.issueDate = issueDate; }
  public LocalDate getExpiryDate() { return expiryDate; }
  public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }
  public BigDecimal getAmount() { return amount; }
  public void setAmount(BigDecimal amount) { this.amount = amount; }
  public String getCurrency() { return currency; }
  public void setCurrency(String currency) { this.currency = currency; }
  public Long getDocumentId() { return documentId; }
  public void setDocumentId(Long documentId) { this.documentId = documentId; }
}
