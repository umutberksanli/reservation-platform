package com.umutberk.eventreservation.reservation;

import java.util.Collection;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    /** Etkinlikte bu durumdaki kayit sayisi (idx_reservation_event_status_waitlist). */
    long countByEventIdAndStatus(Long eventId, ReservationStatus status);

    /** Kullanicinin bu etkinlikteki aktif (onayli ya da bekleyen) kaydi; en fazla bir tane olabilir (V3 UNIQUE). */
    @Query("select r from Reservation r where r.event.id = :eventId and r.user.id = :userId and r.status in :statuses")
    Optional<Reservation> findActive(@Param("eventId") Long eventId, @Param("userId") Long userId,
            @Param("statuses") Collection<ReservationStatus> statuses);

    /** Etkinlikte bugune kadar verilmis en buyuk sira numarasi (iptal/terfi edilenler dahil; numara tekrar kullanilmaz). */
    @Query("select coalesce(max(r.waitlistNumber), 0) from Reservation r where r.event.id = :eventId")
    int findMaxWaitlistNumber(@Param("eventId") Long eventId);

    /** Siradaki bekleyen: en kucuk sira numarasi. Etkinlik kilitliyken cagrilir; SKIP LOCKED kullanilmaz. */
    Optional<Reservation> findFirstByEventIdAndStatusOrderByWaitlistNumberAsc(Long eventId, ReservationStatus status);

    /** Bu sira numarasindan once bekleyen kisi sayisi; konum = bu sayi + 1. */
    @Query("select count(r) from Reservation r where r.event.id = :eventId and r.status = :status and r.waitlistNumber < :number")
    long countAhead(@Param("eventId") Long eventId, @Param("status") ReservationStatus status, @Param("number") int number);

    /**
     * Kullanicinin kayitlari, etkinlik bilgisiyle birlikte (join fetch: satir basina ek sorgu yok).
     * status null ise tum durumlar. Siralama Pageable'dan gelir.
     */
    @Query(value = "select r from Reservation r join fetch r.event "
            + "where r.user.id = :userId and (:status is null or r.status = :status)",
            countQuery = "select count(r) from Reservation r "
            + "where r.user.id = :userId and (:status is null or r.status = :status)")
    Page<Reservation> findForUser(@Param("userId") Long userId, @Param("status") ReservationStatus status,
            Pageable pageable);

    /** Iptalde hangi etkinligin kilitlenecegini bulmak icin; entity yuklemez (event_id hic degismez). */
    @Query("select r.event.id from Reservation r where r.id = :id")
    Optional<Long> findEventIdById(@Param("id") Long id);
}
