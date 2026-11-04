package com.tenderops.web;

import com.tenderops.audit.AuditService;
import com.tenderops.company.Company;
import com.tenderops.company.CompanyRepository;
import com.tenderops.config.TenderopsProperties;
import com.tenderops.security.CurrentUser;
import com.tenderops.security.JwtService;
import com.tenderops.security.RateLimiter;
import com.tenderops.security.User;
import com.tenderops.security.UserRepository;
import com.tenderops.web.dto.AuthDtos.AuthResponse;
import com.tenderops.web.dto.AuthDtos.LoginRequest;
import com.tenderops.web.dto.AuthDtos.RegisterRequest;
import com.tenderops.web.dto.AuthDtos.UserDto;
import java.util.Map;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

  private final UserRepository users;
  private final CompanyRepository companies;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final RateLimiter rateLimiter;
  private final TenderopsProperties properties;
  private final AuditService auditService;

  public AuthService(UserRepository users, CompanyRepository companies,
      PasswordEncoder passwordEncoder, JwtService jwtService, RateLimiter rateLimiter,
      TenderopsProperties properties, AuditService auditService) {
    this.users = users;
    this.companies = companies;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
    this.rateLimiter = rateLimiter;
    this.properties = properties;
    this.auditService = auditService;
  }

  /** Register creates a NEW EMPTY company; the user becomes its BID_MANAGER. */
  @Transactional
  public AuthResponse register(RegisterRequest request, String clientIp) {
    if (!rateLimiter.tryConsume("register:" + clientIp,
        properties.rateLimit().registerPerMinute())) {
      throw new RateLimitExceededException();
    }
    String email = request.email().trim().toLowerCase();
    if (users.existsByEmail(email)) {
      throw new EmailTakenException();
    }
    Company company = companies.save(new Company(request.companyName().trim()));
    User user = users.save(new User(company.getId(), email,
        passwordEncoder.encode(request.password()), User.BID_MANAGER));
    auditService.record(company.getId(), user.getId(), "USER_REGISTERED", "USER", user.getId(),
        null, Map.of("email", email, "role", user.getRole(), "companyId", company.getId()));
    return tokenFor(user);
  }

  @Transactional
  public AuthResponse login(LoginRequest request, String clientIp) {
    String email = request.email().trim().toLowerCase();
    if (!rateLimiter.tryConsume("login:" + clientIp + "|" + email,
        properties.rateLimit().loginPerMinute())) {
      throw new RateLimitExceededException();
    }
    User user = users.findByEmail(email).orElseThrow(
        () -> new BadCredentialsException("Invalid email or password"));
    if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
      throw new BadCredentialsException("Invalid email or password");
    }
    auditService.record(user.getCompanyId(), user.getId(), "USER_LOGGED_IN", "USER", user.getId(),
        null, Map.of("email", email));
    return tokenFor(user);
  }

  public CurrentUser currentUser() {
    return CurrentUser.require();
  }

  private AuthResponse tokenFor(User user) {
    String token = jwtService.generate(user.getId(), user.getCompanyId(), user.getRole());
    return new AuthResponse(token, "Bearer", jwtService.expirySeconds(),
        new UserDto(user.getId(), user.getEmail(), user.getRole(), user.getCompanyId()));
  }
}
