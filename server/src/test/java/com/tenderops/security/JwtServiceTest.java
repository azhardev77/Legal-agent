package com.tenderops.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

/** JWT issue/validate units (TC-SEC-03 API proof in AuthApiTest). */
class JwtServiceTest {

  private static final String DUMMY_SECRET = "UnitTestDummySecret0123456789abcdefghij01";

  @Test
  void roundTripPreservesClaims() {
    JwtService service = new JwtService(DUMMY_SECRET, 12);
    String token = service.generate(7L, 9L, User.ADMIN);
    CurrentUser parsed = service.parse(token);
    assertThat(parsed.userId()).isEqualTo(7L);
    assertThat(parsed.companyId()).isEqualTo(9L);
    assertThat(parsed.role()).isEqualTo(User.ADMIN);
    assertThat(service.expirySeconds()).isEqualTo(12 * 3600L);
  }

  @Test
  void tamperedTokenRejected() {
    JwtService service = new JwtService(DUMMY_SECRET, 12);
    String token = service.generate(1L, 1L, User.VIEWER);
    // Flip the FIRST signature char: all 6 bits significant, so the tamper always lands.
    // (Flipping the LAST char is a silent no-op whenever only dropped padding bits change.)
    int sigStart = token.lastIndexOf('.') + 1;
    char first = token.charAt(sigStart);
    String tampered = token.substring(0, sigStart) + (first == 'a' ? 'b' : 'a')
        + token.substring(sigStart + 1);
    assertThatThrownBy(() -> service.parse(tampered)).isInstanceOf(JwtException.class);
  }

  @Test
  void wrongSecretRejected() {
    JwtService issuer = new JwtService(DUMMY_SECRET, 12);
    JwtService other = new JwtService("AnotherDummySecret0123456789abcdefghij02", 12);
    String token = issuer.generate(1L, 1L, User.VIEWER);
    assertThatThrownBy(() -> other.parse(token)).isInstanceOf(JwtException.class);
  }

  @Test
  void expiredTokenRejected() {
    JwtService issuer = new JwtService(DUMMY_SECRET, -1);
    JwtService parser = new JwtService(DUMMY_SECRET, 12);
    String token = issuer.generate(1L, 1L, User.VIEWER);
    assertThatThrownBy(() -> parser.parse(token)).isInstanceOf(ExpiredJwtException.class);
  }

  @Test
  void shortSecretRejectedAtConstruction() {
    assertThatThrownBy(() -> new JwtService("too-short", 12))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
