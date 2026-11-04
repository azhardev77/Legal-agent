package com.tenderops.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;

/** Issues and validates HS256 JWTs. Claims: sub=userId, companyId, role, exp. */
public class JwtService {

  private final SecretKey key;
  private final long expirySeconds;

  public JwtService(String secret, int expiryHours) {
    byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
    if (bytes.length < 32) {
      throw new IllegalArgumentException("JWT secret must be at least 32 bytes (256 bits)");
    }
    this.key = Keys.hmacShaKeyFor(bytes);
    this.expirySeconds = expiryHours * 3600L;
  }

  public String generate(long userId, long companyId, String role) {
    Instant now = Instant.now();
    return Jwts.builder()
        .subject(Long.toString(userId))
        .claim("companyId", companyId)
        .claim("role", role)
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plusSeconds(expirySeconds)))
        .signWith(key)
        .compact();
  }

  /** Parses and validates; throws JwtException (expired, tampered, malformed) on failure. */
  public CurrentUser parse(String token) {
    Jws<Claims> jws = Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
    Claims claims = jws.getPayload();
    long userId = Long.parseLong(claims.getSubject());
    long companyId = claims.get("companyId", Number.class).longValue();
    String role = claims.get("role", String.class);
    if (role == null || role.isBlank()) {
      throw new JwtException("JWT is missing the role claim");
    }
    return new CurrentUser(userId, companyId, role);
  }

  public long expirySeconds() {
    return expirySeconds;
  }
}
