package com.umutberk.eventreservation.event;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data, metot adindan SQL sorgusunu kendisi uretir. */
public interface EventRepository extends JpaRepository<Event, Long> {

    /** Etkinlikleri baslangic zamanina gore (esitse id'ye gore) sirali getirir. */
    List<Event> findAllByOrderByStartsAtAscIdAsc();
}
