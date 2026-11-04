package com.tenderops.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenderops.audit.AuditLogRepository;
import com.tenderops.company.Company;
import com.tenderops.company.CompanyRepository;
import com.tenderops.security.JwtService;
import com.tenderops.security.User;
import com.tenderops.security.UserRepository;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** TC-SEC-02 (user-admin parts): ADMIN gating, self-guard, cross-company 404. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "tenderops.jwt.secret=ApiTestDummySecret0123456789abcdefghij0123")
@Transactional
class UserApiTest {

  @Autowired
  private MockMvc mvc;

  @Autowired
  private UserRepository users;

  @Autowired
  private CompanyRepository companies;

  @Autowired
  private AuditLogRepository audits;

  @Autowired
  private JwtService jwtService;

  @Autowired
  private PasswordEncoder passwordEncoder;

  @Autowired
  private ObjectMapper objectMapper;

  private User adminA;
  private User viewerA;
  private User userB;
  private String adminTokenA;

  private User createUser(Company company, String role) {
    return users.save(new User(company.getId(),
        "u-" + UUID.randomUUID() + "@x.local", passwordEncoder.encode("password-123"), role));
  }

  @BeforeEach
  void setUp() {
    Company companyA = companies.save(new Company("Company A"));
    Company companyB = companies.save(new Company("Company B"));
    adminA = createUser(companyA, User.ADMIN);
    viewerA = createUser(companyA, User.VIEWER);
    userB = createUser(companyB, User.BID_MANAGER);
    adminTokenA = jwtService.generate(adminA.getId(), adminA.getCompanyId(), adminA.getRole());
  }

  private String tokenFor(User user) {
    return jwtService.generate(user.getId(), user.getCompanyId(), user.getRole());
  }

  @Test
  void adminListsOnlyOwnCompanyUsers() throws Exception {
    mvc.perform(get("/api/v1/users").header("Authorization", "Bearer " + adminTokenA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.email == '" + userB.getEmail() + "')]").doesNotExist())
        .andExpect(jsonPath("$[?(@.email == '" + adminA.getEmail() + "')]").exists());
  }

  @Test
  void viewerCannotListUsers() throws Exception {
    mvc.perform(get("/api/v1/users").header("Authorization", "Bearer " + tokenFor(viewerA)))
        .andExpect(status().isForbidden());
  }

  @Test
  void missingTokenIs401() throws Exception {
    mvc.perform(get("/api/v1/users")).andExpect(status().isUnauthorized());
  }

  @Test
  void adminChangesRoleAndAuditRecorded() throws Exception {
    long before = audits.countByCompanyId(adminA.getCompanyId());
    mvc.perform(patch("/api/v1/users/{id}/role", viewerA.getId())
            .header("Authorization", "Bearer " + adminTokenA)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of("role", "BID_MANAGER"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("BID_MANAGER"));
    assertThat(users.findById(viewerA.getId())).get().extracting(User::getRole)
        .isEqualTo(User.BID_MANAGER);
    assertThat(audits.countByCompanyId(adminA.getCompanyId())).isEqualTo(before + 1);
  }

  @Test
  void viewerCannotChangeRole() throws Exception {
    mvc.perform(patch("/api/v1/users/{id}/role", adminA.getId())
            .header("Authorization", "Bearer " + tokenFor(viewerA))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of("role", "VIEWER"))))
        .andExpect(status().isForbidden());
  }

  @Test
  void adminCannotChangeOwnRole() throws Exception {
    mvc.perform(patch("/api/v1/users/{id}/role", adminA.getId())
            .header("Authorization", "Bearer " + adminTokenA)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of("role", "VIEWER"))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void crossCompanyRoleChangeIs404() throws Exception {
    mvc.perform(patch("/api/v1/users/{id}/role", userB.getId())
            .header("Authorization", "Bearer " + adminTokenA)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of("role", "VIEWER"))))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.title").value("Not found"))
        .andExpect(jsonPath("$.stackTrace").doesNotExist());
  }

  @Test
  void unknownRoleIs400AndUnknownUserIs404() throws Exception {
    mvc.perform(patch("/api/v1/users/{id}/role", viewerA.getId())
            .header("Authorization", "Bearer " + adminTokenA)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of("role", "SUPERUSER"))))
        .andExpect(status().isBadRequest());
    mvc.perform(patch("/api/v1/users/{id}/role", 999999999L)
            .header("Authorization", "Bearer " + adminTokenA)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of("role", "VIEWER"))))
        .andExpect(status().isNotFound());
  }
}
