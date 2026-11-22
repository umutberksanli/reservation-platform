package com.umutberk.eventreservation.event.dto;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * POST /api/events istek govdesi. Kurallar V1 semasindaki kisitlarla ayni,
 * boylece hatali veri veritabanina gitmeden 400 Bad Request ile reddedilir.
 *
 * @param startsAt UTC zaman, ornek: "2027-01-15T19:00:00Z"
 */
public record CreateEventRequest(
        @NotBlank @Size(max = 200) String name,
        @NotNull Instant startsAt,
        @NotNull @Positive Integer capacity) {
}
