package com.umutberk.eventreservation.reservation.dto;

import java.time.Instant;

import com.umutberk.eventreservation.reservation.Reservation;
import com.umutberk.eventreservation.reservation.ReservationStatus;

/**
 * API'nin dondurdugu rezervasyon gorunumu. Entity disariya dogrudan acilmaz.
 *
 * @param waitlistNumber   kalici sira numarasi (dogrudan onaylananlarda null; terfiden sonra da gorunur)
 * @param waitlistPosition yalnizca WAITLISTED iken dolu: 1 = siradaki
 */
public record ReservationResponse(
        Long id,
        Long eventId,
        Long userId,
        ReservationStatus status,
        Integer waitlistNumber,
        Integer waitlistPosition,
        Instant createdAt,
        Instant promotedAt,
        Instant cancelledAt) {

    /** getEvent().getId() / getUser().getId() lazy proxy'yi yuklemez; ek sorgu yapilmaz. */
    public static ReservationResponse from(Reservation reservation, Integer waitlistPosition) {
        return new ReservationResponse(
                reservation.getId(),
                reservation.getEvent().getId(),
                reservation.getUser().getId(),
                reservation.getStatus(),
                reservation.getWaitlistNumber(),
                waitlistPosition,
                reservation.getCreatedAt(),
                reservation.getPromotedAt(),
                reservation.getCancelledAt());
    }
}
