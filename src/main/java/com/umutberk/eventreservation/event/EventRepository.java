package com.umutberk.eventreservation.event;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

/** Spring Data, metot adindan SQL sorgusunu kendisi uretir. */
public interface EventRepository extends JpaRepository<Event, Long> {

    /** Etkinlikleri baslangic zamanina gore (esitse id'ye gore) sirali getirir. */
    List<Event> findAllByOrderByStartsAtAscIdAsc();

    /**
     * Etkinligi okur ve satirini transaction bitene kadar KILITLER (MySQL: SELECT ... FOR UPDATE).
     * Ayni etkinlik icin ayni anda gelen rezervasyon istekleri bu noktada sirayla bekler.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Event e where e.id = :id")
    Optional<Event> findByIdForUpdate(@Param("id") Long id);
}
