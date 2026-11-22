package com.umutberk.eventreservation.security;

import java.util.Locale;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.umutberk.eventreservation.security.dto.LoginRequest;
import com.umutberk.eventreservation.security.dto.TokenResponse;
import com.umutberk.eventreservation.user.AppUser;
import com.umutberk.eventreservation.user.AppUserRepository;

/** E-posta + sifre ile giris; basariliysa JWT verir. */
@Service
public class AuthService {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService tokenService;
    /** Kullanici yoksa da ayni maliyette bir BCrypt karsilastirmasi yapilir (zamanlamadan e-posta tahmini zorlasir). */
    private final String dummyHash;

    public AuthService(AppUserRepository appUserRepository, PasswordEncoder passwordEncoder,
            JwtTokenService tokenService) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.dummyHash = passwordEncoder.encode("dummy-password-for-timing");
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        AppUser user = appUserRepository.findByEmail(request.email().toLowerCase(Locale.ROOT)).orElse(null);
        String hash = user != null && user.getPasswordHash() != null ? user.getPasswordHash() : dummyHash;
        boolean matches = passwordEncoder.matches(request.password(), hash);
        if (user == null || user.getPasswordHash() == null || !matches) {
            throw new InvalidCredentialsException();
        }
        return tokenService.issue(user);
    }
}
