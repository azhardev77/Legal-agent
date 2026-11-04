package com.tenderops.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Authenticated principal: user id, company id (scoping), role. */
public record CurrentUser(long userId, long companyId, String role) {

  public static CurrentUser require() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !(auth.getPrincipal() instanceof CurrentUser current)) {
      throw new AccessDeniedException("Not authenticated");
    }
    return current;
  }

  /** Company scoping: callers can only touch their own company's resources. */
  public void requireCompany(long resourceCompanyId) {
    if (resourceCompanyId != companyId) {
      throw new ResourceNotVisibleException();
    }
  }

  public boolean isAdmin() {
    return "ADMIN".equals(role);
  }

  public boolean canApprove() {
    return "ADMIN".equals(role) || "BID_MANAGER".equals(role);
  }
}
