package com.umutberk.eventreservation.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** POST /api/auth/token istek govdesi. */
public record LoginRequest(
        @NotBlank @Size(max = 254) String email,
        @NotBlank @Size(max = 72) String password) {

    public LoginRequest {
        email = email == null ? null : email.strip();
    }

    /** Sifre loglara/hata mesajlarina dusmesin. */
    @Override
    public String toString() {
        return "LoginRequest[email=" + email + ", password=***]";
    }
}
