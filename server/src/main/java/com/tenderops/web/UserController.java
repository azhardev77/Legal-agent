package com.tenderops.web;

import com.tenderops.audit.AuditService;
import com.tenderops.security.CurrentUser;
import com.tenderops.security.User;
import com.tenderops.security.UserRepository;
import com.tenderops.web.dto.AuthDtos.RoleChangeRequest;
import com.tenderops.web.dto.AuthDtos.UserDto;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

  private static final Set<String> ROLES = Set.of(User.ADMIN, User.BID_MANAGER, User.VIEWER);

  private final UserRepository users;
  private final AuditService auditService;

  public UserController(UserRepository users, AuditService auditService) {
    this.users = users;
    this.auditService = auditService;
  }

  /** ADMIN-only: list users of the caller's company. */
  @GetMapping
  public List<UserDto> list() {
    CurrentUser current = CurrentUser.require();
    if (!current.isAdmin()) {
      throw new AccessDeniedException("ADMIN role required");
    }
    return users.findByCompanyIdOrderById(current.companyId()).stream()
        .map(u -> new UserDto(u.getId(), u.getEmail(), u.getRole(), u.getCompanyId()))
        .toList();
  }

  /** ADMIN-only: change a role within the caller's company (cross-company -> 404). */
  @PatchMapping("/{id}/role")
  public UserDto changeRole(@PathVariable long id,
      @Valid @RequestBody RoleChangeRequest request) {
    CurrentUser current = CurrentUser.require();
    if (!current.isAdmin()) {
      throw new AccessDeniedException("ADMIN role required");
    }
    String role = request.role().trim().toUpperCase();
    if (!ROLES.contains(role)) {
      throw new IllegalArgumentException("Unknown role: " + request.role());
    }
    User user = users.findById(id).orElseThrow(() -> new NotFoundException("User not found"));
    current.requireCompany(user.getCompanyId());
    if (user.getId() == current.userId()) {
      throw new IllegalArgumentException("You cannot change your own role");
    }
    String before = user.getRole();
    user.setRole(role);
    users.save(user);
    auditService.record(user.getCompanyId(), current.userId(), "USER_ROLE_CHANGED", "USER",
        user.getId(), Map.of("role", before), Map.of("role", role));
    return new UserDto(user.getId(), user.getEmail(), user.getRole(), user.getCompanyId());
  }
}
