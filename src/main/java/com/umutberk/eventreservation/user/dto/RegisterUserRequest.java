package com.umutberk.eventreservation.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * POST /api/users istek govdesi. Sinirlar V1 semasindaki sutun boylariyla ayni.
 * Bas/son bosluklar dogrulamadan ONCE kirpilir; boylece "  ayse@x.com " gecerli sayilir.
 */
public record RegisterUserRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(max = 100) String fullName) {

    public RegisterUserRequest {
        email = email == null ? null : email.strip();
        fullName = fullName == null ? null : fullName.strip();
    }
}
