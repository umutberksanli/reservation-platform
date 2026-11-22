package com.umutberk.eventreservation.reservation.dto;

import java.time.Instant;

import com.umutberk.eventreservation.reservation.Reservation;
import com.umutberk.eventreservation.reservation.ReservationStatus;

/** API'nin dondurdugu rezervasyon gorunumu. Entity disariya dogrudan acilmaz. */
public record ReservationResponse(
        Long id,
        Long eventId,
        Long userId,
        ReservationStatus status,
        Instant createdAt) {

    /** getEvent().getId() / getUser().getId() lazy proxy'yi yuklemez; ek sorgu yapilmaz. */
    public static ReservationResponse from(Reservation reservation) {
        return new ReservationResponse(
                reservation.getId(),
                reservation.getEvent().getId(),
                reservation.getUser().getId(),
                reservation.getStatus(),
                reservation.getCreatedAt());
    }
}
