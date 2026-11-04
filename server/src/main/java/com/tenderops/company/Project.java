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
@Table(name = "company_projects")
public class Project {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "company_id", nullable = false)
  private Long companyId;

  @Column(nullable = false)
  private String name;

  @Column(name = "contract_value", precision = 19, scale = 4)
  private BigDecimal contractValue;

  @Column(nullable = false)
  private String currency = "INR";

  @Column(nullable = false)
  private String status = "COMPLETED";

  @Column(name = "completion_date")
  private LocalDate completionDate;

  @Column(name = "domain_category", nullable = false)
  private String domainCategory;

  @Column(name = "work_order_doc_id")
  private Long workOrderDocId;

  @Column(name = "completion_cert_doc_id")
  private Long completionCertDocId;

  protected Project() {}

  public Project(Long companyId, String name) {
    this.companyId = companyId;
    this.name = name;
  }

  public Long getId() { return id; }
  public Long getCompanyId() { return companyId; }
  public String getName() { return name; }
  public void setName(String name) { this.name = name; }
  public BigDecimal getContractValue() { return contractValue; }
  public void setContractValue(BigDecimal contractValue) { this.contractValue = contractValue; }
  public String getCurrency() { return currency; }
  public void setCurrency(String currency) { this.currency = currency; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public LocalDate getCompletionDate() { return completionDate; }
  public void setCompletionDate(LocalDate completionDate) { this.completionDate = completionDate; }
  public String getDomainCategory() { return domainCategory; }
  public void setDomainCategory(String domainCategory) { this.domainCategory = domainCategory; }
  public Long getWorkOrderDocId() { return workOrderDocId; }
  public void setWorkOrderDocId(Long workOrderDocId) { this.workOrderDocId = workOrderDocId; }
  public Long getCompletionCertDocId() { return completionCertDocId; }
  public void setCompletionCertDocId(Long completionCertDocId) {
    this.completionCertDocId = completionCertDocId;
  }
}
