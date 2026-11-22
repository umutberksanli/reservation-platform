package com.umutberk.eventreservation.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

/**
 * Istegi yapan kisi: JWT'nin "sub" alanindaki kullanici ID'si ve ADMIN olup olmadigi.
 * Sahiplik kontrolleri bu nesne uzerinden yapilir.
 */
public record Actor(long userId, boolean admin) {

    public static Actor from(Authentication authentication) {
        boolean admin = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);
        return new Actor(Long.parseLong(authentication.getName()), admin);
    }

    /** Kaynak bu kullaniciya ait degilse ve kisi ADMIN degilse 403 FORBIDDEN. */
    public void requireSelfOrAdmin(long ownerUserId) {
        if (!admin && ownerUserId != userId) {
            throw new ForbiddenException("You can only access your own data");
        }
    }
}
