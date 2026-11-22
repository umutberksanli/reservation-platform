package com.umutberk.eventreservation.reservation.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** POST /api/events/{eventId}/reservations istek govdesi. */
public record CreateReservationRequest(@NotNull @Positive Long userId) {
}
