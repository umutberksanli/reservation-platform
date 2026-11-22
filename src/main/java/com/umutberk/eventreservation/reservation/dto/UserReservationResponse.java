package com.umutberk.eventreservation.reservation.dto;

import java.time.Instant;

import com.umutberk.eventreservation.event.Event;
import com.umutberk.eventreservation.reservation.Reservation;
import com.umutberk.eventreservation.reservation.ReservationStatus;

/**
 * Kullanicinin kendi kayitlari listesindeki bir satir: rezervasyon/bekleme durumu + etkinlik ozeti.
 *
 * @param waitlistPosition yalnizca WAITLISTED iken dolu: 1 = siradaki
 */
public record UserReservationResponse(
        Long id,
        ReservationStatus status,
        Integer waitlistNumber,
        Integer waitlistPosition,
        Instant createdAt,
        Instant promotedAt,
        Instant cancelledAt,
        EventSummary event) {

    public record EventSummary(Long id, String name, Instant startsAt) {
    }

    /** Etkinlik "join fetch" ile yuklenmis olmalidir (ek sorgu yapilmaz). */
    public static UserReservationResponse from(Reservation reservation, Integer waitlistPosition) {
        Event event = reservation.getEvent();
        return new UserReservationResponse(
                reservation.getId(),
                reservation.getStatus(),
                reservation.getWaitlistNumber(),
                waitlistPosition,
                reservation.getCreatedAt(),
                reservation.getPromotedAt(),
                reservation.getCancelledAt(),
                new EventSummary(event.getId(), event.getName(), event.getStartsAt()));
    }
}
