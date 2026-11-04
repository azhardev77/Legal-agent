package com.tenderops.web;

import com.tenderops.web.dto.AuthDtos.AuthResponse;
import com.tenderops.web.dto.AuthDtos.LoginRequest;
import com.tenderops.web.dto.AuthDtos.RegisterRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

  private final AuthService authService;

  public AuthController(AuthService authService) {
    this.authService = authService;
  }

  @PostMapping("/register")
  @ResponseStatus(HttpStatus.CREATED)
  public AuthResponse register(@Valid @RequestBody RegisterRequest request,
      HttpServletRequest httpRequest) {
    return authService.register(request, httpRequest.getRemoteAddr());
  }

  @PostMapping("/login")
  public AuthResponse login(@Valid @RequestBody LoginRequest request,
      HttpServletRequest httpRequest) {
    return authService.login(request, httpRequest.getRemoteAddr());
  }
}
