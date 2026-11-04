package com.tenderops.security;

import com.tenderops.config.TenderopsProperties;
import java.security.SecureRandom;
import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
public class JwtConfig {

  private static final Logger log = LoggerFactory.getLogger(JwtConfig.class);

  @Bean
  public JwtService jwtService(TenderopsProperties properties, Environment environment) {
    String secret = properties.jwt() == null ? null : properties.jwt().secret();
    int expiryHours = properties.jwt() == null ? 12 : properties.jwt().expiryHours();
    boolean nonDev = Arrays.stream(environment.getActiveProfiles())
        .anyMatch(p -> p.equals("postgres") || p.equals("prod"));
    if (secret == null || secret.isBlank()) {
      if (nonDev) {
        // R9 / TC-SEC-07: non-dev profiles fail to start without a JWT secret.
        throw new IllegalStateException(
            "tenderops.jwt.secret is required in non-dev profiles (set JWT_SECRET)");
      }
      secret = ephemeralSecret();
      log.warn("JWT_SECRET is empty: generated an ephemeral dev-only signing secret (tokens will not "
          + "survive restart; set JWT_SECRET for stable tokens)");
    }
    return new JwtService(secret, expiryHours);
  }

  private static String ephemeralSecret() {
    SecureRandom random = new SecureRandom();
    String alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    StringBuilder sb = new StringBuilder(48);
    for (int i = 0; i < 48; i++) {
      sb.append(alphabet.charAt(random.nextInt(alphabet.length())));
    }
    return sb.toString();
  }
}
