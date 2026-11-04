package com.tenderops.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.tenderops.audit.AuditLogRepository;
import com.tenderops.company.CompanyRepository;
import com.tenderops.security.JwtService;
import com.tenderops.security.User;
import com.tenderops.security.UserRepository;
import com.tenderops.web.dto.AuthDtos.AuthResponse;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * M1 auth API tests (TC-SEC-02/03/04/05 auth parts; approve-endpoint parts land with M6).
 * Dummy JWT secret below is test-only and clearly named as such; it is not a real secret.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = "tenderops.jwt.secret=ApiTestDummySecret0123456789abcdefghij0123")
class AuthApiTest {

  static final String TEST_SECRET = "ApiTestDummySecret0123456789abcdefghij0123";

  @Autowired
  private TestRestTemplate rest;

  @Autowired
  private UserRepository users;

  @Autowired
  private CompanyRepository companies;

  @Autowired
  private AuditLogRepository audits;

  private static String freshEmail(String prefix) {
    return prefix + UUID.randomUUID() + "@x.local";
  }

  private AuthResponse register(String email, String password, String company) {
    Map<String, String> body = Map.of("email", email, "password", password, "companyName", company);
    ResponseEntity<AuthResponse> response =
        rest.postForEntity("/api/v1/auth/register", body, AuthResponse.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody()).isNotNull();
    return response.getBody();
  }

  private static HttpEntity<Map<String, String>> json(Map<String, String> body, String token) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    if (token != null) {
      headers.setBearerAuth(token);
    }
    return new HttpEntity<>(body, headers);
  }

  @Test
  void registerCreatesCompanyUserAndToken() {
    String email = freshEmail("reg-");
    AuthResponse response = register(email, "password-123", "Reg Co");
    assertThat(response.accessToken()).isNotBlank();
    assertThat(response.tokenType()).isEqualTo("Bearer");
    assertThat(response.user().email()).isEqualTo(email);
    assertThat(response.user().role()).isEqualTo(User.BID_MANAGER);
    assertThat(users.findByEmail(email)).isPresent();
    assertThat(companies.findById(response.user().companyId())).isPresent();
    assertThat(audits.countByCompanyId(response.user().companyId())).isGreaterThanOrEqualTo(1L);
  }

  @Test
  void registerDuplicateEmailIs409Problem() {
    String email = freshEmail("dup-");
    register(email, "password-123", "Dup Co");
    Map<String, String> body = Map.of("email", email, "password", "password-123",
        "companyName", "Dup Co 2");
    ResponseEntity<Map> response = rest.postForEntity("/api/v1/auth/register", body, Map.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getBody()).containsEntry("title", "Conflict");
    assertThat(response.getBody()).doesNotContainKeys("stackTrace", "trace");
  }

  @Test
  void registerInvalidBodyIs400Problem() {
    Map<String, String> body = Map.of("email", "not-an-email", "password", "short",
        "companyName", "");
    ResponseEntity<Map> response = rest.postForEntity("/api/v1/auth/register", body, Map.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).containsKey("errors");
    assertThat(response.getBody()).doesNotContainKeys("stackTrace", "trace");
  }

  @Test
  void loginOkThenAuthenticatedCallSucceeds() {
    String email = freshEmail("login-");
    register(email, "password-123", "Login Co");
    Map<String, String> body = Map.of("email", email, "password", "password-123");
    ResponseEntity<AuthResponse> response =
        rest.postForEntity("/api/v1/auth/login", body, AuthResponse.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().accessToken()).isNotBlank();
  }

  @Test
  void loginWrongPasswordAndUnknownUserAreUniform401() {
    String email = freshEmail("wrong-");
    register(email, "password-123", "Wrong Co");
    ResponseEntity<Map> wrongPassword = rest.postForEntity("/api/v1/auth/login",
        Map.of("email", email, "password", "nope-nope-nope"), Map.class);
    assertThat(wrongPassword.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(wrongPassword.getBody()).containsEntry("detail", "Invalid email or password");
    ResponseEntity<Map> unknown = rest.postForEntity("/api/v1/auth/login",
        Map.of("email", freshEmail("nobody-"), "password", "whatever-123"), Map.class);
    assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(unknown.getBody()).containsEntry("detail", "Invalid email or password");
  }

  @Test
  void missingTokenIs401() {
    ResponseEntity<Map> response =
        rest.getForEntity("/api/v1/users", Map.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(response.getBody()).containsEntry("title", "Unauthorized");
  }

  @Test
  void tamperedTokenIs401() {
    String email = freshEmail("tamper-");
    String token = register(email, "password-123", "Tamper Co").accessToken();
    char last = token.charAt(token.length() - 1);
    String tampered = token.substring(0, token.length() - 1) + (last == 'a' ? 'b' : 'a');
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(tampered);
    ResponseEntity<Map> response =
        rest.exchange("/api/v1/users", HttpMethod.GET, new HttpEntity<>(headers), Map.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void expiredTokenIs401() {
    String email = freshEmail("expired-");
    AuthResponse registered = register(email, "password-123", "Expired Co");
    String expired = new JwtService(TEST_SECRET, -1).generate(registered.user().id(),
        registered.user().companyId(), registered.user().role());
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(expired);
    ResponseEntity<Map> response =
        rest.exchange("/api/v1/users", HttpMethod.GET, new HttpEntity<>(headers), Map.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void loginRateLimitTriggers429() {
    String email = freshEmail("ratelimit-");
    register(email, "password-123", "Rate Co");
    Map<String, String> wrong = Map.of("email", email, "password", "wrong-wrong-wrong");
    for (int i = 0; i < 5; i++) {
      ResponseEntity<Map> attempt = rest.postForEntity("/api/v1/auth/login", wrong, Map.class);
      assertThat(attempt.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
    ResponseEntity<Map> limited = rest.postForEntity("/api/v1/auth/login",
        Map.of("email", email, "password", "password-123"), Map.class);
    assertThat(limited.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    assertThat(limited.getHeaders().getFirst("Retry-After")).isEqualTo("60");
  }
}
