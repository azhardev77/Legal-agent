package com.tenderops.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/** Insert-only audit record with before/after JSON (R11). */
@Entity
@Table(name = "audit_logs")
public class AuditLog {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "company_id")
  private Long companyId;

  @Column(name = "user_id")
  private Long userId;

  @Column(nullable = false)
  private String action;

  @Column(name = "entity_type", nullable = false)
  private String entityType;

  @Column(name = "entity_id")
  private Long entityId;

  @Column(name = "before_json", columnDefinition = "TEXT")
  private String beforeJson;

  @Column(name = "after_json", columnDefinition = "TEXT")
  private String afterJson;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  protected AuditLog() {}

  public AuditLog(Long companyId, Long userId, String action, String entityType, Long entityId,
      String beforeJson, String afterJson) {
    this.companyId = companyId;
    this.userId = userId;
    this.action = action;
    this.entityType = entityType;
    this.entityId = entityId;
    this.beforeJson = beforeJson;
    this.afterJson = afterJson;
    this.createdAt = OffsetDateTime.now();
  }

  public Long getId() { return id; }
  public Long getCompanyId() { return companyId; }
  public Long getUserId() { return userId; }
  public String getAction() { return action; }
  public String getEntityType() { return entityType; }
  public Long getEntityId() { return entityId; }
  public String getBeforeJson() { return beforeJson; }
  public String getAfterJson() { return afterJson; }
  public OffsetDateTime getCreatedAt() { return createdAt; }
}
