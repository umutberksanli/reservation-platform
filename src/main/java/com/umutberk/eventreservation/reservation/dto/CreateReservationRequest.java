package com.umutberk.eventreservation.reservation.dto;

import jakarta.validation.constraints.Positive;

/**
 * POST /api/events/{eventId}/reservations istek govdesi (istege bagli).
 * Govde yoksa ya da userId bossa rezervasyon giris yapmis kullanici icindir.
 * Baskasi adina (farkli userId) yalnizca ADMIN rezervasyon yapabilir.
 */
public record CreateReservationRequest(@Positive Long userId) {
}
