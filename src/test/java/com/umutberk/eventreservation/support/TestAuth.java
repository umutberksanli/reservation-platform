package com.umutberk.eventreservation.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import java.util.List;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * MockMvc istekleri icin kimlik: gercek uygulamadaki JWT ile ayni bicimde
 * (sub = kullanici ID'si, roles = [USER|ADMIN]). Imza dogrulamasi AuthApiTest'te gercek token'la sinanir.
 */
public final class TestAuth {

    private TestAuth() {
    }

    public static RequestPostProcessor asUser(long userId) {
        return as(userId, "USER");
    }

    public static RequestPostProcessor asAdmin(long userId) {
        return as(userId, "ADMIN");
    }

    private static RequestPostProcessor as(long userId, String role) {
        return jwt()
                .jwt(token -> token.subject(String.valueOf(userId)).claim("roles", List.of(role)))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
