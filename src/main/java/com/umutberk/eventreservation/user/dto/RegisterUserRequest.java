package com.umutberk.eventreservation.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * POST /api/users istek govdesi. Sinirlar V1 semasindaki sutun boylariyla ayni.
 * E-posta ve ad icin bas/son bosluklar dogrulamadan ONCE kirpilir; sifre oldugu gibi kalir
 * (bosluk da sifrenin parcasidir). 72 sinir BCrypt'ten gelir (daha uzun kismi yok sayardi).
 */
public record RegisterUserRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(max = 100) String fullName,
        @NotBlank @Size(min = 8, max = 72) String password) {

    public RegisterUserRequest {
        email = email == null ? null : email.strip();
        fullName = fullName == null ? null : fullName.strip();
    }

    /** Sifre loglara/hata mesajlarina dusmesin. */
    @Override
    public String toString() {
        return "RegisterUserRequest[email=" + email + ", fullName=" + fullName + ", password=***]";
    }
}
