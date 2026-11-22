package com.umutberk.eventreservation.user.dto;

import java.time.Instant;

import com.umutberk.eventreservation.user.AppUser;

/** API'nin dondurdugu kullanici gorunumu. Entity disariya dogrudan acilmaz. */
public record UserResponse(
        Long id,
        String email,
        String fullName,
        Instant createdAt) {

    public static UserResponse from(AppUser user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getCreatedAt());
    }
}
