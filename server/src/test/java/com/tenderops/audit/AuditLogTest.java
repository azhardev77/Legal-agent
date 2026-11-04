package com.tenderops.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenderops.company.Company;
import com.tenderops.company.CompanyRepository;
import java.lang.reflect.Method;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

/**
 * TC-SEC-06: the audit log is insert-only (no update/delete path) and records before/after JSON.
 */
@DataJpaTest
class AuditLogTest {

  @Autowired
  private AuditLogRepository repository;

  @Autowired
  private CompanyRepository companies;

  @Test
  void recordPersistsBeforeAndAfterJson() {
    Company company = companies.save(new Company("Audit Co"));
    AuditService service = new AuditService(repository, new ObjectMapper());
    AuditLog saved = service.record(company.getId(), null, "USER_ROLE_CHANGED", "USER", 3L,
        Map.of("role", "VIEWER"), Map.of("role", "ADMIN"));
    assertThat(saved.getId()).isNotNull();
    assertThat(saved.getBeforeJson()).contains("VIEWER");
    assertThat(saved.getAfterJson()).contains("ADMIN");
    assertThat(repository.countByCompanyId(company.getId())).isEqualTo(1L);
    assertThat(repository.findByCompanyIdOrderByIdDesc(company.getId(),
        org.springframework.data.domain.Pageable.unpaged())).hasSize(1);
  }

  @Test
  void repositoryExposesNoUpdateOrDelete() {
    for (Method method : AuditLogRepository.class.getMethods()) {
      String name = method.getName().toLowerCase();
      assertThat(name)
          .doesNotStartWith("delete")
          .doesNotStartWith("remove")
          .doesNotStartWith("update");
    }
  }

  @Test
  void serviceExposesNoUpdateOrDelete() {
    for (Method method : AuditService.class.getDeclaredMethods()) {
      String name = method.getName().toLowerCase();
      if (method.isSynthetic()) {
        continue;
      }
      assertThat(name)
          .doesNotStartWith("delete")
          .doesNotStartWith("remove")
          .doesNotStartWith("update");
    }
  }
}
