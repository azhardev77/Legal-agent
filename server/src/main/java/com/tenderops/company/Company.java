package com.tenderops.company;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "companies")
public class Company {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String name;

  @Column(name = "gst_number")
  private String gstNumber;

  @Column(name = "gst_registered", nullable = false)
  private boolean gstRegistered;

  @Column(name = "msme_registered", nullable = false)
  private boolean msmeRegistered;

  @Column(nullable = false)
  private boolean blacklisted;

  @Column(name = "is_demo", nullable = false)
  private boolean demo;

  /** Monotonic per-company counter bumped on every company-data write (section 6.11). */
  @Column(name = "data_version", nullable = false)
  private long dataVersion;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  protected Company() {}

  public Company(String name) {
    this.name = name;
    this.createdAt = OffsetDateTime.now();
    this.updatedAt = OffsetDateTime.now();
  }

  public Long getId() { return id; }
  public String getName() { return name; }
  public void setName(String name) { this.name = name; }
  public String getGstNumber() { return gstNumber; }
  public void setGstNumber(String gstNumber) { this.gstNumber = gstNumber; }
  public boolean isGstRegistered() { return gstRegistered; }
  public void setGstRegistered(boolean gstRegistered) { this.gstRegistered = gstRegistered; }
  public boolean isMsmeRegistered() { return msmeRegistered; }
  public void setMsmeRegistered(boolean msmeRegistered) { this.msmeRegistered = msmeRegistered; }
  public boolean isBlacklisted() { return blacklisted; }
  public void setBlacklisted(boolean blacklisted) { this.blacklisted = blacklisted; }
  public boolean isDemo() { return demo; }
  public void setDemo(boolean demo) { this.demo = demo; }
  public long getDataVersion() { return dataVersion; }
  public OffsetDateTime getCreatedAt() { return createdAt; }
  public OffsetDateTime getUpdatedAt() { return updatedAt; }

  /** Bumps data_version and updated_at; every company-data write must call this. */
  public void bumpDataVersion() {
    this.dataVersion++;
    this.updatedAt = OffsetDateTime.now();
  }
}
