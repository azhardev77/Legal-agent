package com.tenderops.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tenderops.config.TenderopsProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.env.MockEnvironment;

/**
 * TC-SEC-07: non-dev profiles fail to start without a JWT secret; dev-like profiles generate
 * an ephemeral one.
 */
class JwtStartupValidatorTest {

  private static TenderopsProperties propsWithSecret(String secret) {
    return new TenderopsProperties(new TenderopsProperties.Jwt(secret, 12), null, null, null,
        null, null, null, null, null, null, null, null);
  }

  private static MockEnvironment envWithProfile(String profile) {
    MockEnvironment env = new MockEnvironment();
    env.addActiveProfile(profile);
    return env;
  }

  @Test
  void postgresProfileWithoutSecretFails() {
    assertThatThrownBy(() -> new JwtConfig().jwtService(propsWithSecret(""),
        envWithProfile("postgres")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("JWT_SECRET");
  }

  @Test
  void prodProfileWithoutSecretFails() {
    assertThatThrownBy(() -> new JwtConfig().jwtService(propsWithSecret(null),
        envWithProfile("prod")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("JWT_SECRET");
  }

  @Test
  void postgresProfileWithSecretStarts() {
    JwtService service = new JwtConfig().jwtService(
        propsWithSecret("ApiTestDummySecret0123456789abcdefghij0123"), envWithProfile("postgres"));
    assertThat(service.generate(1L, 1L, User.ADMIN)).isNotBlank();
  }

  @Test
  void devProfileWithoutSecretGeneratesEphemeral() {
    JwtService service = new JwtConfig().jwtService(propsWithSecret(""), envWithProfile("dev"));
    assertThat(service.parse(service.generate(1L, 2L, User.VIEWER)).companyId()).isEqualTo(2L);
  }

  @Test
  void applicationContextFailsToStartOnPostgresWithoutSecret() {
    new ApplicationContextRunner()
        .withUserConfiguration(JwtConfig.class)
        .withBean(TenderopsProperties.class, () -> propsWithSecret(""))
        .withPropertyValues("spring.profiles.active=postgres")
        .run(context -> assertThat(context).hasFailed());
  }

  @Test
  void applicationContextStartsOnDevWithoutSecret() {
    new ApplicationContextRunner()
        .withUserConfiguration(JwtConfig.class)
        .withBean(TenderopsProperties.class, () -> propsWithSecret(""))
        .withPropertyValues("spring.profiles.active=dev")
        .run(context -> assertThat(context).hasSingleBean(JwtService.class));
  }
}
