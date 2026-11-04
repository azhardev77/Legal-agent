package com.tenderops.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

  private AuthDtos() {}

  public record RegisterRequest(
      @Email @NotBlank @Size(max = 320) String email,
      @NotBlank @Size(min = 8, max = 72) String password,
      @NotBlank @Size(max = 255) String companyName) {}

  public record LoginRequest(
      @NotBlank @Size(max = 320) String email,
      @NotBlank @Size(max = 72) String password) {}

  public record UserDto(long id, String email, String role, long companyId) {}

  public record AuthResponse(String accessToken, String tokenType, long expiresIn, UserDto user) {}

  public record RoleChangeRequest(@NotBlank String role) {}
}
