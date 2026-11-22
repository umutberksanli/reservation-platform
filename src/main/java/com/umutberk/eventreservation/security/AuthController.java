package com.umutberk.eventreservation.security;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.umutberk.eventreservation.security.dto.LoginRequest;
import com.umutberk.eventreservation.security.dto.TokenResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** 200 + JWT; yanlis e-posta/sifre 401 INVALID_CREDENTIALS; eksik alan 400. */
    @PostMapping("/token")
    public TokenResponse token(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }
}
