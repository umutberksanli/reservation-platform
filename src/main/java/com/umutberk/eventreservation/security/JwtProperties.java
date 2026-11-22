package com.umutberk.eventreservation.security;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * app.security.jwt.* ayarlari.
 *
 * @param secret HS256 imza anahtari (JWT_SECRET, en az 32 karakter); bossa gelistirme icin rastgele uretilir
 * @param issuer token'in "iss" alani; dogrulamada da kontrol edilir
 * @param ttl    token omru
 */
@ConfigurationProperties(prefix = "app.security.jwt")
public record JwtProperties(String secret, String issuer, Duration ttl) {

    public JwtProperties {
        issuer = issuer == null || issuer.isBlank() ? "reservation-platform" : issuer;
        ttl = ttl == null ? Duration.ofHours(1) : ttl;
    }
}
