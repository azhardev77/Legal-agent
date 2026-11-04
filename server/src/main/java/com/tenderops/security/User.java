package com.tenderops.security;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/** A user belongs to exactly one company (no multi-tenant hierarchies). */
@Entity
@Table(name = "users")
public class User {

  public static final String ADMIN = "ADMIN";
  public static final String BID_MANAGER = "BID_MANAGER";
  public static final String VIEWER = "VIEWER";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "company_id", nullable = false)
  private Long companyId;

  @Column(nullable = false, unique = true)
  private String email;

  @Column(name = "password_hash", nullable = false)
  private String passwordHash;

  @Column(nullable = false)
  private String role;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  protected User() {}

  public User(Long companyId, String email, String passwordHash, String role) {
    this.companyId = companyId;
    this.email = email;
    this.passwordHash = passwordHash;
    this.role = role;
    this.createdAt = OffsetDateTime.now();
  }

  public Long getId() { return id; }
  public Long getCompanyId() { return companyId; }
  public String getEmail() { return email; }
  public String getPasswordHash() { return passwordHash; }
  public String getRole() { return role; }
  public void setRole(String role) { this.role = role; }
  public OffsetDateTime getCreatedAt() { return createdAt; }
}
