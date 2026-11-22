package com.umutberk.eventreservation.security;

import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.umutberk.eventreservation.user.AppUser;
import com.umutberk.eventreservation.user.AppUserRepository;
import com.umutberk.eventreservation.user.UserRole;

/**
 * Ilk admin: ADMIN_EMAIL ve ADMIN_PASSWORD ikisi de tanimliysa acilista calisir.
 * Kullanici yoksa ADMIN olarak olusturur; varsa yalnizca rolunu ADMIN yapar (sifresine dokunmaz).
 * Ikisi de bossa hicbir sey yapmaz. Sifre loglanmaz.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String password;

    public AdminBootstrap(AppUserRepository appUserRepository, PasswordEncoder passwordEncoder,
            @Value("${app.bootstrap-admin.email:}") String email,
            @Value("${app.bootstrap-admin.password:}") String password) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.email = email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
        this.password = password == null ? "" : password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (email.isEmpty() && password.isEmpty()) {
            return;
        }
        if (email.isEmpty() || password.length() < 8 || password.length() > 72) {
            throw new IllegalStateException(
                    "ADMIN_EMAIL and ADMIN_PASSWORD (8-72 characters) must both be set to create the first admin");
        }
        appUserRepository.findByEmail(email).ifPresentOrElse(existing -> {
            if (existing.getRole() != UserRole.ADMIN) {
                existing.promoteToAdmin();
                log.info("Bootstrap admin: existing user {} promoted to ADMIN", existing.getId());
            }
        }, () -> {
            AppUser admin = appUserRepository.save(
                    new AppUser(email, "Administrator", passwordEncoder.encode(password), UserRole.ADMIN));
            log.info("Bootstrap admin: created ADMIN user {}", admin.getId());
        });
    }
}
