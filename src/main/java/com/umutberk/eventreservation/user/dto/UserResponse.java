package com.umutberk.eventreservation.user.dto;

import java.time.Instant;

import com.umutberk.eventreservation.user.AppUser;
import com.umutberk.eventreservation.user.UserRole;

/** API'nin dondurdugu kullanici gorunumu. Entity (ve sifre ozeti) disariya dogrudan acilmaz. */
public record UserResponse(
        Long id,
        String email,
        String fullName,
        UserRole role,
        Instant createdAt) {

    public static UserResponse from(AppUser user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getRole(), user.getCreatedAt());
    }
}
