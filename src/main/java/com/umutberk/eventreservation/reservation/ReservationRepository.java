package com.umutberk.eventreservation.reservation;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    /** Etkinlikte bu durumdaki rezervasyon sayisi (idx_reservation_event_status indeksini kullanir). */
    long countByEventIdAndStatus(Long eventId, ReservationStatus status);

    boolean existsByEventIdAndUserIdAndStatus(Long eventId, Long userId, ReservationStatus status);
}
