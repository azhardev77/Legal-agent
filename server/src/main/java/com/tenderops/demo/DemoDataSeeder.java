package com.tenderops.demo;

import com.tenderops.audit.AuditService;
import com.tenderops.common.FileSafetyValidator;
import com.tenderops.company.Certificate;
import com.tenderops.company.CertificateRepository;
import com.tenderops.company.Company;
import com.tenderops.company.CompanyDocument;
import com.tenderops.company.CompanyDocumentRepository;
import com.tenderops.company.CompanyRepository;
import com.tenderops.company.FinancialRecord;
import com.tenderops.company.FinancialRecordRepository;
import com.tenderops.company.Project;
import com.tenderops.company.ProjectRepository;
import com.tenderops.config.TenderopsProperties;
import com.tenderops.document.StorageService;
import com.tenderops.security.User;
import com.tenderops.security.UserRepository;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Demo bootstrap (MASTER_PROMPT section 5): demo user, "Apex InfraTech Pvt. Ltd." company data,
 * supporting DEMO PDFs and the three sample tender PDFs. Runs on startup in dev/demo/test when
 * enabled, NEVER in prod. Idempotent: a second run changes nothing.
 */
@Component
public class DemoDataSeeder {

  public static final String DEMO_EMAIL = "demo@tenderops.local";
  public static final String DEMO_COMPANY = "Apex InfraTech Pvt. Ltd.";

  private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

  private final UserRepository users;
  private final CompanyRepository companies;
  private final FinancialRecordRepository financials;
  private final ProjectRepository projects;
  private final CertificateRepository certificates;
  private final CompanyDocumentRepository documents;
  private final StorageService storage;
  private final TenderPdfGenerator generator;
  private final PasswordEncoder passwordEncoder;
  private final AuditService auditService;
  private final TenderopsProperties properties;
  private final Environment environment;
  private final String demoPassword;

  public DemoDataSeeder(UserRepository users, CompanyRepository companies,
      FinancialRecordRepository financials, ProjectRepository projects,
      CertificateRepository certificates, CompanyDocumentRepository documents,
      StorageService storage, TenderPdfGenerator generator, PasswordEncoder passwordEncoder,
      AuditService auditService, TenderopsProperties properties, Environment environment,
      @Value("${DEMO_ADMIN_PASSWORD:}") String demoPassword) {
    this.users = users;
    this.companies = companies;
    this.financials = financials;
    this.projects = projects;
    this.certificates = certificates;
    this.documents = documents;
    this.storage = storage;
    this.generator = generator;
    this.passwordEncoder = passwordEncoder;
    this.auditService = auditService;
    this.properties = properties;
    this.environment = environment;
    this.demoPassword = demoPassword;
  }

  public record SeedResult(boolean created, long companyId, Path samplesDir) {}

  @EventListener(ApplicationReadyEvent.class)
  public void onReady() {
    if (!shouldSeed()) {
      log.info("Demo seeder skipped (enabled={}, profiles={})",
          properties.seeder().enabled(), Arrays.toString(environment.getActiveProfiles()));
      return;
    }
    seed(Paths.get("data/samples"));
  }

  boolean shouldSeed() {
    if (properties.seeder() == null || !properties.seeder().enabled()) {
      return false;
    }
    return Arrays.stream(environment.getActiveProfiles())
        .noneMatch(p -> p.equals("prod") || p.equals("postgres"));
  }

  /**
   * Seeds demo data; safe to call twice (the second call only regenerates the deterministic
   * sample PDFs and changes no database rows).
   */
  @Transactional
  public SeedResult seed(Path samplesDir) {
    try {
      generator.generateSamples(samplesDir);
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot generate sample PDFs", e);
    }
    if (users.existsByEmail(DEMO_EMAIL)) {
      Company existing = companies.findAll().stream()
          .filter(c -> c.getName().equals(DEMO_COMPANY)).findFirst().orElse(null);
      log.info("Demo data already seeded, skipping database insert");
      return new SeedResult(false, existing == null ? -1L : existing.getId(), samplesDir);
    }

    Company company = new Company(DEMO_COMPANY);
    company.setGstNumber("27AAPCA1234F1Z5");
    company.setGstRegistered(true);
    company.setMsmeRegistered(true);
    company.setBlacklisted(false);
    company.setDemo(true);
    company = companies.save(company);
    long companyId = company.getId();

    long fy2324 = supportDoc(companyId, "FY2023-24 audited statement",
        List.of("Audited annual turnover: Rs 98 Cr.", "Financial year ending 31 March 2024."));
    long fy2425 = supportDoc(companyId, "FY2024-25 audited statement",
        List.of("Audited annual turnover: Rs 105 Cr.", "Financial year ending 31 March 2025."));
    long fy2526 = supportDoc(companyId, "FY2025-26 audited statement",
        List.of("Audited annual turnover: Rs 112 Cr.", "Net worth: Rs 35 Cr.",
            "Financial year ending 31 March 2026."));

    financial(new FinancialRecord(companyId, "FY2023-24", LocalDate.of(2024, 3, 31)),
        "980000000.0000", null, true, fy2324);
    financial(new FinancialRecord(companyId, "FY2024-25", LocalDate.of(2025, 3, 31)),
        "1050000000.0000", null, true, fy2425);
    financial(new FinancialRecord(companyId, "FY2025-26", LocalDate.of(2026, 3, 31)),
        "1120000000.0000", "350000000.0000", true, fy2526);

    long scWo = supportDoc(companyId, "Smart City work order",
        List.of("Work order: Smart City Integrated Command Centre.", "Value: Rs 38 Cr."));
    long scCc = supportDoc(companyId, "Smart City completion certificate",
        List.of("Completed 30 June 2024.", "Domain: Smart City."));
    long hwWo = supportDoc(companyId, "Highway work order",
        List.of("Work order: Highway ITS Corridor.", "Value: Rs 29 Cr."));
    long hwCc = supportDoc(companyId, "Highway completion certificate",
        List.of("Completed 31 March 2023.", "Domain: Highway."));

    project(companyId, "Smart City Integrated Command Centre", "380000000.0000",
        LocalDate.of(2024, 6, 30), "SMART_CITY", scWo, scCc);
    project(companyId, "Highway ITS Corridor", "290000000.0000",
        LocalDate.of(2023, 3, 31), "HIGHWAY", hwWo, hwCc);

    long iso9001 = supportDoc(companyId, "ISO 9001 certificate",
        List.of("ISO 9001 Quality Management.", "Valid to 31 August 2027."));
    long iso27001 = supportDoc(companyId, "ISO 27001 certificate",
        List.of("ISO 27001 Information Security.", "Valid to 31 May 2027."));
    long cmmi = supportDoc(companyId, "CMMI Level 3 certificate",
        List.of("CMMI Maturity Level 3.", "Expired 31 March 2026."));

    certificate(companyId, "ISO_9001", null, null, null, LocalDate.of(2027, 8, 31), iso9001);
    certificate(companyId, "ISO_27001", null, null, null, LocalDate.of(2027, 5, 31), iso27001);
    certificate(companyId, "CMMI", "Level 3", 3, null, LocalDate.of(2026, 3, 31), cmmi);
    // NO Bank Solvency Certificate on purpose (golden 6.2 is UNKNOWN).

    String password = demoPassword == null || demoPassword.isBlank()
        ? generatedPassword() : demoPassword;
    User user = users.save(new User(companyId, DEMO_EMAIL,
        passwordEncoder.encode(password), User.ADMIN));

    company.bumpDataVersion();
    companies.save(company);
    auditService.record(companyId, user.getId(), "DEMO_SEEDED", "COMPANY", companyId, null,
        Map.of("company", DEMO_COMPANY));
    log.info("Demo data seeded for company '{}'", DEMO_COMPANY);
    return new SeedResult(true, companyId, samplesDir);
  }

  private long supportDoc(long companyId, String title, List<String> lines) {
    try {
      byte[] bytes = generator.supportDoc(title, lines);
      String relative = storage.store(bytes, ".pdf");
      CompanyDocument doc = documents.save(new CompanyDocument(companyId, title + ".pdf",
          relative, FileSafetyValidator.sha256Hex(bytes), "application/pdf", bytes.length, true));
      return doc.getId();
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot generate support doc: " + title, e);
    }
  }

  private void financial(FinancialRecord record, String turnover, String netWorth,
      boolean audited, long documentId) {
    record.setTurnover(new BigDecimal(turnover));
    record.setNetWorth(netWorth == null ? null : new BigDecimal(netWorth));
    record.setAudited(audited);
    record.setDocumentId(documentId);
    financials.save(record);
  }

  private void project(long companyId, String name, String value, LocalDate completed,
      String domain, long workOrderId, long completionCertId) {
    Project project = new Project(companyId, name);
    project.setContractValue(new BigDecimal(value));
    project.setStatus("COMPLETED");
    project.setCompletionDate(completed);
    project.setDomainCategory(domain);
    project.setWorkOrderDocId(workOrderId);
    project.setCompletionCertDocId(completionCertId);
    projects.save(project);
  }

  private void certificate(long companyId, String name, String levelText, Integer level,
      LocalDate issue, LocalDate expiry, long documentId) {
    Certificate certificate = new Certificate(companyId, name);
    certificate.setLevelText(levelText);
    certificate.setLevelNumber(level);
    certificate.setIssueDate(issue);
    certificate.setExpiryDate(expiry);
    certificate.setDocumentId(documentId);
    certificates.save(certificate);
  }

  private String generatedPassword() {
    SecureRandom random = new SecureRandom();
    String alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    StringBuilder sb = new StringBuilder(20);
    for (int i = 0; i < 20; i++) {
      sb.append(alphabet.charAt(random.nextInt(alphabet.length())));
    }
    // Required by MASTER_PROMPT 5(a): log the generated demo password exactly once.
    log.warn("DEMO_ADMIN_PASSWORD is empty: generated demo login password, shown once, DEMO ONLY: {}",
        sb);
    return sb.toString();
  }
}
