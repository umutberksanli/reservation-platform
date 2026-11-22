package com.umutberk.eventreservation.security.dto;

/**
 * POST /api/auth/token cevabi.
 *
 * @param accessToken sonraki isteklerde "Authorization: Bearer &lt;accessToken&gt;" olarak gonderilir
 * @param expiresIn   saniye cinsinden omur
 */
public record TokenResponse(String accessToken, String tokenType, long expiresIn) {
}
