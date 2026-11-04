package com.tenderops.demo;

import static org.assertj.core.api.Assertions.assertThat;

import com.tenderops.audit.AuditService;
import com.tenderops.company.CertificateRepository;
import com.tenderops.company.Company;
import com.tenderops.company.CompanyDocumentRepository;
import com.tenderops.company.CompanyRepository;
import com.tenderops.company.FinancialRecordRepository;
import com.tenderops.company.ProjectRepository;
import com.tenderops.config.TenderopsProperties;
import com.tenderops.document.StorageService;
import com.tenderops.security.UserRepository;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

/** M2 gate: seeded company matches MASTER_PROMPT section 5 exactly; seeding is idempotent. */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "tenderops.jwt.secret=ApiTestDummySecret0123456789abcdefghij0123")
@Transactional
class DemoDataSeederTest {

  @TempDir
  Path temp;

  @Autowired
  private UserRepository users;

  @Autowired
  private CompanyRepository companies;

  @Autowired
  private FinancialRecordRepository financials;

  @Autowired
  private ProjectRepository projects;

  @Autowired
  private CertificateRepository certificates;

  @Autowired
  private CompanyDocumentRepository documents;

  @Autowired
  private StorageService storage;

  @Autowired
  private TenderPdfGenerator generator;

  @Autowired
  private PasswordEncoder passwordEncoder;

  @Autowired
  private AuditService auditService;

  @Autowired
  private TenderopsProperties properties;

  @Autowired
  private Environment environment;

  private DemoDataSeeder seeder() {
    return new DemoDataSeeder(users, companies, financials, projects, certificates, documents,
        storage, generator, passwordEncoder, auditService, properties, environment,
        "seed-test-password-1");
  }

  @Test
  void seedsExactlySection5() {
    DemoDataSeeder.SeedResult result = seeder().seed(temp);
    assertThat(result.created()).isTrue();
    assertThat(users.findByEmail(DemoDataSeeder.DEMO_EMAIL)).isPresent();
    assertThat(users.findByEmail(DemoDataSeeder.DEMO_EMAIL).orElseThrow().getRole())
        .isEqualTo("ADMIN");

    Company company = companies.findById(result.companyId()).orElseThrow();
    assertThat(company.getName()).isEqualTo(DemoDataSeeder.DEMO_COMPANY);
    assertThat(company.getGstNumber()).hasSize(15);
    assertThat(company.isGstRegistered()).isTrue();
    assertThat(company.isMsmeRegistered()).isTrue();
    assertThat(company.isBlacklisted()).isFalse();
    assertThat(company.isDemo()).isTrue();
    assertThat(company.getDataVersion()).isGreaterThanOrEqualTo(1L);

    var records = financials.findByCompanyIdOrderByFyEndDateDesc(result.companyId());
    assertThat(records).hasSize(3);
    assertThat(records.get(0).getFyLabel()).isEqualTo("FY2025-26");
    assertThat(records.get(0).getTurnover()).isEqualByComparingTo(new BigDecimal("1120000000"));
    assertThat(records.get(0).getNetWorth()).isEqualByComparingTo(new BigDecimal("350000000"));
    assertThat(records.get(1).getTurnover()).isEqualByComparingTo(new BigDecimal("1050000000"));
    assertThat(records.get(2).getTurnover()).isEqualByComparingTo(new BigDecimal("980000000"));
    assertThat(records).allSatisfy(r -> {
      assertThat(r.isAudited()).isTrue();
      assertThat(r.getDocumentId()).isNotNull();
    });

    var projs = projects.findByCompanyIdOrderById(result.companyId());
    assertThat(projs).hasSize(2);
    assertThat(projs.get(0).getName()).isEqualTo("Smart City Integrated Command Centre");
    assertThat(projs.get(0).getContractValue()).isEqualByComparingTo(new BigDecimal("380000000"));
    assertThat(projs.get(0).getCompletionDate()).isEqualTo(LocalDate.of(2024, 6, 30));
    assertThat(projs.get(0).getDomainCategory()).isEqualTo("SMART_CITY");
    assertThat(projs.get(1).getContractValue()).isEqualByComparingTo(new BigDecimal("290000000"));
    assertThat(projs.get(1).getCompletionDate()).isEqualTo(LocalDate.of(2023, 3, 31));
    assertThat(projs.get(1).getDomainCategory()).isEqualTo("HIGHWAY");
    assertThat(projs).allSatisfy(p -> {
      assertThat(p.getStatus()).isEqualTo("COMPLETED");
      assertThat(p.getWorkOrderDocId()).isNotNull();
      assertThat(p.getCompletionCertDocId()).isNotNull();
    });

    var certs = certificates.findByCompanyIdOrderById(result.companyId());
    assertThat(certs).hasSize(3);
    assertThat(certs.stream().map(c -> c.getName()).toList())
        .containsExactlyInAnyOrder("ISO_9001", "ISO_27001", "CMMI");
    assertThat(certs.stream().filter(c -> c.getName().equals("ISO_9001")).findFirst()
        .orElseThrow().getExpiryDate()).isEqualTo(LocalDate.of(2027, 8, 31));
    assertThat(certs.stream().filter(c -> c.getName().equals("ISO_27001")).findFirst()
        .orElseThrow().getExpiryDate()).isEqualTo(LocalDate.of(2027, 5, 31));
    var cmmi = certs.stream().filter(c -> c.getName().equals("CMMI")).findFirst().orElseThrow();
    assertThat(cmmi.getLevelNumber()).isEqualTo(3);
    assertThat(cmmi.getExpiryDate()).isEqualTo(LocalDate.of(2026, 3, 31));
    assertThat(certs).allSatisfy(c -> assertThat(c.getDocumentId()).isNotNull());

    assertThat(documents.findByCompanyIdOrderById(result.companyId())).hasSize(10);
    assertThat(Files.exists(temp.resolve(TenderPdfGenerator.V1_FILE))).isTrue();
    assertThat(Files.exists(temp.resolve(TenderPdfGenerator.CORR_FILE))).isTrue();
    assertThat(Files.exists(temp.resolve(TenderPdfGenerator.VARIANT_B_FILE))).isTrue();
  }

  @Test
  void seedingIsIdempotent() {
    DemoDataSeeder.SeedResult first = seeder().seed(temp);
    assertThat(first.created()).isTrue();
    DemoDataSeeder.SeedResult second = seeder().seed(temp);
    assertThat(second.created()).isFalse();
    assertThat(financials.findByCompanyIdOrderByFyEndDateDesc(first.companyId())).hasSize(3);
    assertThat(projects.findByCompanyIdOrderById(first.companyId())).hasSize(2);
    assertThat(certificates.findByCompanyIdOrderById(first.companyId())).hasSize(3);
  }

  @Test
  void seederDisabledInTestProfile() {
    assertThat(seeder().shouldSeed()).isFalse();
  }
}
